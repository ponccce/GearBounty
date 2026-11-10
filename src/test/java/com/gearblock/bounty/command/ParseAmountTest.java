package com.gearblock.bounty.command;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParseAmountTest {

    @Test
    void acceptsPlainNumbersAndShortcuts() {
        assertEquals(100.0, BountyCommand.parseAmount("100"));
        assertEquals(1500.5, BountyCommand.parseAmount("1,500.50"));
        assertEquals(10_000.0, BountyCommand.parseAmount("10k"));
        assertEquals(2_000_000.0, BountyCommand.parseAmount("2M"));
        assertEquals(0.13, BountyCommand.parseAmount("0.129"));
    }

    @Test
    void rejectsCheatsAndGarbage() {
        for (String bad : new String[]{"-100", "0", "NaN", "Infinity", "1e999", "abc", "", "k"}) {
            assertTrue(Double.isNaN(BountyCommand.parseAmount(bad)), "should reject: " + bad);
        }
    }
}
