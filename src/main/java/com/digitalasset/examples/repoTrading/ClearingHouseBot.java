// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.examples.repoTrading;

import com.digitalasset.examples.repoTrading.model.DomainObject;
import com.digitalasset.examples.repoTrading.model.DvP;
import com.digitalasset.examples.repoTrading.model.NetObligation;
import com.digitalasset.examples.repoTrading.model.RecordMapper;
import com.digitalasset.examples.repoTrading.model.Security;
import com.digitalasset.examples.repoTrading.model.Trade;
import com.digitalasset.examples.repoTrading.util.ControlServer;

import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import com.daml.ledger.javaapi.data.Command;
import com.daml.ledger.javaapi.data.ContractId;
import com.daml.ledger.javaapi.data.DamlList;
import com.daml.ledger.javaapi.data.Filter;
import com.daml.ledger.javaapi.data.FiltersByParty;
import com.daml.ledger.javaapi.data.Identifier;
import com.daml.ledger.javaapi.data.InclusiveFilter;
import com.daml.ledger.javaapi.data.Party;
import com.daml.ledger.javaapi.data.Record;
import com.daml.ledger.javaapi.data.TransactionFilter;
import com.daml.ledger.javaapi.data.Value;
import com.sun.net.httpserver.HttpExchange;
import main.ccp.InitiateSettlementControl;
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;
import org.pcollections.PSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import main.ccp.InviteClearingHouse;

public class ClearingHouseBot extends RepoMarketBot {

    private static final Logger log = LoggerFactory.getLogger(ClearingHouseBot.class);

    private final Identifier inviteClearingHouseTemplateId;
    private final Identifier initiateSettlementControlTemplateId;

    private String ccpContractId = null;
    private LocalDate settlementDate;

    // State variables
    private Boolean settlementInProgress = false;
    private Boolean nettingInProgress = false;

    private int tradeCount = 0;             // Total trades ingested
    private int tradesNovatedCount = 0;     // Total trades novated (i.e settling today)
    private int dvpCount = 0;               // The number of dvp's that will be created

    private ConcurrentHashMap<LocalDate,Set<Map.Entry<String,DomainObject>>> tradesPerDate = new ConcurrentHashMap<>();    // Count of trades per settlementDate

    public ConcurrentHashMap<LocalDate,Long> getTradeCountsPerDate() {
        ConcurrentHashMap<LocalDate,Long> result = new ConcurrentHashMap<>();
        tradesPerDate.forEach((d,s) -> result.put(d,(long)s.size()));
        return result;
    }

    public ClearingHouseBot(RepoTradingMain mainClass, String party) {
        super(mainClass, party);
        this.inviteClearingHouseTemplateId = InviteClearingHouse.TEMPLATE_ID;
        this.initiateSettlementControlTemplateId = InitiateSettlementControl.TEMPLATE_ID;
    }

    @Override
    public int run(String[] args) throws java.io.IOException {
        if (!RepoTradingMain.parseArguments(this, args)) {
            return 1;
        }

        new ControlServer(getConfiguration().getCcp().getPort())
            .addHandler("/settle",this::handleSettlement)
            .addHandler("/tradeState",this::handleTradeState)
            .start();

        return super.run(args);
    }

    private ControlServer.ControlResult handleSettlement(HttpExchange exchange) {
        String queryString = exchange.getRequestURI().getQuery();
        Map<String,String> params = ControlServer.parseQuery(queryString);

        if(params.get("date") == null) {
            log.debug("No date: query='{}'", queryString == null ? "(null)" : queryString);
            return new ControlServer.ControlResult(400,"No date provided\n");
        }

        String dateStr = params.get("date");
        try {
            LocalDate date = LocalDate.parse(dateStr);

            log.debug("handleSettlement date={}", date);

            startSettlement(date).orElse(Stream.empty())
                .map(cps -> cps.getSubmitCommandsRequest())
                .forEach(scr -> submitCommands(scr.getWorkflowId(), scr.getCommands()));

            return new ControlServer.ControlResult(200,"Settled\n");
        } catch(DateTimeParseException ex) {
            log.debug("Bad date format: date={}",dateStr);
            return new ControlServer.ControlResult(400,"Bad date format: date="+dateStr+"\n");
        }
    }

