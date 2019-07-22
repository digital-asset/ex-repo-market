/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.examples.repoTrading;

import static org.junit.jupiter.api.Assertions.*;

import com.digitalasset.examples.repoTrading.util.CsvFile;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

@RunWith(JUnitPlatform.class)
@DisplayName("A CsvFile")
public class CsvFileTests {

  File testFile;

  @BeforeEach
  void setupFile() {
    try {
      testFile = File.createTempFile("tmp", "csvTest");
      BufferedWriter out = new BufferedWriter(new FileWriter(testFile.getPath()));
      ;
      out.write("field1,field2,field3\nval1,val2,val3\n");
      out.close();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void skipsTheHeader() {
    CsvFile csvf = new CsvFile(testFile);
    try {
      List<Map<String, String>> recs = csvf.open().recordStream().collect(Collectors.toList());
      assertEquals(1, recs.size());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void mapsTheRecord() {
    CsvFile csvf = new CsvFile(testFile);
    try {
      List<Map<String, String>> recs = csvf.open().recordStream().collect(Collectors.toList());
      Map rec = recs.get(0);
      assertEquals(3, rec.size());
      assertEquals("val1", rec.get("field1"));
      assertEquals("val2", rec.get("field2"));
      assertEquals("val3", rec.get("field3"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
