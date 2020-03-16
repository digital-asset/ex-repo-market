/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.examples.repoTrading;

import com.daml.ledger.javaapi.data.*;
import com.daml.ledger.rxjava.DamlLedgerClient;
import com.daml.ledger.rxjava.components.Bot;
import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import com.daml.ledger.rxjava.components.helpers.CreatedContract;
import com.daml.ledger.rxjava.components.helpers.TemplateUtils;
import com.digitalasset.examples.repoTrading.util.Configuration;
import io.reactivex.Flowable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import main.cash.Cash;
import main.cash.LockedCash;
import main.cashrequest.CashRequest;
import main.ccp.CCP;
import main.ccp.CCPInvite;
import main.ccp.InitiateSettlementControl;
import main.ccp.InviteClearingHouse;
import main.dvp.AllocatedDvP;
import main.dvp.CashAllocatedDvP;
import main.dvp.DvP;
import main.dvp.SettledDvP;
import main.genesis.Genesis;
import main.netobligation.NetObligation;
import main.netobligation.NetObligationRequest;
import main.netting.NettingGroup;
import main.security.MergedSecurity;
import main.security.Security;
import main.trade.NovatedTrade;
import main.trade.Trade;
import main.trade.TradeRegistrationRequest;
import main.tradingparticipant.InviteTradingParticipant;
import main.tradingparticipant.TradingParticipant;
import org.pcollections.PMap;
import org.pcollections.PSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class RepoMarketBot {

  private static final Logger log = LoggerFactory.getLogger(RepoMarketBot.class);

  // Constants
  static final String ONBOARDING_WORKFLOW_ID = "Onboarding";
  static final String TRADE_INJECTION_WORKFLOW_ID = "TradeInjection";
  static final String NETTING_WORKFLOW_ID = "Netting";
  static final String SETTLEMENT_WORKFLOW_ID = "Settlement";

  // Template identities
  final Identifier ccpTemplateId;
  final Identifier tradeTemplateId;
  final Identifier cashRequestTemplateId;
  final Identifier novatedTradeTemplateId;
  final Identifier nettingGroupTemplateId;
  final Identifier netObligationRequestTemplateId;
  final Identifier netObligationTemplateId;
  final Identifier dvpTemplateId;
  final Identifier cashAllocatedDvpTemplateId;
  final Identifier allocatedDvpTemplateId;
  final Identifier settledDvpTemplateId;
  final Identifier cashTemplateId;
  final Identifier securityTemplateId;

  // Instance vars
  private final RepoTradingMain mainClass;
  private String party;

  RepoMarketBot(RepoTradingMain mainClass, String party) {
    this.mainClass = mainClass;
    this.party = party;

    this.ccpTemplateId = CCP.TEMPLATE_ID;
    this.tradeTemplateId = Trade.TEMPLATE_ID;
    this.cashRequestTemplateId = CashRequest.TEMPLATE_ID;
    this.novatedTradeTemplateId = NovatedTrade.TEMPLATE_ID;
    this.nettingGroupTemplateId = NettingGroup.TEMPLATE_ID;
    this.netObligationRequestTemplateId = NetObligationRequest.TEMPLATE_ID;
    this.netObligationTemplateId = NetObligation.TEMPLATE_ID;
    this.dvpTemplateId = DvP.TEMPLATE_ID;
    this.cashAllocatedDvpTemplateId = CashAllocatedDvP.TEMPLATE_ID;
    this.allocatedDvpTemplateId = AllocatedDvP.TEMPLATE_ID;
    this.settledDvpTemplateId = SettledDvP.TEMPLATE_ID;
    this.cashTemplateId = Cash.TEMPLATE_ID;
    this.securityTemplateId = Security.TEMPLATE_ID;
  }

  public int run(String[] args) throws java.io.IOException {
    Bot.wire(
        RepoTradingMain.APP_ID,
        mainClass.getClient(),
        getTransactionFilter(),
        this::runProcess,
        RepoMarketBot::asDomainObject);
    logMessage("bot started");
    return 0;
  }

  private static Template asDomainObject(CreatedContract created) {
    log.trace("{} maps to {} ", created.getTemplateId());
    return TemplateUtils.contractTransformer(
            Cash.class,
            LockedCash.class,
            Security.class,
            MergedSecurity.class,
            NovatedTrade.class,
            DvP.class,
            CashAllocatedDvP.class,
            AllocatedDvP.class,
            SettledDvP.class,
            NetObligationRequest.class,
            NetObligation.class,
            TradeRegistrationRequest.class,
            Trade.class,
            Genesis.class,
            InviteClearingHouse.class,
            InitiateSettlementControl.class,
            CCPInvite.class,
            CCP.class,
            InviteTradingParticipant.class,
            TradingParticipant.class,
            NettingGroup.class,
            CashRequest.class)
        .apply(created);
  }

  private Flowable<CommandsAndPendingSet> runProcess(
      LedgerViewFlowable.LedgerView<Template> ledgerView) {
    Stream<CommandsAndPendingSet> cmdStream =
        process(ledgerView).filter(cps -> !cps.equals(CommandsAndPendingSet.empty));
    return Flowable.fromIterable(cmdStream::iterator);
  }

  public abstract Stream<CommandsAndPendingSet> process(
      LedgerViewFlowable.LedgerView<Template> ledgerView);

  public abstract TransactionFilter getTransactionFilter();

  /**
   * Helper method to build a CommandAndPendingSet with default values
   *
   * @param workflowId the workflowId to use
   * @param commandList the commands to execute
   * @param pendingSet the contract ids that shoud be marked as pending
   * @return a CommandsAndPendingSet
   */
  CommandsAndPendingSet newCommandAndPendingSet(
      String workflowId, List<Command> commandList, PMap<Identifier, PSet<String>> pendingSet) {
    SubmitCommandsRequest commands =
        new SubmitCommandsRequest(
            workflowId,
            RepoTradingMain.APP_ID,
            UUID.randomUUID().toString(),
            this.party,
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(10),
            commandList);
    return new CommandsAndPendingSet(commands, pendingSet);
  }

  ExerciseCommand newExercise(
      Identifier templateId, String contractId, String choice, Record.Field... args) {
    log.debug("new exercise: {}, {}, {}, {}", templateId, contractId, choice, args);

    return new ExerciseCommand(templateId, contractId, choice, new Record(Arrays.asList(args)));
  }

  void submitCommands(String workflowId, List<Command> commands) {

    String commandId = UUID.randomUUID().toString();
    log.debug("{} submits command id={}, commands={}", getParty(), commandId, commands);

    // asynchronously send the commands
    getClient()
        .getCommandSubmissionClient()
        .submit(
            workflowId,
            RepoTradingMain.APP_ID,
            commandId,
            getParty(),
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(5),
            commands)
        .blockingGet();
  }

  void logMessage(String message) {
    RepoTradingMain.logMessage(getParty(), message);
  }

  void logError(String message) {
    RepoTradingMain.logError(getParty(), message);
  }

  public RepoTradingMain getMainClass() {
    return mainClass;
  }

  String getParty() {
    return party;
  }

  public void setParty(String party) {
    this.party = party;
  }

  String getOperatorName() {
    return mainClass.getOperatorName();
  }

  String getCcpName() {
    return mainClass.getCcpName();
  }

  String getPaymentProcessorName() {
    return mainClass.getPaymentProcessorName();
  }

  Configuration getConfiguration() {
    return mainClass.getConfiguration();
  }

  private DamlLedgerClient getClient() {
    return mainClass.getClient();
  }

  private static String identifierToString(Identifier identifier) {
    return identifier.getModuleName().concat(":").concat(identifier.getEntityName());
  }

  protected LocalDate toLocalDate(Instant instant) {
    return instant.atZone(ZoneOffset.UTC).toLocalDate();
  }
}
