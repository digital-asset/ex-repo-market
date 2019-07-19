// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.examples.repoTrading;

import com.daml.ledger.javaapi.data.*;
import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import main.ccp.CCPInvite;
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

public class PaymentProcessorBot extends RepoMarketBot {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorBot.class);

    // Template Id's
    private final Identifier ccpInviteTemplateId;

    public PaymentProcessorBot(RepoTradingMain mainClass, String party) {
        super(mainClass, party);
        this.ccpInviteTemplateId = CCPInvite.TEMPLATE_ID;
    }

    @Override
    public Stream<CommandsAndPendingSet> process(LedgerViewFlowable.LedgerView<Template> ledgerView) {
        return ledgerView.getContracts(ccpInviteTemplateId).entrySet().stream().map(this::confirmCcp);
    }

    private CommandsAndPendingSet confirmCcp(Map.Entry<String, Template> entry) {

        log.debug("{} confirms CCP", getPaymentProcessorName());

        String contractId = entry.getKey();

        return newCommandAndPendingSet(
            ONBOARDING_WORKFLOW_ID,
            Collections.singletonList(newExercise(ccpInviteTemplateId, contractId,"ConfirmCCP")),
            HashTreePMap.singleton(ccpInviteTemplateId, HashTreePSet.singleton(contractId)));
    }

    @Override
    public TransactionFilter getTransactionFilter() {
        InclusiveFilter inclusiveFilter = new InclusiveFilter(new HashSet<>(Arrays.asList(ccpInviteTemplateId)));
        Map<String, Filter> filter = Collections.singletonMap(getParty(), inclusiveFilter);
        return new FiltersByParty(filter);
    }
}
