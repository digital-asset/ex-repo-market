/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.examples.repoTrading.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * A simple HTTP server to receive Http control requests. The server maps routes to handler methods
 * which can activate various bot behaviors.
 */
public class ControlServer {

  public static class ControlResult extends AbstractMap.SimpleImmutableEntry<Integer, String> {

    public ControlResult(Integer status, String body) {
      super(status, body);
    }

    Integer getStatus() {
      return getKey();
    }

    String getBody() {
      return getValue();
    }
  }

  private HttpServer server;

  public static Map<String, String> parseQuery(String queryString) {
    Map<String, String> r = new HashMap<>();
    if (queryString != null) {
      for (String p : queryString.split("&")) {
        String[] parts = p.split("=");
        if (parts.length == 2) {
          r.put(parts[0], parts[1]);
        }
      }
    }
    return r;
  }

  public ControlServer(int port) throws java.io.IOException {
    this.server = HttpServer.create(new InetSocketAddress(port), 0);
  }

  /**
   * Flow style addition of handlers
   *
   * @param route - the handler route
   * @param f - handler function
   * @return this - the ControlServer
   */
  public ControlServer addHandler(String route, Function<HttpExchange, ControlResult> f) {

    server.createContext(
        route,
        new HttpHandler() {
          @Override
          public void handle(HttpExchange httpExchange) throws IOException {
            ControlResult r = f.apply(httpExchange);
            byte[] b = r.getBody().getBytes();
            httpExchange.sendResponseHeaders(r.getStatus(), b.length);
            OutputStream os = httpExchange.getResponseBody();
            os.write(b);
            os.close();
          }
        });

    return this;
  }

  public ControlServer start() {
    server.start();
    return this;
  }

  public void stop() {
    server.stop(0);
  }
}
