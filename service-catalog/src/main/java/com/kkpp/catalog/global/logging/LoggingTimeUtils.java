package com.kkpp.catalog.global.logging;

public final class LoggingTimeUtils {

    private LoggingTimeUtils() {
    }

    public static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000;
    }
}
