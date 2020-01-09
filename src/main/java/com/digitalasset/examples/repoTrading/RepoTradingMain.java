/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.examples.repoTrading;

import com.daml.ledger.rxjava.DamlLedgerClient;
import com.digitalasset.examples.repoTrading.util.Configuration;
import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepoTradingMain {

  // Public Constants

  public static final String APP_ID = "RepoTradingMarket";

  // Private fields

  private static final Logger log = LoggerFactory.getLogger(RepoTradingMain.class);

  private static List<String> COMMAND_VERBS =
      Arrays.asList("operator", "ccp", "paymentProcessor", "tradingParticipant", "all");

  // Party names

  private static final String OPERATOR_NAME = "Operator";
  private static final String CCP_NAME = "CCP";
  private static final String PMTPROC_NAME = "PaymentProcessor";

  // Local state
  private static boolean exitFlag = false;
  private static int exitCode = 0;

  private static final Thread mainThread = Thread.currentThread();

  @Option(
      name = "--host",
      aliases = {"-h"},
      metaVar = "HOST",
      usage = "host to connect to")
  private String host = "localhost";

  @Option(
      name = "--port",
      aliases = {"-p"},
      metaVar = "PORT",
      usage = "port to connect to")
  private int port = 6865;

  @Option(
      name = "--config",
      aliases = {"-c"},
      metaVar = "CONFIG FILE",
      usage = "configuration file to load")
  private File configFile = new File("config.yaml");

  @Argument(
      index = 0,
      required = true,
      metaVar = "COMMAND",
      usage =
          "the type of bot to run: one of 'operator', 'ccp', 'paymentProcessor', or 'tradingParticipant' or 'all'")
  private String command = "all";

  private String ledgerId;

  private DamlLedgerClient client;

  private Configuration configuration;

  // Methods

  public static void main(String[] args) {

    try {
      // Run the main class and wait for termination
      exitCode = new RepoTradingMain().run(args);
    } catch (Exception e) {
      System.err.printf("Exception starting market");
      e.printStackTrace();
      System.exit(10);
    }
    if (exitCode == 0) {
      waitForTermination();
    }
    System.exit(exitCode);
  }

  private int run(String args[]) throws Exception {

    // Args4j won't parse the full command line wth options for both program and command - so split
    // them apart
    // and parse separately

    String[] cmdArgs = splitCmd(args);
    String[] botArgs = Arrays.copyOfRange(args, cmdArgs.length, args.length);

    if (!parseArguments(this, cmdArgs)) return 1;

    DamlLedgerClient ledgerClient = DamlLedgerClient.newBuilder(host, port).build();
    waitForSandbox(ledgerClient, host, port);

    return startBots(ledgerClient, botArgs);
  }

  public int startBots(DamlLedgerClient ledgerClient, String[] botArgs) throws Exception {
    this.client = ledgerClient;

    configuration = new Configuration(configFile);

    ledgerId = ledgerClient.getLedgerId();

    if (command.equals("all")) {
      String[] tradingBotArgs = new String[botArgs.length + 1];
      System.arraycopy(botArgs, 0, tradingBotArgs, 1, botArgs.length);

      System.out.println("Starting all bots defined in the config file");
      for (String party : configuration.getTradingParties().keySet()) {
        tradingBotArgs[0] = party;
        new TradingParticipantBot(this, null).run(tradingBotArgs);
      }
      new OperatorBot(this, getOperatorName()).run(new String[0]);
      new ClearingHouseBot(this, getCcpName()).run(new String[0]);
      new PaymentProcessorBot(this, getPaymentProcessorName()).run(new String[0]);

      return 0;
    } else {
      RepoMarketBot myBot = null;

      switch (command) {
        case "operator":
          myBot = new OperatorBot(this, getOperatorName());
          break;

        case "ccp":
          myBot = new ClearingHouseBot(this, getCcpName());
          break;

        case "paymentProcessor":
          myBot = new PaymentProcessorBot(this, getPaymentProcessorName());
          break;

        case "tradingParticipant":
          myBot = new TradingParticipantBot(this, null);
          break;
      }

      if (myBot == null) {
        logError(command, "unknown command");
        return 2;
      }
      return myBot.run(botArgs);
    }
  }

  private static void waitForTermination() {

    // Sleep until we are interrupted and the exitFlag is set
    while (!exitFlag) {
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        if (exitFlag) return;
      }
    }
  }

  public static void terminate(int ec) {
    exitFlag = true;
    exitCode = ec;
    mainThread.interrupt();
  }

  /**
   * Args4j cannot parse cmmand lines with options for both the initial and command arguments. So
   * split out the command and it's opions and parse separately.
   *
   * <p>Look for the command verb and collect it and any preceeding arguments.
   */
  private static String[] splitCmd(String[] args) {
    int i = 0;
    while (i < args.length && !COMMAND_VERBS.contains(args[i])) i++;
    return i == args.length ? args : Arrays.copyOfRange(args, 0, i + 1);
  }

  public static boolean parseArguments(Object bean, String[] args) {
    CmdLineParser parser = new CmdLineParser(bean);

    try {
      parser.parseArgument(args);
    } catch (CmdLineException e) {
      logError("", e.getMessage());
      parser.printUsage(System.err);
      return false;
    }

    return true;
  }

  // Getters

  public String getLedgerId() {
    return ledgerId;
  }

  public DamlLedgerClient getClient() {
    return client;
  }

  public String getOperatorName() {
    return OPERATOR_NAME;
  }

  public String getCcpName() {
    return CCP_NAME;
  }

  public String getPaymentProcessorName() {
    return PMTPROC_NAME;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  // Logging

  public static synchronized void logMessage(String command, String message) {
    commandMessage(System.out, command, message);
  }

  public static synchronized void logError(String command, String message) {
    commandMessage(System.err, command, message);
  }

  private static synchronized void commandMessage(
      PrintStream stream, String command, String message) {
    stream.println(command + ": " + message);
  }

  private static void waitForSandbox(DamlLedgerClient client, String host, int port) {
    boolean connected = false;
    while (!connected) {
      try {
        client.connect();
        connected = true;
      } catch (Exception ignored) {
        System.out.println(String.format("Connecting to sandbox at %s:%s", host, port));
        try {
          Thread.sleep(1000);
        } catch (InterruptedException ignored2) {
        }
      }
    }
    System.out.println(String.format("Connected to sandbox at %s:%s", host, port));
  }
}
