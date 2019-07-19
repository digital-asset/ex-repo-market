#!/usr/bin/env bash

# Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
# SPDX-License-Identifier: Apache-2.0

BASE=$(dirname $0)/..
VERSION=0.0.1-SNAPSHOT
JAR=${BASE}/target/ex-repo-trading-${VERSION}.jar

run() {
 java -jar -Dlogback.configurationFile=logback.xml $JAR  $@ &
}

stop () {
  printf "\nstopping participants...\n"
  os="$(uname -s)"
  if [ "$os" == "Linux" ]
  then
    psSwitch=ae
  else
    psSwitch="-ae"
  fi
  kill $(ps $psSwitch|grep "ex-repo-trading-.*\.jar"|grep -v grep|awk '{print $1}')
  state=stop
}

wait() {
  while [ $state = running ]; do sleep 1; done
}

trap stop 1 2 3
state=running

injectDelay=""

while getopts "d:" opt
do
  case $opt in
  "d") injectDelay="--injectDelay $OPTARG" ;;
  "*") echo "Invalid option: -$opt" >&2 ;;
  esac
  shift $((OPTIND-1))
done

TRADE_FILE=${1:-data/Trades12-2018-06-28.csv}

rm -f $BASE/logs/apps.log

for p in `sed -n -e '/tradingParties/,$p' config.yaml|awk '/name:/ { print $3 }'`
do
  run tradingParticipant $injectDelay $p $TRADE_FILE
done

run operator
run ccp
run paymentProcessor

wait
