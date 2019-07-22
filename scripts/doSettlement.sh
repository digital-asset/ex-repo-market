#!/usr/bin/env bash
#
# Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0
#

date=$1
if [[ "$date" = "" ]]
then
  echo Please suppy a settlement date YYY-MM-DD 1>&2
  exit 1
fi

port=`sed -n -e '/ccp/,/tradingParties/p' config.yaml|awk '/port:/ { print $2 }'`

curl http://localhost:${port}/settle?date=$date
