package io.hhplus.tdd.point;

import java.util.List;

public interface PointService {
    UserPoint chargePoint(Long userId, long amount);
    UserPoint usePoint(Long userId, long amount) throws IllegalArgumentException;
    UserPoint modifyPoint(Long userId, long newAmount, long amount, TransactionType transactionType);
    UserPoint getUserPoint(Long userId);
    List<PointHistory> getPointHistoryAll(Long userId);
    PointHistory insertHistory(Long userId, long amount, TransactionType transactionType, long millis);
}
