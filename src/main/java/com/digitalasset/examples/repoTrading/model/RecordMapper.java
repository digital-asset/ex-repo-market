// Copyright (c) 2019, Digital Asset (Switzerland) GmbH and/or its affiliates. All rights reserved.
// SPDX-License-Identifier: Apache-2.0

package com.digitalasset.examples.repoTrading.model;

import com.daml.ledger.javaapi.data.DamlList;
import com.daml.ledger.javaapi.data.Identifier;
import com.daml.ledger.javaapi.data.Record;
import com.daml.ledger.javaapi.data.Timestamp;
import com.daml.ledger.javaapi.data.Value;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.function.Supplier;

public class RecordMapper implements DomainObject {

    public static final long MICRO_SEC_PER_DAY = 1000000L * 60 * 60 * 24;

    static class ModelMappingException extends RuntimeException {

        ModelMappingException(String message) {
            super(message);
        }

//        public static Supplier<ModelMappingException> missingField(String fieldName) {
//            return () -> new ModelMappingException("No such field: "+fieldName);
//        }

        static Supplier<ModelMappingException> badType(Value v, int index, String requiredType) {
            return () -> new ModelMappingException(String.format(
                "Expected field at %s to be of type %s, but got type %s",
                index,requiredType,v.getClass().getSimpleName()));
        }
    }

    public static RecordMapper fromRecord(Record record) {
        return new RecordMapper(record);
    }

    private Record record;

    RecordMapper(Record record) {
        this.record = record;
    }

    public Optional<Identifier> getRecordId() {
        return record.getRecordId();
    }

    public String getPartyField(int index) {
        Value v = fieldAt(index).getValue();
        return v.asParty()
            .orElseThrow(ModelMappingException.badType(v, index,"Party"))
            .getValue();
    }

    public BigDecimal getDecimalField(int index) {
        Value v = fieldAt(index).getValue();
        return v.asDecimal()
            .orElseThrow(ModelMappingException.badType(v, index,"Decimal"))
            .getValue();
    }

    public String getTextField(int index) {
        Value v = fieldAt(index).getValue();
        return v.asText()
            .orElseThrow(ModelMappingException.badType(v, index,"Text"))
            .getValue();
    }

    public long getInt64Field(int index) {
        Value v = fieldAt(index).getValue();
        return v.asInt64()
            .orElseThrow(ModelMappingException.badType(v, index,"Int64"))
            .getValue();
    }

    public boolean getBooleanField(int index) {
        Value v = fieldAt(index).getValue();
        return v.asBool()
            .orElseThrow(ModelMappingException.badType(v, index,"Bool"))
            .isValue();
    }

    public Instant getTimestampField(int index) {
        Value v = fieldAt(index).getValue();
        return v.asTimestamp()
            .orElseThrow(ModelMappingException.badType(v, index,"Timestamp"))
            .getValue();
    }

    public LocalDate getDateField(int index) {
        Value v = fieldAt(index).getValue();
        return LocalDate.ofEpochDay(
            toEpochDay(
                v.asTimestamp().orElseThrow(ModelMappingException.badType(v, index,"Timestamp as Date"))
            )
        );
    }

    public Record getRecordField(int index) {
        Value v = fieldAt(index).getValue();
        return v.asRecord()
            .orElseThrow(ModelMappingException.badType(v, index,"Record"));
    }

    public DamlList getListField(int index) {
        Value v = fieldAt(index).getValue();
        return v.asList()
            .orElseThrow(ModelMappingException.badType(v, index,"DamlList"));
    }

    private Record.Field fieldAt(int index) {
        return record.getFields().get(index);
    }

    /*
     * Convert a timestamp into a long rpresenting a java.time epocjDay count. Timestamp is
     * a long of microseconds since the epoch, so just divide by microseconds per day
     */
    private long toEpochDay(Timestamp timestamp) {
        return timestamp.getValue().toEpochMilli() * 1000 / MICRO_SEC_PER_DAY;
    }
}
