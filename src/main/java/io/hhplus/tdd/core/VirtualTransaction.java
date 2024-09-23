package io.hhplus.tdd.core;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public final class VirtualTransaction {

    private final Map<String, Lock> locks = new WeakHashMap<>();

    private Lock getLock(String scope) {
        return locks.computeIfAbsent(scope, k -> new ReentrantLock(true));
    }

    public <T> T perform(String scope, Supplier<T> func) {
        Lock lock = getLock(scope);
        lock.lock();
        try {
            return func.get();
        } finally {
            lock.unlock();
        }
    }
}
