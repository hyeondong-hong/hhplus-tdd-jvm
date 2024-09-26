package io.hhplus.tdd.integration.point.service;

import io.hhplus.tdd.point.model.record.UserPoint;
import io.hhplus.tdd.point.service.PointService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class PointServiceIntegrationTest {

    @Autowired
    private PointService pointService;

    private Long targetUserId;

    @BeforeEach
    public void init() {
        targetUserId = new Random().nextLong();
    }

    @Test
    @DisplayName("동시 접근 안정성 테스트")
    public void success_validateConcurrentConnection() throws InterruptedException {
        UserPoint chargedPoint = pointService.chargePoint(targetUserId, 10000L);
        assertEquals(chargedPoint.point(), 10000L);

        long[] plan = {700L, -300L, 950L, 1200L, -650L, -200L, 600L, -1100L};

        CountDownLatch latch = new CountDownLatch(plan.length);
        CountDownLatch standbyLatch = new CountDownLatch(1);

        for (long amount : plan) {
            Runnable runnable = (amount > 0)
                    ? () -> pointService.chargePoint(targetUserId, amount)
                    : () -> pointService.usePoint(targetUserId, -amount);
            CompletableFuture.runAsync(() -> {
                try {
                    standbyLatch.await();
                    runnable.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        standbyLatch.countDown();
        latch.await();

        assertEquals(pointService.getUserPoint(targetUserId).point(), Arrays.stream(plan).sum() + 10000L);
        pointService.getPointHistoryAll(targetUserId).forEach(System.out::println);

    }

}
