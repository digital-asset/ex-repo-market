/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.examples.repoTrading;

import static org.junit.Assert.assertTrue;

import com.daml.ledger.javaapi.data.ContractId;
import com.daml.ledger.javaapi.data.Party;
import com.daml.ledger.rxjava.DamlLedgerClient;
import com.digitalasset.testing.comparator.ledger.ContractArchived;
import com.digitalasset.testing.junit4.Sandbox;
import com.digitalasset.testing.ledger.DefaultLedgerAdapter;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import main.ccp.CCP;
import main.ccp.InitiateSettlementControl;
import main.dvp.SettledDvP;
import main.trade.Trade;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;

public class RepoTradingIT {

  private static final Path RELATIVE_DAR_PATH = Paths.get("./target/ex-repo-market.dar");
  private static final String TEST_MODULE = "RepoMarket";
  private static final String TEST_SCENARIO = "empty";

  private static Party CITI_PARTY = new Party("Citi");
  private static Party BARCLAYS_PARTY = new Party("Barclays");
  private static Party JPMORGAN_PARTY = new Party("JPMorgan");
  private static Party HSBC_PARTY = new Party("HSBC");
  private static Party CCP_PARTY = new Party("CCP");
  private static Party OPERATOR_PARTY = new Party("Operator");
  private static Party PAYMENTPROCESSOR_PARTY = new Party("PaymentProcessor");

  private static Sandbox sandbox =
      Sandbox.builder()
          .dar(RELATIVE_DAR_PATH)
          .module(TEST_MODULE)
          .scenario(TEST_SCENARIO)
          .parties(
              CITI_PARTY.getValue(),
              BARCLAYS_PARTY.getValue(),
              JPMORGAN_PARTY.getValue(),
              HSBC_PARTY.getValue(),
              CCP_PARTY.getValue(),
              OPERATOR_PARTY.getValue(),
              PAYMENTPROCESSOR_PARTY.getValue())
          .setupAppCallback(RepoTradingIT::runBots)
          .build();

  public static void runBots(DamlLedgerClient ledgerClient) {
    try {
      RepoTradingMain repoTradingMain = new RepoTradingMain();
      repoTradingMain.startBots(ledgerClient, new String[] {"data/Trades12-2018-06-28.csv"});
    } catch (Exception e) {
      System.out.println("Failed to start bots");
      e.printStackTrace();
    }
  }

  @ClassRule public static ExternalResource compile = sandbox.getClassRule();

  @Rule public ExternalResource sandboxRule = sandbox.getRule();

  private DefaultLedgerAdapter ledger;

  @Before
  public void setup() {
    ledger = sandbox.getLedgerAdapter();
  }

  @Test
  public void testWorkflow() throws InvalidProtocolBufferException {
    // wait for OperatorBot and TradingParticipantBot initial processes and the injected trades
    for (int i = 0; i < 12; i++) {
      ledger.getCreatedContractId(CCP_PARTY, Trade.TEMPLATE_ID, Trade.ContractId::new);
    }

    // initiate settlement
    CCP.ContractId ccpCid =
        ledger.getCreatedContractId(CCP_PARTY, CCP.TEMPLATE_ID, CCP.ContractId::new);
    ledger.exerciseChoice(
        CCP_PARTY, ccpCid.exerciseInitiateSettlement(Instant.parse("2018-06-28T00:00:00Z")));

    InitiateSettlementControl.ContractId isControlCid =
        ledger.getCreatedContractId(
            CCP_PARTY,
            InitiateSettlementControl.TEMPLATE_ID,
            InitiateSettlementControl.ContractId::new);

    // waiting for the settlement to be completed. It happens when the control contract is archived
    ledger.observeEvent(
        CCP_PARTY.getValue(),
        ContractArchived.apply(
            "Main.CCP:InitiateSettlementControl", new ContractId(isControlCid.contractId)));

    assertTrue(
        ledger.observeMatchingContracts(
            CCP_PARTY,
            SettledDvP.TEMPLATE_ID,
            SettledDvP::fromValue,
            true,
            dvp -> dvp.paymentAmount.toBigInteger().longValue() == 8550000L,
            dvp -> dvp.paymentAmount.toBigInteger().longValue() == 5700000L,
            dvp -> dvp.paymentAmount.toBigInteger().longValue() == 4512500L,
            dvp -> dvp.paymentAmount.toBigInteger().longValue() == 9737500L));
  }
}
