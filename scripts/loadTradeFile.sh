#!/usr/bin/env bash
#
# Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#

tradeFile=$1
if [[ "$tradeFile" = "" ]]
then
  echo Please suppliy a trade file path 1>&1
fi

for p in `sed -n -e '/tradingParties/,$p' config.yaml|awk '/port:/ { print $2 }'`
do
  curl http://localhost:$p/injectTradeFile?fileName=$tradeFile
done