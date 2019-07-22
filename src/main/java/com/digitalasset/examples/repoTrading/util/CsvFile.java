/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.examples.repoTrading.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** A CSV file reader than can provide a stream of records, one per line. */
public class CsvFile {

  private final File file;
  private BufferedReader reader;
  private String[] headers = null;

  public CsvFile(File file) {
    this.file = file;
  }

  public File getFile() {
    return file;
  }

  public CsvFile open() throws FileNotFoundException {
    reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
    headers = null;
    return this;
  }

  public void close() throws IOException {
    reader.close();
  }

  public Stream<Map<String, String>> recordStream() {
    return reader.lines().map(this::toRecord).skip(1);
  }

  private Map<String, String> toRecord(String line) {
    String[] splitLIne = line.split(",");
    Map<String, String> record = new HashMap<>();
    if (headers == null) {
      headers = splitLIne;
    } else {
      for (int i = 0; i < headers.length; i++) {
        record.put(headers[i], splitLIne[i]);
      }
    }
    return record;
  }
}