    private ControlServer.ControlResult handleTradeState(HttpExchange exchange) {
        StringBuilder tradeState = new StringBuilder();

        for(Map.Entry<LocalDate,Set<Map.Entry<String,DomainObject>>> entry : tradesPerDate.entrySet()) {
            tradeState.append(entry.getKey().toString()); tradeState.append(" "); tradeState.append(entry.getValue().size());
            tradeState.append("\n");
        }
        return new ControlServer.ControlResult(200,tradeState.toString());
    }

    @Override
    public Stream<CommandsAndPendingSet> process(LedgerViewFlowable.LedgerView<DomainObject> ledgerView) {

        logStatus(ledgerView);
        updateTradeState(ledgerView);

        return Stream.of(
            ledgerView.getContracts(ccpTemplateId).entrySet().stream().map(this::saveCcpContractId),
            ledgerView.getContracts(inviteClearingHouseTemplateId).entrySet().stream().map(this::acceptCcpInvite),
            startSettlementFromSentinel(ledgerView),
            createNettingGroups(ledgerView),
            ledgerView.getContracts(nettingGroupTemplateId).entrySet().stream().map(this::netTrades),
            ledgerView.getContracts(netObligationTemplateId).entrySet().stream().map(this::createDvP),
            ledgerView.getContracts(allocatedDvpTemplateId).entrySet().stream().map(this::settleDvp),
            allocateSecurities(ledgerView),
            finishSettlement(ledgerView)
        ).flatMap(s -> s);
    }

    /**
     * Provide status messagaes to stdout
     *
     * @param ledgerView - the stable ledger view
     */
    private void logStatus(LedgerViewFlowable.LedgerView<DomainObject> ledgerView) {

        if (!settlementInProgress) {
            // Count trades until settlement starts
            int thisTradeCount = ledgerView.getContracts(tradeTemplateId).size();
            if ((tradeCount < thisTradeCount && thisTradeCount > 0 && (thisTradeCount % 10) == 0)) {
                logMessage(String.format("%d trades received...", thisTradeCount));
            }
            tradeCount = thisTradeCount;

        }
    }

    private void updateTradeState(LedgerViewFlowable.LedgerView<DomainObject> ledgerView) {
        tradesPerDate = new ConcurrentHashMap<>();
        ledgerView.getContracts(tradeTemplateId).entrySet().forEach(
            e -> tradesPerDate.computeIfAbsent(
                ((Trade) (e.getValue())).getTradeInfo().getSettlementDate(),
                k -> new HashSet<>()
            ).add(e));
    }

    /**
     * An invite to the CCP has been received - accept it
     *
     * @param entry - an entry containing the contractId and the contract (A {@link com.digitalasset.examples.repoTrading.model.RecordMapper})
     * @return - a command set accepting the invite
     */
    private CommandsAndPendingSet acceptCcpInvite(Map.Entry<String, DomainObject> entry) {

        log.debug("{} accept invitation, contractId={}", getParty(), entry.getKey());

        String contractId = entry.getKey();

        return newCommandAndPendingSet(
            ONBOARDING_WORKFLOW_ID,
            Collections.singletonList(
                newExercise(
                    inviteClearingHouseTemplateId, contractId,
                    "AcceptClearingHouseInvite",
                    new Record.Field("paymentProcessor", new Party(getPaymentProcessorName())))),
            HashTreePMap.singleton(inviteClearingHouseTemplateId, HashTreePSet.singleton(contractId)));
    }

    /**
     * The CCP role contract has been created - save it for future use
     *
     * @param entry - an entry containing the contractId and the contract (A {@link com.digitalasset.examples.repoTrading.model.RecordMapper})
     * @return an empty command set
     */
    private CommandsAndPendingSet saveCcpContractId(Map.Entry<String, DomainObject> entry) {

        if (ccpContractId == null) {
            log.debug("CCP created, contractId={}", entry.getKey());
            ccpContractId = entry.getKey();
        }

        return CommandsAndPendingSet.empty;
    }

