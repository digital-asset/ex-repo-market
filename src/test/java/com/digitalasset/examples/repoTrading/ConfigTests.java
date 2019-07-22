/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.examples.repoTrading;

import static org.junit.jupiter.api.Assertions.*;

import com.digitalasset.examples.repoTrading.util.Configuration;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
@DisplayName("A Configuration")
class ConfigTests {

  @Test
  void readsAFile() throws FileNotFoundException {

    Configuration c = new Configuration(new File("src/test/resources/testConfig.yaml"));

    assertEquals("CCP", c.getCcp().getName());
    assertEquals(9000, c.getCcp().getPort());

    assertEquals(4, c.getTradingParties().size());

    List<String> parties = Arrays.asList("Citi", "HSBC", "Barclays", "JPMorgan");

    for (String p : parties) {
      assertEquals(p, c.getTradingParties().get(p).getName());
      assertEquals(9001 + parties.indexOf(p), c.getTradingParties().get(p).getPort());
    }
  }
}
