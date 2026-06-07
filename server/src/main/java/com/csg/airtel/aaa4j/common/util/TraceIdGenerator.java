package com.csg.airtel.aaa4j.common.util;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility for generating request trace identifiers.
 * Uses epoch millis + random hex for high-throughput, allocation-minimal trace IDs.
 */
public final class TraceIdGenerator {

	private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

	private TraceIdGenerator() {
		// Utility class; do not instantiate
	}

	/**
	 * Generates a new trace id using epoch millis and random hex.
	 * Avoids ZonedDateTime.now().format() which is expensive at high TPS.
	 */
    @SuppressWarnings("java:S2245")
    public static String generateTraceId() {
        long random = ThreadLocalRandom.current().nextLong();
        long timeMillis = System.currentTimeMillis();
        char[] buf = new char[26];

        // 8 hex chars from random
        for (int i = 0; i < 8; i++) {
            buf[i] = HEX_CHARS[(int) (random & 0x0F)];
            random >>>= 4;
        }
        buf[8] = '-';

        // 13 chars from epoch millis (avoids DateTimeFormatter overhead)
        long t = timeMillis;
        for (int i = 21; i >= 9; i--) {
            buf[i] = (char) ('0' + (t % 10));
            t /= 10;
        }

        // 4 more hex chars from remaining random bits
        for (int i = 22; i < 26; i++) {
            buf[i] = HEX_CHARS[(int) (random & 0x0F)];
            random >>>= 4;
        }

        return new String(buf);
    }

}


