// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.examples.repoTrading;

import com.digitalasset.examples.repoTrading.model.DomainObject;
import com.digitalasset.examples.repoTrading.model.RecordMapper;
import com.digitalasset.examples.repoTrading.model.TradeRegistrationRequest;
import com.digitalasset.examples.repoTrading.util.ControlServer;
import com.digitalasset.examples.repoTrading.util.CsvFile;

import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import com.daml.ledger.javaapi.data.Command;
import com.daml.ledger.javaapi.data.Decimal;
import com.daml.ledger.javaapi.data.Filter;
import com.daml.ledger.javaapi.data.FiltersByParty;
import com.daml.ledger.javaapi.data.Identifier;
import com.daml.ledger.javaapi.data.InclusiveFilter;
import com.daml.ledger.javaapi.data.Int64;
import com.daml.ledger.javaapi.data.Party;
import com.daml.ledger.javaapi.data.Record;
import com.daml.ledger.javaapi.data.Text;
import com.daml.ledger.javaapi.data.Timestamp;
import com.daml.ledger.javaapi.data.TransactionFilter;
import com.sun.net.httpserver.HttpExchange;
import main.tradingparticipant.InviteTradingParticipant;
import main.tradingparticipant.TradingParticipant;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

public class TradingParticipantBot extends RepoMarketBot {

    private static final Logger log = LoggerFactory.getLogger(TradingParticipantBot.class);

    private class TradeStreamer implements Runnable {

        private final CsvFile reader;
        private final long delay;          // mS between registrations

        TradeStreamer(File tradeFile, int delay) {
            this.reader = new CsvFile(tradeFile);
            this.delay = delay;
        }

        TradeStreamer(File tradeFile){
            this(tradeFile,2000);
        }

        @Override
        public void run() {
            commandStream().forEach(this::submit);
            try {
                reader.close();
            } catch (IOException e) {
                log.warn("Error closing trade reader: {}",e);
            }
        }

        private void submit(Map.Entry<String,Command> commandEntry) {

            log.debug("{} registers trade {}", getParty(), commandEntry.getKey());
            submitCommands(TRADE_INJECTION_WORKFLOW_ID, Collections.singletonList(commandEntry.getValue()));
        }

