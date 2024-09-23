package io.hhplus.tdd.point;

import static io.hhplus.tdd.point.PointConstants.CHARGE_LIMIT;
import static org.junit.jupiter.api.Assertions.*;

import io.hhplus.tdd.core.VirtualTransaction;
import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Random;

@ExtendWith(MockitoExtension.class)
public class PointServiceTest {

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

    @Test
    public void 포인트_충전_실패() {
        Long userId = new Random().nextLong();
        Exception e1 = assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(userId, -100));
        assertEquals("입력 포인트 값이 0 이하", e1.getMessage());

        Exception e2 = assertThrows(IllegalArgumentException.class, () -> pointService.chargePoint(userId, CHARGE_LIMIT + 1));
        assertEquals("최대 충전 포인트 초과", e2.getMessage());
    }

    @Test
    public void 포인트_사용_실패() {
        long userId = new Random().nextLong();
        Exception e1 = assertThrows(IllegalArgumentException.class, () -> pointService.usePoint(userId, -100));
        assertEquals("입력 포인트 값이 0 이하", e1.getMessage());

        Exception e2 = assertThrows(IllegalArgumentException.class, () -> pointService.usePoint(userId, CHARGE_LIMIT + 1));
        assertEquals("포인트 부족", e2.getMessage());
    }
}