    /**
     * A trade has been created - novate it
     *
     * @param entry - an entry containing the contractId and the contract (A {@link Trade})
     * @return a command set novating the Trade
     */
    private CommandsAndPendingSet novateTrade(Map.Entry<String, DomainObject> entry) {

        assert (entry.getValue().getClass() == Trade.class);

        Trade thisTrade = (Trade) entry.getValue();
        String contractId = entry.getKey();

        log.debug("novate trade, tradeId={}, contractId={}", thisTrade.getTradeInfo().getTradeId(), contractId);

        tradesNovatedCount++;

        return newCommandAndPendingSet(
            TRADE_INJECTION_WORKFLOW_ID,
            Collections.singletonList(
                newExercise(
                    tradeTemplateId, contractId,
                    "Novate")),
            HashTreePMap.singleton(tradeTemplateId, HashTreePSet.singleton(contractId)));
    }

    /**
     * Initiate netting by sentinel. Clear out any previous asset state, and start settlement by novating all trades for
     * the given settlement date
     *
     * @param ledgerView - the ledger state
     * @return a CommandsAndPendingSet
     */
    private Stream<CommandsAndPendingSet> startSettlementFromSentinel(LedgerViewFlowable.LedgerView<DomainObject> ledgerView) {

        Stream<CommandsAndPendingSet> commandStream = Stream.empty();

        if (
            ledgerView.getContracts(initiateSettlementControlTemplateId).size() > 0 &&
                !settlementInProgress
            ) {

            RecordMapper sentinel =
                (RecordMapper) new ArrayList<>(
                    ledgerView.getContracts(initiateSettlementControlTemplateId).entrySet()
                ).get(0).getValue();

            Optional<Stream<CommandsAndPendingSet>> optionalCommandStream = startSettlement(sentinel.getDateField(1));

            if(!optionalCommandStream.isPresent()) {
                logMessage(String.format("No trades to settle on %s",settlementDate.toString()));
            }

            commandStream = optionalCommandStream.orElse(
                ledgerView.getContracts(initiateSettlementControlTemplateId).entrySet().stream()
                .map(e -> newCommandAndPendingSet(
                    SETTLEMENT_WORKFLOW_ID,
                    Collections.singletonList(
                        newExercise(initiateSettlementControlTemplateId, e.getKey(), "ArchiveInitiateSettlementControl")
                    ),
                    HashTreePMap.singleton(initiateSettlementControlTemplateId, HashTreePSet.singleton(e.getKey()))
                )));
        }

        return commandStream;
    }

    private Optional<Stream<CommandsAndPendingSet>> startSettlement(LocalDate sdate) {

        Optional<Stream<CommandsAndPendingSet>> maybeCommandStream = Optional.empty();


        Set<Map.Entry<String, DomainObject>> trades = tradesPerDate.getOrDefault(sdate, new HashSet<>());

        if(!trades.isEmpty()) {

            settlementDate = sdate; settlementInProgress = true;

            logMessage(String.format("initiating settlement for %d trades, settlementDate=%s", trades.size(), settlementDate.toString()));

            log.debug("settlement initiated: settlementDate={},tradeCount={}", settlementDate, trades.size());

            // Initiate by novating all eligible trades
            maybeCommandStream = Optional.of(trades.stream().map(this::novateTrade));

        }

        return maybeCommandStream;
    }

