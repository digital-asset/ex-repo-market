/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.examples.repoTrading.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

public class Configuration {

  public class BotConfiguration {
    private String name;
    private int port;

    BotConfiguration(String name, int port) {
      this.name = name;
      this.port = port;
    }

    BotConfiguration(Map map) {
      this((String) map.get("name"), (int) map.get("port"));
    }

    public String getName() {
      return name;
    }

    public int getPort() {
      return port;
    }
  }

  private BotConfiguration ccp;
  private Map<String, BotConfiguration> tradingParties;

  public Configuration(File yamlFile) throws FileNotFoundException {
    FileInputStream is = new FileInputStream(yamlFile);
    Yaml yaml = new Yaml();

    Map map = yaml.load(is);
    map = (Map) map.get("configuration");

    ccp = new BotConfiguration((Map) map.get("ccp"));
    tradingParties = new HashMap<>();
    for (Object t : ((List) map.get("tradingParties"))) {
      Map m = (Map) t;
      BotConfiguration b = new BotConfiguration(m);
      tradingParties.put(b.getName(), b);
    }
  }

  public BotConfiguration getCcp() {
    return ccp;
  }

  public Map<String, BotConfiguration> getTradingParties() {
    return tradingParties;
  }
}
