package io.hhplus.tdd.point.unit.validator;

import io.hhplus.tdd.point.model.record.UserPoint;
import io.hhplus.tdd.point.validator.PointValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static io.hhplus.tdd.point.constants.PointConstants.CHARGE_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class PointValidatorUnitTest {

    private PointValidator pointValidator;

    private Long targetUserId;

    @BeforeEach
    public void setUp() {
        pointValidator = new PointValidator();
        targetUserId = new Random().nextLong();
    }

    @Test
    @DisplayName("포인트 충전 유효성 검사")
    public void validate_chargePoint() {
        long currentTime = System.currentTimeMillis();
        UserPoint userPoint = new UserPoint(targetUserId, CHARGE_LIMIT, currentTime);
        Exception enterNegativeValue = assertThrows(IllegalArgumentException.class, () -> pointValidator.validateCharge(userPoint, -100));
        Exception maximumInputExceed = assertThrows(IllegalArgumentException.class, () -> pointValidator.validateCharge(userPoint, 100));
        assertEquals("입력 포인트 값이 0 이하", enterNegativeValue.getMessage());
        assertEquals("최대 충전 포인트 초과", maximumInputExceed.getMessage());
    }

    @Test
    @DisplayName("포인트 사용 유효성 검사")
    public void validate_usePoint() {
        long currentTime = System.currentTimeMillis();
        UserPoint userPoint = new UserPoint(targetUserId, 0, currentTime);
        Exception enterNegativeValue = assertThrows(IllegalArgumentException.class, () -> pointValidator.validateUse(userPoint, -100));
        Exception maximumInputExceed = assertThrows(IllegalArgumentException.class, () -> pointValidator.validateUse(userPoint, 100));
        assertEquals("입력 포인트 값이 0 이하", enterNegativeValue.getMessage());
        assertEquals("포인트 부족", maximumInputExceed.getMessage());
    }
}
