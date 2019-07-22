#!/usr/bin/env bash
#
# Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#

port=`sed -n -e '/ccp/,/tradingParties/p' config.yaml|awk '/port:/ { print $2 }'`

curl http://localhost:$port/tradeState
