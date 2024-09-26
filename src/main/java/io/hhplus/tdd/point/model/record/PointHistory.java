package io.hhplus.tdd.point.model.record;

import io.hhplus.tdd.point.model.enumeration.TransactionType;

public record PointHistory(
        long id,
        long userId,
        long amount,
        TransactionType type,
        long updateMillis
) {
}
