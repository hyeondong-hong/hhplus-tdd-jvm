package io.hhplus.tdd.common;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

@Component
public final class VirtualTransaction {

    private final Map<String, Lock> locks = new ConcurrentHashMap<>();
//    private final Map<String, Lock> locks = new WeakHashMap<>();
//    private final ReentrantLock globalLock = new ReentrantLock(true);

    public Lock getLock(String scope) {
//        globalLock.lock();
//        try {
//            return locks.computeIfAbsent(scope, k -> new ReentrantLock(true));
//        } finally {
//            globalLock.unlock();
//        }
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
