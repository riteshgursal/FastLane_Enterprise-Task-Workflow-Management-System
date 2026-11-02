package com.flowlane.common.persistence;

import java.security.SecureRandom;
import java.util.UUID;

/** Time-ordered UUID (RFC 9562 version 7) so primary keys insert in index order. */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    public static UUID next() {
        long millis = System.currentTimeMillis();
        long randA = RANDOM.nextInt(1 << 12);
        long msb = (millis << 16) | (0x7L << 12) | randA;
        long lsb = (RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