    /**
     * Perform netting by waiting for all novated trades and creating netting groups.
     * <p>
     * Group all novated trades by their netting group key, then pass all these lists (a list of lists) to the
     * 'FormNettingGroups' choice. This creates all netting groups.
     * <p>
     * The process is triggered when all trades have been novated - that is, the count of NovatedTrades is double
     * the teh count of trades novated
     *
     * @param ledgerView - the current view of the ledger
     * @return a Stream of CommandsAndPendingSets that execute teh 'FromNettingGroups' choice
     */
    private Stream<CommandsAndPendingSet> createNettingGroups(LedgerViewFlowable.LedgerView<DomainObject> ledgerView) {

        int novatedTradeCount = ledgerView.getContracts(novatedTradeTemplateId).size();
        log.trace(
            "Create netting groups: novatedTradeCount={}, tradeNovatedCount={}",
            novatedTradeCount,
            tradesNovatedCount
        );

        CommandsAndPendingSet commands = CommandsAndPendingSet.empty;

        if (!nettingInProgress && novatedTradeCount > 0 && novatedTradeCount == (2 * tradesNovatedCount)) {

            log.debug("netting started for {}:  netting trades, count = {}",
                settlementDate,
                novatedTradeCount
            );

            nettingInProgress = true;   // Only start netting once
            logMessage("netting trades...");

            /*
             * Filter  the novated trades by settlement date, then group by their domain key - this forms them into
             * lists that can be netted i.e trades of the same settlement date, participant, cusip and currency
             */
            Map<String, List<Map.Entry<String, DomainObject>>> groups = ledgerView
                .getContracts(novatedTradeTemplateId).entrySet().stream()
                .collect(Collectors.groupingBy(entry -> entry.getValue().getDomainKey()));

            /*
             * Turn the groups into the right form of an argument to the FormNettingGroups choice. This transforms
             * a Map of ContractId,DomainObjects (NovatedTrades) tuples into a DamlList of ContractIds (Values),
             * keyed by the domain key
             */
            List<Value> tradeGroups = groups.entrySet().stream()
                .map(entry -> entry.getValue().stream().<Value>map(e -> new ContractId(e.getKey())).collect(Collectors.toList()))
                .map(DamlList::new)
                .collect(Collectors.toList());

            dvpCount = tradeGroups.size();

            /*
             * ... and build the exercise command and pending set.
             */
            commands = newCommandAndPendingSet(
                SETTLEMENT_WORKFLOW_ID,
                Collections.singletonList(newExercise(
                    ccpTemplateId, ccpContractId, "FormNettingGroups", new Record.Field("groupsList", new DamlList(tradeGroups))
                )),
                HashTreePMap.empty());
        }
        return Stream.of(commands);
    }

    /**
     * Net out trades. A NettingGroup has been created for all nettable trades - net them out and form a NetObligationRequest
     * for confirmation by the particiant
     *
     * @param entry - a NettingGroup contractId and value
     * @return - a command set to net the trades
     */
    private CommandsAndPendingSet netTrades(Map.Entry<String, DomainObject> entry) {

        assert (entry.getValue().getClass() == RecordMapper.class);
        RecordMapper nettingGroup = (RecordMapper) entry.getValue();

        String contractId = entry.getKey();

        log.debug("net trades, tradeIds={}, contractId={}", nettingGroup.getListField(0), contractId);

        return newCommandAndPendingSet(
            NETTING_WORKFLOW_ID,
            Collections.singletonList(
                newExercise(
                    nettingGroupTemplateId, contractId,
                    "NetTrades")),
            HashTreePMap.singleton(nettingGroupTemplateId, HashTreePSet.singleton(contractId)));
    }

    /**
     * Create a DvP from a final NetObligation.
     *
     * @param entry - NetObligation and contractId
     * @return - CommandsAndPendingSet
     */
    private CommandsAndPendingSet createDvP(Map.Entry<String, DomainObject> entry) {

        NetObligation netObligation = (NetObligation) entry.getValue();

        String contractId = entry.getKey();

        log.debug("create DvP: netObligationId={}, cusip={}, receiver={}, payer={}, paymentAmount={}, quantity={}",
            contractId,
            netObligation.getCusip(),
            netObligation.getReceiver(),
            netObligation.getPayer(),
            netObligation.getPaymentAmount(),
            netObligation.getQuantity()
        );

        String choice = netObligation.getPayer().equals(netObligation.getCcp()) ?
            "CreateBuyDvP" : "CreateSellDvP";

        return newCommandAndPendingSet(
            SETTLEMENT_WORKFLOW_ID,
            Collections.singletonList(
                newExercise(
                    netObligationTemplateId, contractId,
                    choice)),
            HashTreePMap.singleton(netObligationTemplateId, HashTreePSet.singleton(contractId)));
    }

