package com.example.supplychain.service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

/** Local counters for a single application instance. */
@Service
public class LoginAttemptService {
    private static final int MAX_FAILURES = 5;
    private static final int MAX_IP_FAILURES = 20;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);
    private final ConcurrentMap<String, AttemptState> attempts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AttemptState> ipAttempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) { return isBlocked(attempts, key); }
    public boolean isIpBlocked(String ip) { return isBlocked(ipAttempts, ip); }
    public void recordFailure(String key) { recordFailure(attempts, key, MAX_FAILURES); }
    public void recordIpFailure(String ip) { recordFailure(ipAttempts, ip, MAX_IP_FAILURES); }
    public void recordSuccess(String key) { attempts.remove(key); }

    private boolean isBlocked(ConcurrentMap<String, AttemptState> counters, String key) {
        Instant now = Instant.now();
        AttemptState state = counters.get(key);
        if (state == null) return false;
        synchronized (state) {
            if (state.blockedUntil != null && state.blockedUntil.isAfter(now)) return true;
            if (state.blockedUntil != null || Duration.between(state.windowStarted, now).compareTo(WINDOW) >= 0) {
                counters.remove(key, state);
            }
            return false;
        }
    }

    private void recordFailure(ConcurrentMap<String, AttemptState> counters, String key, int limit) {
        Instant now = Instant.now();
        counters.compute(key, (ignored, previous) -> {
            AttemptState state = previous;
            if (state == null || (state.blockedUntil != null && !state.blockedUntil.isAfter(now))
                    || (state.blockedUntil == null && Duration.between(state.windowStarted, now).compareTo(WINDOW) >= 0)) {
                state = new AttemptState(now);
            }
            synchronized (state) {
                // Requests already blocked must not extend the lockout indefinitely.
                if (state.blockedUntil == null) {
                    state.failures++;
                    if (state.failures >= limit) state.blockedUntil = now.plus(BLOCK_DURATION);
                }
            }
            return state;
        });
    }

    private static final class AttemptState {
        private final Instant windowStarted;
        private int failures;
        private Instant blockedUntil;
        private AttemptState(Instant now) { this.windowStarted = now; }
    }
}
