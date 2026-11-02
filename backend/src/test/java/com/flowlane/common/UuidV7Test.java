package com.flowlane.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowlane.common.persistence.UuidV7;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void hasVersion7AndRfcVariant() {
        UUID id = UuidV7.next();
        assertEquals(7, id.version());
        assertEquals(2, id.variant());
    }

    @Test
    void laterIdsSortAfterEarlierOnes() throws InterruptedException {
        UUID a = UuidV7.next();
        Thread.sleep(2);
        UUID b = UuidV7.next();
        assertTrue(Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits()) < 0);
    }
}
