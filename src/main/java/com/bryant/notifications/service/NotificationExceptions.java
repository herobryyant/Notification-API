package com.bryant.notifications.service;

public final class NotificationExceptions {
    private NotificationExceptions() { }

    public static class NotFound extends RuntimeException {
        public NotFound(String message) { super(message); }
    }

    public static class IdempotencyConflict extends RuntimeException {
        public IdempotencyConflict(String message) { super(message); }
    }

    public static class RateLimitExceeded extends RuntimeException {
        private final long retryAfterSeconds;
        public RateLimitExceeded(long retryAfterSeconds) {
            super("Notification rate limit exceeded for this recipient");
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    public static class QueueUnavailable extends RuntimeException {
        public QueueUnavailable(String message, Throwable cause) { super(message, cause); }
    }
}