    /**
     * A fully-allocated Dvp has been created - settle it.
     *
     * @param entry - a DvP entry
     * @return = CommandsAndPendingSet
     */
    private CommandsAndPendingSet settleDvp(Map.Entry<String, DomainObject> entry) {

        DvP dvp = (DvP) entry.getValue();
        String contractId = entry.getKey();

        log.debug("settle dvp, dvpId={}, payer={}, receiver={}", contractId, dvp.getPayer(), dvp.getReceiver());

        return newCommandAndPendingSet(
            SETTLEMENT_WORKFLOW_ID,
            Collections.singletonList(newExercise(allocatedDvpTemplateId, contractId, "Settle")),
            HashTreePMap.singleton(allocatedDvpTemplateId, HashTreePSet.singleton(contractId))
        );
    }

    private class AllocationResult {

        final List<Command> commands;
        final PSet<String> dvpIds;
        final PSet<String> securityIds;

        AllocationResult(List<Command> commands, PSet<String> dvpIds, PSet<String> securityIds) {
            this.commands = commands;
            this.dvpIds = dvpIds;
            this.securityIds = securityIds;
        }

        AllocationResult() {
            this(new ArrayList<>(), HashTreePSet.empty(), HashTreePSet.empty());
        }

        boolean isAllocated() {
            assert( (commands.isEmpty() && dvpIds.isEmpty() && securityIds.isEmpty()) ||
                (!commands.isEmpty() && !dvpIds.isEmpty() && !securityIds.isEmpty()));

            return !commands.isEmpty();
        }

        CommandsAndPendingSet asCommands() {
            return newCommandAndPendingSet(
                SETTLEMENT_WORKFLOW_ID,
                commands,
                HashTreePMap
                    .singleton(cashAllocatedDvpTemplateId,dvpIds)
                    .plus( securityTemplateId,securityIds )
            );
        }
    }
    /**
     * A Cash-allocated DvP has been created - allocate any securities available.
     *
     * @param entry - a CashAllocatedDvp entry
     * @param availableSecurities - securities available for settlement indexed by contractId
     * @return an AllocationResult
     */
    private AllocationResult allocateSecurity(Map.Entry<String, DomainObject> entry, Map<String,Security> availableSecurities) {

        AllocationResult result = new AllocationResult();
        String dvpId = entry.getKey();
        DvP dvp = (DvP) entry.getValue();

        // If we have enough securities to settle....

        BigDecimal sum = BigDecimal.ZERO;
        List<String> allocated = new ArrayList<>();

        for(Iterator<Map.Entry<String,Security>> iter = availableSecurities.entrySet().iterator(); iter.hasNext() && sum.compareTo(dvp.getQuantity()) < 0;) {
            Map.Entry<String,Security> e = iter.next();
            String securityId = e.getKey();
            Security s = e.getValue();
            if(s.getCusip().equals(dvp.getCusip())) {
                sum = sum.add(s.getCollateralQuantity());
                allocated.add(securityId);
            }
        }

        log.trace("allocate securities: cusip={}, collateralQuantity={}, security sum={}",
            dvp.getCusip(), dvp.getQuantity(), sum);

        if(sum.compareTo(dvp.getQuantity()) >= 0) {
            // We have enough securites to allocate

            log.debug("allocate securities: dvpId={} cusip={}, quantity={}, security sum={}, count={}",
                dvpId, dvp.getCusip(), dvp.getQuantity(), sum, allocated.size()
            );

            DamlList arg = new DamlList(
                allocated.stream().<Value>map(ContractId::new).collect(Collectors.toList())
            );

            result = new AllocationResult(
                Collections.singletonList(
                    newExercise(cashAllocatedDvpTemplateId, dvpId,
                        "AllocateSecurity", new Record.Field("securities", arg))
                ),
                HashTreePSet.singleton(dvpId),
                HashTreePSet.from(allocated)
            );
        }
        return result;
    }

