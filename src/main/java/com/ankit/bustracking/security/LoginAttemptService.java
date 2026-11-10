package com.ankit.bustracking.security;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LoginAttemptService {

    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long FAILURE_WINDOW_SECONDS = 300L; // 5-minute rolling failure window
    public static final long LOCK_DURATION_SECONDS = 300L;  // 5-minute lock duration

    private static class AttemptRecord {
        int count;
        Instant windowStartedAt;
        Instant lockedUntil;
    }

    private final Map<String, AttemptRecord> attemptsByKey = new ConcurrentHashMap<>();

    public void recordFailure(String username, String clientIp) {
        recordFailureAt(username, clientIp, Instant.now());
    }

    public void recordFailureAt(String username, String clientIp, Instant now) {
        if (username != null && !username.trim().isEmpty()) {
            incrementKey("USER:" + username.trim().toLowerCase(), now);
        }
        if (clientIp != null && !clientIp.trim().isEmpty()) {
            incrementKey("IP:" + clientIp.trim(), now);
        }
    }

    public void recordSuccess(String username, String clientIp) {
        if (username != null && !username.trim().isEmpty()) {
            attemptsByKey.remove("USER:" + username.trim().toLowerCase());
        }
        if (clientIp != null && !clientIp.trim().isEmpty()) {
            attemptsByKey.remove("IP:" + clientIp.trim());
        }
    }

    public boolean isBlocked(String username, String clientIp) {
        return isBlockedAt(username, clientIp, Instant.now());
    }

    public boolean isBlockedAt(String username, String clientIp, Instant now) {
        if (username != null && !username.trim().isEmpty()
                && isKeyBlocked("USER:" + username.trim().toLowerCase(), now)) {
            return true;
        }
        if (clientIp != null && !clientIp.trim().isEmpty()
                && isKeyBlocked("IP:" + clientIp.trim(), now)) {
            return true;
        }
        return false;
    }

    private void incrementKey(String key, Instant now) {
        attemptsByKey.compute(key, (k, existing) -> {
            AttemptRecord record = existing;
            if (record == null
                    || (record.lockedUntil != null && now.isAfter(record.lockedUntil))
                    || (record.lockedUntil == null && record.windowStartedAt != null
                        && now.isAfter(record.windowStartedAt.plusSeconds(FAILURE_WINDOW_SECONDS)))) {
                record = new AttemptRecord();
                record.windowStartedAt = now;
            }
            if (record.windowStartedAt == null) {
                record.windowStartedAt = now;
            }
            record.count += 1;
            if (record.count >= MAX_FAILED_ATTEMPTS) {
                record.lockedUntil = now.plusSeconds(LOCK_DURATION_SECONDS);
            }
            return record;
        });
    }

    private boolean isKeyBlocked(String key, Instant now) {
        AttemptRecord record = attemptsByKey.get(key);
        if (record == null) {
            return false;
        }
        if (record.lockedUntil != null) {
            if (now.isAfter(record.lockedUntil)) {
                attemptsByKey.remove(key);
                return false;
            }
            return true;
        }
        if (record.windowStartedAt != null
                && now.isAfter(record.windowStartedAt.plusSeconds(FAILURE_WINDOW_SECONDS))) {
            attemptsByKey.remove(key);
        }
        return false;
    }
}
