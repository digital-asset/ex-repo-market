/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.examples.repoTrading;

import com.daml.ledger.javaapi.data.*;
import com.daml.ledger.rxjava.components.LedgerViewFlowable;
import com.daml.ledger.rxjava.components.helpers.CommandsAndPendingSet;
import com.digitalasset.examples.repoTrading.util.ControlServer;
import com.digitalasset.examples.repoTrading.util.CsvFile;
import com.sun.net.httpserver.HttpExchange;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;
import main.netobligation.NetObligationRequest;
import main.trade.TradeRegistrationRequest;
import main.tradingparticipant.InviteTradingParticipant;
import main.tradingparticipant.TradingParticipant;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TradingParticipantBot extends RepoMarketBot {

  private static final Logger log = LoggerFactory.getLogger(TradingParticipantBot.class);

  private class TradeStreamer implements Runnable {

    private final CsvFile reader;
    private final long delay; // mS between registrations

    TradeStreamer(File tradeFile, int delay) {
      this.reader = new CsvFile(tradeFile);
      this.delay = delay;
    }

    @Override
    public void run() {
      commandStream().forEach(this::submit);
      try {
        reader.close();
      } catch (IOException e) {
        log.warn("Error closing trade reader: {}", e);
      }
    }

    private void submit(Map.Entry<String, Command> commandEntry) {

      log.debug("{} registers trade {}", getParty(), commandEntry.getKey());
      submitCommands(
          TRADE_INJECTION_WORKFLOW_ID, Collections.singletonList(commandEntry.getValue()));
    }

    Stream<Map.Entry<String, Command>> commandStream() {
      try {
        return reader
            .open()
            .recordStream()
            .filter(r -> r.get("lender").equals(getParty()))
            .peek(r -> pause(delay))
            .map(this::toTradeCommand);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    private Map.Entry<String, Command> toTradeCommand(Map<String, String> record) {

      logMessage(
          String.format(
              "requests trade with %s, tradeId '%s'",
              record.get("borrower"), record.get("tradeId")));

      TradingParticipant.ContractId contractId =
          new TradingParticipant.ContractId(tradingParticipantId);
      ExerciseCommand exerciseCommand =
          contractId.exerciseRequestTrade(
              record.get("borrower"),
              Long.parseLong(record.get("tradeId")),
              record.get("cusip"),
              toInstant(record.get("settlementDate")),
              toInstant(record.get("tradeDate")),
              new BigDecimal(record.get("collateralQuantity")),
              new BigDecimal(record.get("price")),
              new BigDecimal(record.get("repoRate")),
              Long.parseLong(record.get("term")),
              new BigDecimal(record.get("startAmount")),
              new BigDecimal(record.get("endAmount")),
              record.get("currency"));

      return new AbstractMap.SimpleEntry<>(record.get("tradeId"), exerciseCommand);
    }

    Instant toInstant(String dateString) {
      LocalDate d = LocalDate.parse(dateString);
      log.trace("date string {} parses to {}", dateString, d);
      return d.atStartOfDay(ZoneOffset.UTC).toInstant();
    }
  }

  // Constants

  // Template ID's

  private final Identifier inviteTradingParticipantTemplateId;
  private final Identifier tradeRegistrationRequestTemplateId;
  private final Identifier tradingParticipantTemplateId;

  // Argument parsing

  @Option(
      name = "--injectDelay",
      aliases = "-d",
      metaVar = "INJECTION_DELAY",
      usage = "Delay for INJECTION_DELAY mS between each trade request")
  private int injectDelay = 2000;

  @Argument(index = 0, metaVar = "PARTY", required = true, usage = "The trading party")
  private String participant = null;

  @Argument(index = 1, metaVar = "TRADE_FILE", usage = "The trades to inject")
  private File tradeFile = null;

  // Instance vars

  private String tradingParticipantId = null;

  public TradingParticipantBot(RepoTradingMain mainClass, String party) {
    super(mainClass, party);
    this.tradeRegistrationRequestTemplateId = TradeRegistrationRequest.TEMPLATE_ID;
    this.inviteTradingParticipantTemplateId = InviteTradingParticipant.TEMPLATE_ID;
    this.tradingParticipantTemplateId = TradingParticipant.TEMPLATE_ID;
  }

  private ControlServer.ControlResult handleInjectTradeFile(HttpExchange exchange) {

    if (tradingParticipantId == null) {
      log.debug("injectTradeFile: trading participnat not set");
      return new ControlServer.ControlResult(400, "trading participnat not set\n");
    }

    String queryString = exchange.getRequestURI().getQuery();
    Map<String, String> params = ControlServer.parseQuery(queryString);
    String fileName = params.get("fileName");

    if (fileName == null) {
      log.debug("injectTradeFile: No file name");
      return new ControlServer.ControlResult(400, "No file name\n");
    }

    tradeFile = new File(fileName);

    if (!tradeFile.exists()) {
      log.debug("injectTradeFile: No file: {}", fileName);
      return new ControlServer.ControlResult(400, "No file named '" + fileName + "'\n");
    }

    // Stream the trades
    Thread tradeStreamer = new Thread(new TradeStreamer(tradeFile, injectDelay), "tradeStreamer");
    tradeStreamer.start();

    return new ControlServer.ControlResult(200, "Injected\n");
  }

  @Override
  public int run(String[] args) throws java.io.IOException {
    if (!RepoTradingMain.parseArguments(this, args)) {
      return 1;
    }
    setParty(participant);

    new ControlServer(getConfiguration().getTradingParties().get(getParty()).getPort())
        .addHandler("/injectTradeFile", this::handleInjectTradeFile)
        .start();

    return super.run(args);
  }

  @Override
  public Stream<CommandsAndPendingSet> process(LedgerViewFlowable.LedgerView<Template> ledgerView) {

    log.trace("TradingParticpant process: {}, state: {}", getParty(), ledgerView);

    return Stream.of(
            ledgerView.getContracts(inviteTradingParticipantTemplateId).entrySet().stream()
                .map(this::acceptTradingInvite),
            ledgerView.getContracts(tradingParticipantTemplateId).entrySet().stream()
                .map(this::injectTrades),
            ledgerView.getContracts(tradeRegistrationRequestTemplateId).entrySet().stream()
                .map(this::acceptTradeRequest),
            ledgerView.getContracts(netObligationRequestTemplateId).entrySet().stream()
                .map(this::acceptNetObligation))
        .flatMap(s -> s);
  }

  private CommandsAndPendingSet acceptTradingInvite(Map.Entry<String, Template> entry) {

    log.debug("{} accepts trading invitation", getParty());

    logMessage(getParty() + " accepts invitation as trading participant");

    String contractId = entry.getKey();

    return newCommandAndPendingSet(
        ONBOARDING_WORKFLOW_ID,
        Collections.singletonList(
            newExercise(inviteTradingParticipantTemplateId, contractId, "AcceptTradingInvite")),
        HashTreePMap.singleton(
            inviteTradingParticipantTemplateId, HashTreePSet.singleton(contractId)));
  }

  private CommandsAndPendingSet injectTrades(Map.Entry<String, Template> entry) {

    CommandsAndPendingSet commands = CommandsAndPendingSet.empty;
    if (tradingParticipantId == null) {

      log.debug("TradingParticipant created: {}", getParty());

      tradingParticipantId = entry.getKey();

      if (tradeFile != null) {
        Thread tradeStreamer =
            new Thread(new TradeStreamer(tradeFile, injectDelay), "tradeStreamer");
        tradeStreamer.start();
      }
    }

    return commands;
  }

  private CommandsAndPendingSet acceptTradeRequest(Map.Entry<String, Template> entry) {

    assert (entry.getValue().getClass() == TradeRegistrationRequest.class);
    String contractId = entry.getKey();
    log.debug("{} receives trade request, cid={}", getParty(), contractId);

    TradeRegistrationRequest request = (TradeRegistrationRequest) entry.getValue();
    CommandsAndPendingSet commands = CommandsAndPendingSet.empty;

    if (tradingParticipantId != null && request.tradeCounterParty.equals(getParty())) {
      // If this is for me, and we've been accepted as a trading particpant...
      logMessage(
          String.format(
              "accepting tradeId %s, lender=%s",
              request.tradeInfo.tradeId, request.tradeRequester));

      commands =
          newCommandAndPendingSet(
              TRADE_INJECTION_WORKFLOW_ID,
              Collections.singletonList(
                  newExercise(tradeRegistrationRequestTemplateId, contractId, "RegisterTrade")),
              HashTreePMap.singleton(
                  tradeRegistrationRequestTemplateId, HashTreePSet.singleton(contractId)));
    }
    return commands;
  }

  private CommandsAndPendingSet acceptNetObligation(Map.Entry<String, Template> entry) {

    NetObligationRequest netObligationRequest = (NetObligationRequest) entry.getValue();

    String contractId = entry.getKey();

    log.debug(
        "accept net obligation: contractId={}, participantId={}, isBuy={}, cusip={}, paymentAmount={}, quantity={}",
        contractId,
        netObligationRequest.participantId,
        netObligationRequest.isBuy,
        netObligationRequest.cusip,
        netObligationRequest.paymentAmount,
        netObligationRequest.quantity);

    return newCommandAndPendingSet(
        NETTING_WORKFLOW_ID,
        Collections.singletonList(
            newExercise(netObligationRequestTemplateId, contractId, "AcceptNetObligation")),
        HashTreePMap.singleton(netObligationRequestTemplateId, HashTreePSet.singleton(contractId)));
  }

  @Override
  public TransactionFilter getTransactionFilter() {
    InclusiveFilter inclusiveFilter =
        new InclusiveFilter(
            new HashSet<>(
                Arrays.asList(
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
