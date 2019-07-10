// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

export const version = {
  schema: 'navigator-config',
  major: 2,
  minor: 0,
};

import { DamlLfValue } from '@da/ui-core';

var formatTime = function(timestamp) { return timestamp.substring(0, 10) };

// var createColumn = function(key, title, projection, width, alignment = "left", weight = 0, sortable = true) {
//     var createCell = function (data) {
//         console.log(data.rowData);
//         return { type: 'text', value: projection(data.rowData) }
//     };

//     return {
//         key: key,
//         title: title,
//         createCell: createCell,
//         sortable: sortable,
//         width: width,
//         weight: weight,
//         alignment: alignment
//     }
// };

/**
 title, width and proj are optional

 if proj is null and key is "id" then it will default to the contract id
 if proj is null and key is not "id" then it will default to stringified single or array value of rowData.key
*/
var createColumn = function(key, title = toTitle(key), proj, width = 80, alignment = "left", weight = 0, sortable = true) {
// function createCol(key, title = toTitle(key), width = 80, proj) {
  return {
      key: key,
      title: title,
      createCell: ({ rowData }) => ({
          type: "text",
          value: valueFunction(rowData, key, proj)
      }),
      sortable: sortable,
      width: width,
      weight: weight,
      alignment: alignment,
  };
}

function formatIfNum(val) {
  var n = Number(val);
  if (Number.isNaN(n)) return val;
  else return n.toLocaleString();
}

function valueFunction(rowData, key, proj) {
  return (
      proj == null
      ?
      (
          Array.isArray(DamlLfValue.toJSON(rowData.argument)[key])
          ?
          DamlLfValue.toJSON(rowData.argument)[key].join(", ")
          :
          (
              key == "id"
              ?
              rowData.id
              :
              formatIfNum(DamlLfValue.toJSON(rowData.argument)[key])
          )
      )
      :
      formatIfNum(proj(DamlLfValue.toJSON(rowData.argument))));
}

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
        createColumn("owner", "Owner", x => x.argument.owner, 50),
        createColumn("symbol", "Symbol", x => x.argument.cusip || x.argument.currency, 50),
        createColumn("amount", "Amount", x => x.argument.amount || x.argument.collateralQuantity, 80)
      ]
    },
    trades: {
      type: "table-view",
      title: "Trades",
      includeArchived: true,
      source: {type: "contracts", filter: [ { field: "template.id", value: "Main.Trade:Trade", } ], search: "", sort: [ { field: "id",direction: "ASCENDING" } ] },
      columns: [
        createColumn("id", "Contract ID", x => x.id, 40),
        createColumn("tradeId", "Trade ID", x => x.tradeInfo.tradeId, 40),
        createColumn("tradeDate", "Trade Date", x => formatTime(x.tradeInfo.tradeDate), 100),
        createColumn("settlementDate", "Settlement Date", x => formatTime(x.tradeInfo.settlementDate),100),
        createColumn("cusip", "CUSIP", x => x.tradeInfo.cusip, 50),
        createColumn("buyer", "Buyer", x => x.buyer, 80,),
        createColumn("seller", "Seller", x => x.seller, 80),
        createColumn("ccy", "CCY", x => x.tradeInfo.currency, 25, "center"),
        createColumn("startAmount", "Start Amount", x => x.tradeInfo.startAmount, 90, "right"),
        createColumn("endAmount", "End Amount", x => x.tradeInfo.endAmount, 90, "right"),
        createColumn("collateralQuantity", "Collateral Quantity", x => x.tradeInfo.collateralQuantity, 90, "right"),
        createColumn("price", "Price", x => x.tradeInfo.price, 20, "right"),
        createColumn("repoRate", "Repo Rate", x => x.tradeInfo.repoRate, 20, "right"),
        createColumn("term", "Term (Weeks)", x => x.tradeInfo.term, 20, "right")
      ]
    },
    dvps: {
      type: "table-view",
      title: "DvPs",
      includeArchived: true,
      source: { type: "contracts", filter: [ { field: "template.id", value: "Main.DvP:DvP", } ], search: "", sort: [ { field: "id", direction: "ASCENDING" } ] },
      columns: [
        createColumn("id", "Contract ID",  x => x.id, 40),
        createColumn("type", "Type", x => x.template.id.substring(9,x.template.id.lastIndexOf('@')), 80),
        createColumn("payer", "Seller", x => x.argument.payer, 80),
        createColumn("receiver", "Buyer", x => x.argument.receiver, 80,),
        createColumn("settlementDate", "Settlement Date", x => formatTime(x.argument.settlementDate), 100),
        createColumn("cusip", "CUSIP", x => x.argument.cusip, 50),
        createColumn("ccy", "CCY", x => x.argument.currency, 25, "center"),
        createColumn("paymentAmount", "Settlement Amount", x => x.argument.paymentAmount, 90, "right"),
        createColumn("quantity", "Collateral Quantity", x => x.argument.quantity, 90, "right")
     ]
    }
  }
}