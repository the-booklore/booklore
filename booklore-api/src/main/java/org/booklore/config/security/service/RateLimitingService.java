package org.booklore.config.security.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class RateLimitingService {

    private final Cache<String, AtomicInteger> requestCounts;

    public RateLimitingService() {
        this.requestCounts = Caffeine.newBuilder()
                .expireAfterWrite(1, TimeUnit.MINUTES)
                .build();
    }

    public boolean tryAcquire(String key, int limit) {
        AtomicInteger count = requestCounts.get(key, k -> new AtomicInteger(0));
        return count.incrementAndGet() <= limit;
    }
}