        Stream<Map.Entry<String,Command>> commandStream() {
            try {
                return reader.open()
                    .recordStream()
                    .filter(r -> r.get("lender").equals(getParty()))
                    .peek(r -> pause(delay))
                    .map(this::toTradeCommand);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private Map.Entry<String,Command> toTradeCommand(Map<String,String> record) {

            logMessage(String.format("requests trade with %s, tradeId '%s'",record.get("borrower"), record.get("tradeId")));

            return new AbstractMap.SimpleEntry<>(
                record.get("tradeId"),
                newExercise(
                    tradingParticipantTemplateId, tradingParticipantId,
                    "RequestTrade",
                    new Record.Field("borrower",              new Party(record.get("borrower"))),
                    new Record.Field("tradeId",             new Int64(Long.parseLong(record.get("tradeId")))),
                    new Record.Field("cusip",               new Text(record.get("cusip"))),
                    new Record.Field("settlementDate",      new Timestamp(toEpochMicros(record.get("settlementDate")))),
                    new Record.Field("tradeDate",           new Timestamp(toEpochMicros(record.get("tradeDate")))),
                    new Record.Field("collateralQuantity",  new Decimal(new BigDecimal(record.get("collateralQuantity")))),
                    new Record.Field("price",               new Decimal(new BigDecimal(record.get("price")))),
                    new Record.Field("repoRate",            new Decimal(new BigDecimal(record.get("repoRate")))),
                    new Record.Field("term",                new Int64(Long.parseLong(record.get("term")))),
                    new Record.Field("startAmount",         new Decimal(new BigDecimal(record.get("startAmount")))),
                    new Record.Field("endAmount",           new Decimal(new BigDecimal(record.get("endAmount")))),
                    new Record.Field("currency",            new Text(record.get("currency")))
                ));
        }

        long toEpochMicros(String dateString) {
            LocalDate d = LocalDate.parse(dateString);
            log.trace("date string {} parses to {}",dateString,d);
            return d.toEpochDay() * RecordMapper.MICRO_SEC_PER_DAY;
        }
    }

    // Constants

    // Template ID's

    private final Identifier inviteTradingParticipantTemplateId;
    private final Identifier tradeRegistrationRequestTemplateId;
    private final Identifier tradingParticipantTemplateId;

    // Argument parsing

    @Option(name = "--injectDelay", aliases = "-d", metaVar = "INJECTION_DELAY", usage = "Delay for INJECTION_DELAY mS between each trade request")
    private int injectDelay = 2000;

    @Argument(index = 0 , metaVar = "PARTY", required = true, usage = "The trading party")
    private String participant = null;

    @Argument(index = 1 , metaVar = "TRADE_FILE", usage = "The trades to inject")
    private File tradeFile = null;

    // Instance vars

    private String tradingParticipantId = null;

    public TradingParticipantBot(RepoTradingMain mainClass, String party) {
        super(mainClass,party);
        this.tradeRegistrationRequestTemplateId = main.trade.TradeRegistrationRequest.TEMPLATE_ID;
        this.inviteTradingParticipantTemplateId = InviteTradingParticipant.TEMPLATE_ID;
        this.tradingParticipantTemplateId = TradingParticipant.TEMPLATE_ID;
    }

    private ControlServer.ControlResult handleInjectTradeFile(HttpExchange exchange) {

        if(tradingParticipantId == null) {
            log.debug("injectTradeFile: trading participnat not set");
            return new ControlServer.ControlResult(400,"trading participnat not set\n");
        }

        String queryString = exchange.getRequestURI().getQuery();
        Map<String,String> params = ControlServer.parseQuery(queryString);
        String fileName = params.get("fileName");

        if(fileName == null) {
            log.debug("injectTradeFile: No file name");
            return new ControlServer.ControlResult(400,"No file name\n");
        }

        tradeFile = new File(fileName);

        if(!tradeFile.exists()) {
            log.debug("injectTradeFile: No file: {}",fileName);
            return new ControlServer.ControlResult(400,"No file named '"+fileName+"'\n");
        }

        // Stream the trades
        Thread tradeStreamer = new Thread(new TradeStreamer(tradeFile, injectDelay),"tradeStreamer");
        tradeStreamer.start();

        return new ControlServer.ControlResult(200,"Injected\n");
    }

    @Override
    public int run(String[] args) throws java.io.IOException {
        if(!RepoTradingMain.parseArguments(this,args)) {
            return 1;
        }
        setParty(participant);

        new ControlServer(getConfiguration().getTradingParties().get(getParty()).getPort())
            .addHandler("/injectTradeFile",this::handleInjectTradeFile)
            .start();

        return super.run(args);
    }

    @Override
    public Stream<CommandsAndPendingSet> process(LedgerViewFlowable.LedgerView<DomainObject> ledgerView) {

        log.trace("TradingParticpant process: {}, state: {}", getParty(), ledgerView);

        return Stream.of(
            ledgerView.getContracts(inviteTradingParticipantTemplateId).entrySet().stream().map(this::acceptTradingInvite),
            ledgerView.getContracts(tradingParticipantTemplateId).entrySet().stream().map(this::injectTrades),
            ledgerView.getContracts(tradeRegistrationRequestTemplateId).entrySet().stream().map(this::acceptTradeRequest),
            ledgerView.getContracts(netObligationRequestTemplateId).entrySet().stream().map(this::acceptNetObligation)
        ).flatMap(s -> s);
    }

    private CommandsAndPendingSet acceptTradingInvite(Map.Entry<String, DomainObject> entry) {

        log.debug("{} accepts trading invitation", getParty());

        logMessage(getParty() + " accepts invitation as trading participant");

        String contractId = entry.getKey();

        return newCommandAndPendingSet(
            ONBOARDING_WORKFLOW_ID,
            Collections.singletonList(
                newExercise(
                    inviteTradingParticipantTemplateId, contractId,
                    "AcceptTradingInvite")),
            HashTreePMap.singleton(inviteTradingParticipantTemplateId, HashTreePSet.singleton(contractId)));
    }

    private CommandsAndPendingSet injectTrades(Map.Entry<String, DomainObject> entry) {

        CommandsAndPendingSet commands = CommandsAndPendingSet.empty;
        if(tradingParticipantId == null) {

            log.debug("TradingParticipant created: {}", getParty());

            tradingParticipantId = entry.getKey();

            if(tradeFile != null) {
                Thread tradeStreamer = new Thread(new TradeStreamer(tradeFile, injectDelay), "tradeStreamer");
                tradeStreamer.start();
            }
        }

        return commands;
    }

    private CommandsAndPendingSet acceptTradeRequest(Map.Entry<String, DomainObject> entry) {

        assert(entry.getValue().getClass() == TradeRegistrationRequest.class);
        String contractId = entry.getKey();
        log.debug("{} receives trade request, cid={}", getParty(), contractId);

        TradeRegistrationRequest request = (TradeRegistrationRequest) entry.getValue();
        CommandsAndPendingSet commands = CommandsAndPendingSet.empty;

        if(tradingParticipantId != null && request.getTradeCounterParty().equals(getParty())) {
            // If this is for me, and we've been accepted as a trading particpant...
            logMessage(String.format("accepting tradeId %s, lender=%s", request.getTradeInfo().getTradeId(), request.getTradeRequester()));

            commands = newCommandAndPendingSet(
                TRADE_INJECTION_WORKFLOW_ID,
                Collections.singletonList(newExercise(tradeRegistrationRequestTemplateId, contractId,"RegisterTrade")),
                HashTreePMap.singleton(tradeRegistrationRequestTemplateId, HashTreePSet.singleton(contractId)));
        }
        return commands;
    }

    private CommandsAndPendingSet acceptNetObligation(Map.Entry<String, DomainObject> entry) {

        assert(entry.getValue().getClass() == RecordMapper.class);
        RecordMapper netObligationRequest = (RecordMapper) entry.getValue();

        String contractId = entry.getKey();

        log.debug("accept net obligation: contractId={}, participantId={}, isBuy={}, cusip={}, paymentAmount={}, quantity={}",
            contractId,
            netObligationRequest.getPartyField(6),
            netObligationRequest.getBooleanField(7),
            netObligationRequest.getTextField(1),
            netObligationRequest.getDecimalField(3),
            netObligationRequest.getDecimalField(4));

        return newCommandAndPendingSet(
            NETTING_WORKFLOW_ID,
            Collections.singletonList(
                newExercise(
                    netObligationRequestTemplateId, contractId,
                    "AcceptNetObligation")),
            HashTreePMap.singleton(netObligationRequestTemplateId, HashTreePSet.singleton(contractId)));
    }

    @Override
    public TransactionFilter getTransactionFilter() {
        InclusiveFilter inclusiveFilter = new InclusiveFilter(
            new HashSet<>(Arrays.asList(
                inviteTradingParticipantTemplateId,
                tradingParticipantTemplateId,
                tradeRegistrationRequestTemplateId,
                cashRequestTemplateId,
                tradeTemplateId,
                netObligationRequestTemplateId,
                netObligationTemplateId)));
        Map<String, Filter> filter = Collections.singletonMap(getParty(), inclusiveFilter);
        return new FiltersByParty(filter);
    }

    private void pause(long delay) {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            // Ignore
        }
    }
}
