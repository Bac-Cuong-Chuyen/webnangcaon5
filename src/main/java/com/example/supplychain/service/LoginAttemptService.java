package com.example.supplychain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Service;

/** In-process brute-force guard for the coursework deployment. */
@Service
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Instant now = Instant.now();
        AttemptState state = attempts.get(key);
        if (state == null) return false;
        synchronized (state) {
            if (state.blockedUntil != null && state.blockedUntil.isAfter(now)) return true;
            if (state.blockedUntil != null || Duration.between(state.windowStarted, now).compareTo(WINDOW) >= 0) {
                attempts.remove(key, state);
            }
            return false;
        }
    }

    public void recordFailure(String key) {
        Instant now = Instant.now();
        AttemptState state = attempts.computeIfAbsent(key, ignored -> new AttemptState(now));
        synchronized (state) {
            if (Duration.between(state.windowStarted, now).compareTo(WINDOW) >= 0) {
                state.windowStarted = now;
                state.failures = 0;
                state.blockedUntil = null;
            }
            state.failures++;
            if (state.failures >= MAX_FAILURES) state.blockedUntil = now.plus(BLOCK_DURATION);
        }
    }

    public void recordSuccess(String key) {
        attempts.remove(key);
    }

    private static final class AttemptState {
        private Instant windowStarted;
        private int failures;
        private Instant blockedUntil;
        private AttemptState(Instant now) { this.windowStarted = now; }
    }
}