    /**
     * Allocate some (buy side) DvP. Try allocation on each CashAllocatedDvp until one succeeds
     *
     * @param ledgerView - the ledger view containing the DvPs
     * @return A command stream
     */
    private Stream<CommandsAndPendingSet> allocateSecurities(LedgerViewFlowable.LedgerView<DomainObject> ledgerView) {

        Map<String,Security> availableSecurities = new HashMap<>();
        ledgerView.getContracts(securityTemplateId).entrySet().stream()
            .filter(e -> ((Security)e.getValue()).getOwner().equals(getParty()))
            .forEach(e ->  availableSecurities.put(e.getKey(),(Security) e.getValue()));

        AllocationResult result = new AllocationResult();
        for(
            Iterator<Map.Entry<String,DomainObject>>iter = ledgerView.getContracts(cashAllocatedDvpTemplateId)
                .entrySet().iterator();
            iter.hasNext() && !result.isAllocated(); ) {
            result = allocateSecurity(iter.next(), availableSecurities);
        }

        return result.isAllocated() ? Stream.of(result.asCommands()) : Stream.empty();
    }

    /**
     * Complete the settlement cycle. Monitor the settled DvP count, and when it matches the expected count, reset the
     * settlement state ready for a new cycle
     *
     * @param ledgerView - the current ledger view
     * @return a command Stream
     */
    private Stream<CommandsAndPendingSet> finishSettlement(LedgerViewFlowable.LedgerView<DomainObject> ledgerView) {

        Stream<CommandsAndPendingSet> commandStream = Stream.empty();
        long settledDvpCount = ledgerView.getContracts(settledDvpTemplateId).entrySet().stream()
            .filter(e -> ((DvP)e.getValue()).getSettlementDate().equals(settlementDate))
            .count();

        log.trace("finish settlement: nettingInProgress={}, dvpCount={}, settledDvps.count()={}",
            nettingInProgress, dvpCount, settledDvpCount);

        if (nettingInProgress && settledDvpCount == dvpCount ) {

            // Print a final message and clear the dvp record, then rmeove any sentinel contract

            commandStream = ledgerView.getContracts(initiateSettlementControlTemplateId).entrySet().stream()
                .map(e -> newCommandAndPendingSet(
                    SETTLEMENT_WORKFLOW_ID,
                    Collections.singletonList(
                        newExercise(initiateSettlementControlTemplateId, e.getKey(), "ArchiveInitiateSettlementControl")
                    ),
                    HashTreePMap.singleton(initiateSettlementControlTemplateId, HashTreePSet.singleton(e.getKey()))
                ));

            logMessage(String.format("Settlement complete, %d trades and %d DvP's processed, %d trades remaining.",
                tradesNovatedCount, settledDvpCount, ledgerView.getContracts(tradeTemplateId).size()
            ));

            // Reset settlement state so we can restart
            settlementDate = null;
            settlementInProgress = false;
            nettingInProgress = false;
            tradesNovatedCount = 0;
            dvpCount = 0;

        }
        return commandStream;
    }

    @Override
    public TransactionFilter getTransactionFilter() {
        InclusiveFilter inclusiveFilter = new InclusiveFilter(new HashSet<>(Arrays.asList(
            inviteClearingHouseTemplateId,
            ccpTemplateId,
            initiateSettlementControlTemplateId,
            tradeTemplateId,
            novatedTradeTemplateId,
            nettingGroupTemplateId,
            netObligationRequestTemplateId,
            netObligationTemplateId,
            dvpTemplateId,
            cashAllocatedDvpTemplateId,
            allocatedDvpTemplateId,
            settledDvpTemplateId,
            cashRequestTemplateId,
            cashTemplateId,
            securityTemplateId
        )));
        Map<String, Filter> filter = Collections.singletonMap(getParty(), inclusiveFilter);
        return new FiltersByParty(filter);
    }
}
