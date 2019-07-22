/*
 * Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
import { DamlLfValue } from '@da/ui-core';

export const version = {
  schema: 'navigator-config',
  major: 2,
  minor: 0,
};

var formatTime = function(timestamp) { return timestamp.substring(0, 10) };

var createColumn = function(key, title, projection, width, alignment = "left", weight = 0, sortable = true) {
    var createCell = function (data) {
        return { type: 'text', value: projection(data.rowData) }
    };

    return {
        key: key,
        title: title,
        createCell: createCell,
        sortable: sortable,
        width: width,
        weight: weight,
        alignment: alignment
    }
};

export const customViews = (userId, party, role) => {
  return {
    ccp: {
      type: "table-view",
      title: "CCP Role",
      source: { type: "contracts", filter: [ { field: "template.id", value: "Main.CCP:CCP", }, ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
      columns: [
        createColumn("id", "Contract ID", x => x.id, 40),
        createColumn("type", "Type", x => x.template.id, 60),
      ]
    },
    assets: {
      type: "table-view",
      title: "Assets",
      source: { type: "contracts", filter: [ { field: "argument.owner", value: "", }, ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
      columns: [
        createColumn("id", "Contract ID", x => x.id, 40),
        createColumn("type", "Type", x => x.template.id.charAt(5) == "C" ? x.template.id.substring(5, 9) : x.template.id.substring(5, 13), 60),
        createColumn("owner", "Owner", x => DamlLfValue.toJSON(x.argument).owner, 50),
        createColumn("symbol", "Symbol", x => DamlLfValue.toJSON(x.argument).cusip || DamlLfValue.toJSON(x.argument).currency, 50),
        createColumn("amount", "Amount", x => DamlLfValue.toJSON(x.argument).amount || DamlLfValue.toJSON(x.argument).collateralQuantity, 80)
      ]
    },
    trades: {
      type: "table-view",
      title: "Trades",
      includeArchived: true,
      source: {type: "contracts", filter: [ { field: "template.id", value: "Main.Trade:Trade", } ], search: "", sort: [ { field: "id",direction: "ASCENDING" } ] },
      columns: [
        createColumn("id", "Contract ID", x => x.id, 40),
        createColumn("tradeId", "Trade ID", x => DamlLfValue.toJSON(x.argument).tradeInfo.tradeId, 40),
        createColumn("tradeDate", "Trade Date", x => formatTime(DamlLfValue.toJSON(x.argument).tradeInfo.tradeDate), 100),
        createColumn("settlementDate", "Settlement Date", x => formatTime(DamlLfValue.toJSON(x.argument).tradeInfo.settlementDate),100),
        createColumn("cusip", "CUSIP", x => DamlLfValue.toJSON(x.argument).tradeInfo.cusip, 50),
        createColumn("buyer", "Buyer", x => DamlLfValue.toJSON(x.argument).buyer, 80,),
        createColumn("seller", "Seller", x => DamlLfValue.toJSON(x.argument).seller, 80),
        createColumn("ccy", "CCY", x => DamlLfValue.toJSON(x.argument).tradeInfo.currency, 25, "center"),
        createColumn("startAmount", "Start Amount", x => DamlLfValue.toJSON(x.argument).tradeInfo.startAmount, 90, "right"),
        createColumn("endAmount", "End Amount", x => DamlLfValue.toJSON(x.argument).tradeInfo.endAmount, 90, "right"),
        createColumn("collateralQuantity", "Collateral Quantity", x => DamlLfValue.toJSON(x.argument).tradeInfo.collateralQuantity, 90, "right"),
        createColumn("price", "Price", x => DamlLfValue.toJSON(x.argument).tradeInfo.price, 20, "right"),
        createColumn("repoRate", "Repo Rate", x => DamlLfValue.toJSON(x.argument).tradeInfo.repoRate, 20, "right"),
        createColumn("term", "Term (Weeks)", x => DamlLfValue.toJSON(x.argument).tradeInfo.term, 20, "right")
      ]
    },
    dvps: {
      type: "table-view",
      title: "DvPs",
      includeArchived: true,
      source: { type: "contracts", filter: [ { field: "template.id", value: "DvP", } ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
      columns: [
        createColumn("id", "Contract ID",  x => x.id, 40),
        createColumn("type", "Type", x => x.template.id.substring(9,x.template.id.lastIndexOf('@')), 80),
        createColumn("payer", "Seller", x => DamlLfValue.toJSON(x.argument).payer, 80),
        createColumn("receiver", "Buyer", x => DamlLfValue.toJSON(x.argument).receiver, 80),
        createColumn("settlementDate", "Settlement Date", x => formatTime(DamlLfValue.toJSON(x.argument).settlementDate), 100),
        createColumn("cusip", "CUSIP", x => DamlLfValue.toJSON(x.argument).cusip, 50),
        createColumn("ccy", "CCY", x => DamlLfValue.toJSON(x.argument).currency, 25, "center"),
        createColumn("paymentAmount", "Settlement Amount", x => DamlLfValue.toJSON(x.argument).paymentAmount, 90, "right"),
        createColumn("quantity", "Collateral Quantity", x => DamlLfValue.toJSON(x.argument).quantity, 90, "right")
     ]
    }
  }
}
