package io.hhplus.tdd.point.service;

import io.hhplus.tdd.point.model.record.PointHistory;
import io.hhplus.tdd.point.model.enumeration.TransactionType;
import io.hhplus.tdd.point.model.record.UserPoint;

import java.util.List;

public interface PointService {
    UserPoint chargePoint(final Long userId, final long amount);
    UserPoint usePoint(final Long userId, final long amount) throws IllegalArgumentException;
    UserPoint getUserPoint(final Long userId);
    List<PointHistory> getPointHistoryAll(final Long userId);
}
