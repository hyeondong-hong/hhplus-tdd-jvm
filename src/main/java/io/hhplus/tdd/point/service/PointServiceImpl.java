package io.hhplus.tdd.point.service;

import io.hhplus.tdd.common.VirtualTransaction;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.model.enumeration.TableType;
import io.hhplus.tdd.point.model.enumeration.TransactionType;
import io.hhplus.tdd.point.model.record.PointHistory;
import io.hhplus.tdd.point.model.record.UserPoint;
import io.hhplus.tdd.point.validator.PointValidator;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PointServiceImpl implements PointService {

    private final VirtualTransaction virtualTransaction;
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;
    private final PointValidator pointValidator;

    public PointServiceImpl(
            VirtualTransaction virtualTransaction,
            UserPointTable userPointTable,
            PointHistoryTable pointHistoryTable,
            PointValidator pointValidator) {

        this.virtualTransaction = virtualTransaction;
        this.userPointTable = userPointTable;
        this.pointHistoryTable = pointHistoryTable;
        this.pointValidator = pointValidator;
    }

    @Override
    public UserPoint chargePoint(final Long userId, final long amount) {
        return virtualTransaction.perform(TableType.USER_POINT.makeScope(userId), () -> {
            UserPoint userPoint = userPointTable.selectById(userId);
            pointValidator.validateCharge(userPoint, amount);
            long newAmount = userPoint.point() + amount;

            UserPoint result = userPointTable.insertOrUpdate(userId, newAmount);
            insertHistory(userId, amount, TransactionType.CHARGE, result.updateMillis());
            return result;
        });
    }

    @Override
    public UserPoint usePoint(final Long userId, final long amount) {
        return virtualTransaction.perform(TableType.USER_POINT.makeScope(userId), () -> {
            UserPoint userPoint = userPointTable.selectById(userId);
            pointValidator.validateUse(userPoint, amount);
            long newAmount = userPoint.point() - amount;

            UserPoint result = userPointTable.insertOrUpdate(userId, newAmount);
            insertHistory(userId, amount, TransactionType.USE, result.updateMillis());
            return result;
        });
    }

    private void insertHistory(final Long userId, final long amount, final TransactionType transactionType, final long millis) {
        virtualTransaction.perform(TableType.POINT_HISTORY.makeScope(),
                () -> pointHistoryTable.insert(userId, amount, transactionType, millis));
    }

    @Override
    public UserPoint getUserPoint(final Long userId) {
        return virtualTransaction.perform(TableType.USER_POINT.makeScope(userId),
                () -> userPointTable.selectById(userId));
    }

    @Override
    public List<PointHistory> getPointHistoryAll(final Long userId) {
        return virtualTransaction.perform(TableType.POINT_HISTORY.makeScope(),
                () -> pointHistoryTable.selectAllByUserId(userId));
    }

}
