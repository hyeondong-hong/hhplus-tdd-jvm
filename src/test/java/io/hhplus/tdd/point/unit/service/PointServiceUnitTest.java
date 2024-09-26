package io.hhplus.tdd.point.unit.service;

import io.hhplus.tdd.common.VirtualTransaction;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.model.enumeration.TableType;
import io.hhplus.tdd.point.model.record.UserPoint;
import io.hhplus.tdd.point.service.PointServiceImpl;
import io.hhplus.tdd.point.validator.PointValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static io.hhplus.tdd.point.constants.PointConstants.CHARGE_LIMIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.AdditionalMatchers.gt;
import static org.mockito.AdditionalMatchers.leq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PointServiceUnitTest {

    @Mock
    private UserPointTable userPointTable;

    @Mock
    private PointHistoryTable pointHistoryTable;

    @Mock
    private VirtualTransaction virtualTransaction;

    @Mock
    private PointValidator pointValidator;

    @InjectMocks
    private PointServiceImpl pointService;

    private Long targetUserId;

    @BeforeEach
    public void init() {
        targetUserId = new Random().nextLong();

        // 사용하고자 하는 scope에만 Lock 인스턴스 Stubbing
        Lock userPointLock = new ReentrantLock(true);
        lenient().when(virtualTransaction.getLock(eq(TableType.USER_POINT.makeScope(targetUserId)))).thenReturn(userPointLock);
        Lock pointHistoryLock = new ReentrantLock(true);
        lenient().when(virtualTransaction.getLock(eq(TableType.POINT_HISTORY.makeScope()))).thenReturn(pointHistoryLock);

        // 가상 트랜잭션 처리 후 Supplier의 실제 로직처리 및 반환값을 가져오기 위해 CallRealMethod 반환으로 설정
        lenient().when(virtualTransaction.perform(any(String.class), any())).thenCallRealMethod();

        // insertOrUpdate의 반환값은 항상 입력된 매개변수로 만들어진 UserPoint 레코드 인스턴스
        lenient().when(userPointTable.insertOrUpdate(eq(targetUserId), any(Long.class))).thenAnswer(
                invocation -> new UserPoint(invocation.getArgument(0), invocation.getArgument(1), System.currentTimeMillis()));
    }

    @Test
    @DisplayName("포인트 충전 실패")
    public void failure_chargePoint() {
        // given
        UserPoint userPoint = new UserPoint(targetUserId, CHARGE_LIMIT - 5000L, System.currentTimeMillis());
        when(userPointTable.selectById(targetUserId)).thenReturn(userPoint);

        // when
        lenient().doThrow(new IllegalArgumentException("입력 포인트 값이 0 이하"))
                .when(pointValidator).validateCharge(eq(userPoint), leq(0L));
        lenient().doThrow(new IllegalArgumentException("최대 충전 포인트 초과"))
                .when(pointValidator).validateCharge(eq(userPoint), gt(CHARGE_LIMIT - userPoint.point()));

        // then
        Exception enterNegativeValue = assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(targetUserId, -100L));
        assertEquals("입력 포인트 값이 0 이하", enterNegativeValue.getMessage());
        Exception maximumInputExceed = assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(targetUserId, 5000L + 1L));
        assertEquals("최대 충전 포인트 초과", maximumInputExceed.getMessage());
    }

    @Test
    @DisplayName("포인트 사용 실패")
    public void failure_usePoint() {
        // given
        UserPoint userPoint = new UserPoint(targetUserId, 3000L, System.currentTimeMillis());
        when(userPointTable.selectById(targetUserId)).thenReturn(userPoint);

        // when
        lenient().doThrow(new IllegalArgumentException("입력 포인트 값이 0 이하"))
                .when(pointValidator).validateUse(eq(userPoint), eq(-100L));
        lenient().doThrow(new IllegalArgumentException("포인트 부족"))
                .when(pointValidator).validateUse(eq(userPoint), gt(userPoint.point()));

        // then
        Exception enterNegativeValue = assertThrows(IllegalArgumentException.class, () -> pointService.usePoint(targetUserId, -100L));
        assertEquals("입력 포인트 값이 0 이하", enterNegativeValue.getMessage());
        Exception exceedRemaining = assertThrows(IllegalArgumentException.class, () -> pointService.usePoint(targetUserId, userPoint.point() + 1L));
        assertEquals("포인트 부족", exceedRemaining.getMessage());
    }
}
