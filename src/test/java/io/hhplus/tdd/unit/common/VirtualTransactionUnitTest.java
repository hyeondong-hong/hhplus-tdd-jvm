package io.hhplus.tdd.unit.common;

import io.hhplus.tdd.common.VirtualTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class VirtualTransactionUnitTest {

    private VirtualTransaction virtualTransaction;

    @BeforeEach
    public void setUp() {
        virtualTransaction = new VirtualTransaction();
    }

    @Test
    @DisplayName("가상 트랜잭션 락 경합 테스트")
    public void transaction_lockCompetition() throws InterruptedException {
        final String scope = Long.toString(new Random().nextLong());
        final Map<String, String> map = new ConcurrentHashMap<>();

        CompletableFuture.runAsync(() -> virtualTransaction.perform(scope, () -> {
            map.put("target", "target");
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                map.remove("target");
            }
            return null;
        }));
        Thread.sleep(500L);
        String target = virtualTransaction.perform(scope, () -> map.get("target"));

        assertNull(target);
    }

    @Test
    @DisplayName("가상 트랜잭션 Scope 분리 테스트")
    public void transaction_separatedScope() throws InterruptedException {
        // 서로 다른 두 Scope를 보장하기 위해 비교 후 각 Scope 확정
        final String scope1 = Long.toString(new Random().nextLong());
        String scope2Tmp;
        do {
            scope2Tmp = Long.toString(new Random().nextLong());
        } while (scope1.equals(scope2Tmp));
        final String scope2 = scope2Tmp;

        final Map<String, String> map = new ConcurrentHashMap<>();

        CompletableFuture.runAsync(() -> virtualTransaction.perform(scope1, () -> {
            map.put("target", "target");
            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                map.remove("target");
            }
            return null;
        }));
        Thread.sleep(500L);
        String target = virtualTransaction.perform(scope2, () -> map.get("target"));

        assertEquals(target, "target");
    }
}
