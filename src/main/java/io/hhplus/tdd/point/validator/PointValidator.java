package io.hhplus.tdd.point.validator;

import io.hhplus.tdd.point.model.record.UserPoint;
import org.springframework.stereotype.Component;

import static io.hhplus.tdd.point.constants.PointConstants.CHARGE_LIMIT;

@Component
public class PointValidator {

    public void validateCharge(UserPoint userPoint, long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("입력 포인트 값이 0 이하");
        }

        long newAmount = userPoint.point() + amount;
        if (newAmount > CHARGE_LIMIT) {
            throw new IllegalArgumentException("최대 충전 포인트 초과");
        }
    }

    public void validateUse(UserPoint userPoint, long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("입력 포인트 값이 0 이하");
        }

        long newAmount = userPoint.point() - amount;
        if (newAmount < 0L) {
            throw new IllegalArgumentException("포인트 부족");
        }
    }
}
