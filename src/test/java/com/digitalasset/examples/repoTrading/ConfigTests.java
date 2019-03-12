// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: (Apache-2.0 OR BSD-3-Clause)

package com.digitalasset.examples.repoTrading;
import static org.junit.jupiter.api.Assertions.*;

import com.digitalasset.examples.repoTrading.util.Configuration;
import com.digitalasset.examples.repoTrading.util.CsvFile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@RunWith(JUnitPlatform.class)
@DisplayName("A Configuration")
class ConfigTests {

    @Test
    void readsAFile() throws FileNotFoundException {

        Configuration c = new Configuration(new File("src/test/resources/testConfig.yaml"));

        assertEquals("CCP",c.getCcp().getName());
        assertEquals(9000,c.getCcp().getPort());
        
        assertEquals(4,c.getTradingParties().size());

        List<String> parties= Arrays.asList("Citi","HSBC","Barclays","JPMorgan");

        for(String p : parties) {
            assertEquals(p,c.getTradingParties().get(p).getName());
            assertEquals(9001+parties.indexOf(p),c.getTradingParties().get(p).getPort());
        }
    }
}
