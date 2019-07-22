/**
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.digitalasset.examples.repoTrading.util;

import com.daml.ledger.javaapi.data.Identifier;
import com.daml.ledger.javaapi.data.Record;
import com.daml.ledger.javaapi.data.Template;
import main.cash.Cash;
import main.cash.LockedCash;
import main.cashrequest.CashRequest;
import main.ccp.CCP;
import main.ccp.CCPInvite;
import main.ccp.InitiateSettlementControl;
import main.ccp.InviteClearingHouse;
import main.dvp.AllocatedDvP;
import main.dvp.CashAllocatedDvP;
import main.dvp.DvP;
import main.dvp.SettledDvP;
import main.genesis.Genesis;
import main.netobligation.NetObligation;
import main.netobligation.NetObligationRequest;
import main.netting.NettingGroup;
import main.security.MergedSecurity;
import main.security.Security;
import main.trade.NovatedTrade;
import main.trade.Trade;
import main.trade.TradeRegistrationRequest;
import main.tradingparticipant.InviteTradingParticipant;
import main.tradingparticipant.TradingParticipant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelMapper {

  private static final Logger log = LoggerFactory.getLogger(ModelMapper.class);

  public static Template domainObjectFromRecord(Identifier templateId, Record createArgs) {
    Template modelObject;
    if (templateId.equals(Cash.TEMPLATE_ID)) {
      modelObject = Cash.fromValue(createArgs);
    } else if (templateId.equals(LockedCash.TEMPLATE_ID)) {
      modelObject = LockedCash.fromValue(createArgs);
    } else if (templateId.equals(Security.TEMPLATE_ID)) {
      modelObject = Security.fromValue(createArgs);
    } else if (templateId.equals(MergedSecurity.TEMPLATE_ID)) {
      modelObject = MergedSecurity.fromValue(createArgs);
    } else if (templateId.equals(NovatedTrade.TEMPLATE_ID)) {
      modelObject = NovatedTrade.fromValue(createArgs);
    } else if (templateId.equals(DvP.TEMPLATE_ID)) {
      modelObject = DvP.fromValue(createArgs);
    } else if (templateId.equals(CashAllocatedDvP.TEMPLATE_ID)) {
      modelObject = CashAllocatedDvP.fromValue(createArgs);
    } else if (templateId.equals(AllocatedDvP.TEMPLATE_ID)) {
      modelObject = AllocatedDvP.fromValue(createArgs);
    } else if (templateId.equals(SettledDvP.TEMPLATE_ID)) {
      modelObject = SettledDvP.fromValue(createArgs);
    } else if (templateId.equals(NetObligationRequest.TEMPLATE_ID)) {
      modelObject = NetObligationRequest.fromValue(createArgs);
    } else if (templateId.equals(NetObligation.TEMPLATE_ID)) {
      modelObject = NetObligation.fromValue(createArgs);
    } else if (templateId.equals(TradeRegistrationRequest.TEMPLATE_ID)) {
      modelObject = TradeRegistrationRequest.fromValue(createArgs);
    } else if (templateId.equals(Trade.TEMPLATE_ID)) {
      modelObject = Trade.fromValue(createArgs);
    } else if (templateId.equals(Genesis.TEMPLATE_ID)) {
      modelObject = Genesis.fromValue(createArgs);
    } else if (templateId.equals(InviteClearingHouse.TEMPLATE_ID)) {
      modelObject = InviteClearingHouse.fromValue(createArgs);
    } else if (templateId.equals(InitiateSettlementControl.TEMPLATE_ID)) {
      modelObject = InitiateSettlementControl.fromValue(createArgs);
    } else if (templateId.equals(CCPInvite.TEMPLATE_ID)) {
      modelObject = CCPInvite.fromValue(createArgs);
    } else if (templateId.equals(CCP.TEMPLATE_ID)) {
      modelObject = CCP.fromValue(createArgs);
    } else if (templateId.equals(InviteTradingParticipant.TEMPLATE_ID)) {
      modelObject = InviteTradingParticipant.fromValue(createArgs);
    } else if (templateId.equals(TradingParticipant.TEMPLATE_ID)) {
      modelObject = TradingParticipant.fromValue(createArgs);
    } else if (templateId.equals(NettingGroup.TEMPLATE_ID)) {
      modelObject = NettingGroup.fromValue(createArgs);
    } else if (templateId.equals(CashRequest.TEMPLATE_ID)) {
      modelObject = CashRequest.fromValue(createArgs);
    } else {
      throw new IllegalStateException("Unknown contract type: " + templateId);
    }
    log.trace("{} maps to {} ", templateId, modelObject.getClass());
    return modelObject;
  }
}
