// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.examples.repoTrading;

import com.daml.ledger.javaapi.data.*;
import com.digitalasset.examples.repoTrading.util.Configuration;

import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import main.genesis.Genesis;
import org.pcollections.HashTreePMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class OperatorBot extends RepoMarketBot {

    private static final Logger log = LoggerFactory.getLogger(OperatorBot.class);

    private final Identifier genesisTemplateId;

    private String genesisContractId = null;
    private String ccpContractId = null;
    private List<String> tradingParties = new ArrayList<>();

    OperatorBot(RepoTradingMain mainClass, String party) {
        super(mainClass, party);
        genesisTemplateId = Genesis.TEMPLATE_ID;
    }

    @Override
    public int run(String[] args) throws java.io.IOException {

        if (!RepoTradingMain.parseArguments(this, args)) {
            return 1;
        }

        super.run(args);
        loadTradingParties();
        createGenesisContract();
        return 0;
    }

    private void loadTradingParties() {
        tradingParties = getConfiguration().getTradingParties().values().stream()
            .map(Configuration.BotConfiguration::getName)
            .collect(Collectors.toList());
    }

    /**
     * Creates genesis contract.
     */
    private void createGenesisContract() {
        // command that creates the initial Ping contract with the required parameters according to the model
        CreateCommand createCommand = new CreateCommand(genesisTemplateId,
            new Record(
                genesisTemplateId,
                new Record.Field("operator", new Party(getOperatorName()))));

        submitCommands(ONBOARDING_WORKFLOW_ID, Collections.singletonList(createCommand));
    }

    @Override
    public Stream<CommandsAndPendingSet> process(LedgerViewFlowable.LedgerView<Template> ledgerView) {

        log.trace("Operator process, state: {}",ledgerView);

        return Stream.concat(
            ledgerView.getContracts(genesisTemplateId).entrySet().stream().map(this::inviteCcpAndPaymentProcessor),
            ledgerView.getContracts(ccpTemplateId).entrySet().stream().map(this::inviteTradingParties)
        );
    }

    private CommandsAndPendingSet inviteCcpAndPaymentProcessor(Map.Entry<String, Template> entry) {

        if(genesisContractId == null) {
            /*
             *   Only invite if the Genesis contract has been created. The exercises are all anytime choices, so it
             *   will persiste across excercises
             */
            log.debug("Inviting CCP: {}", getCcpName());

            genesisContractId = entry.getKey();

            return newCommandAndPendingSet(
                ONBOARDING_WORKFLOW_ID,
                Arrays.asList(
                    newExercise(
                        genesisTemplateId, genesisContractId,
                        "InviteCCP",
                        new Record.Field("ccp", new Party(getCcpName()))),
                    newExercise(
                        genesisTemplateId, genesisContractId,
                        "InvitePaymentProcessor",
                        new Record.Field("paymentProcessor", new Party(getPaymentProcessorName())))),
                HashTreePMap.empty());
        } else {
            return CommandsAndPendingSet.empty;
        }
    }

    private CommandsAndPendingSet inviteTradingParties(Map.Entry<String, Template> entry) {

        if(ccpContractId == null) {
            /*
             *   Only invite if the Genesis contract has been created. The exercises are all anytime choices, so it
             *   will persiste across excercises
             */
            log.debug("Invite trading parties: {}", tradingParties);

            ccpContractId = entry.getKey();

            DamlList args = new DamlList(tradingParties.stream().map(Party::new).collect(Collectors.toList()));
            return newCommandAndPendingSet(
                ONBOARDING_WORKFLOW_ID,
                Collections.singletonList(
                    newExercise(
                        ccpTemplateId, ccpContractId,
                        "InviteTradingParticipants",
                        new Record.Field(
                            "tradingParties",
                            args))),
                HashTreePMap.empty());
        } else {
            return CommandsAndPendingSet.empty;
        }
    }

    @Override
    public TransactionFilter getTransactionFilter() {
        InclusiveFilter inclusiveFilter = new InclusiveFilter(new HashSet<>(Arrays.asList(genesisTemplateId, ccpTemplateId)));
        Map<String, Filter> filter = Collections.singletonMap(getParty(), inclusiveFilter);
        return new FiltersByParty(filter);
    }

}
