package com.gearblock.bounty.service;

import com.gearblock.bounty.storage.Bounty;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The money math of a claim, tested without a server. */
class SplitTest {

    private static final UUID TARGET = UUID.randomUUID();
    private static final UUID KILLER = UUID.randomUUID();
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private static Bounty b(UUID placer, double amount) {
        return new Bounty(1, TARGET, "Target", placer, "p", amount, 0);
    }

    @Test
    void othersArePaidMinusTax() {
        BountyService.Claim c = BountyService.split(List.of(b(ALICE, 1000), b(BOB, 500)), KILLER, 5);
        assertEquals(1425.0, c.reward());   // 1500 - 5%
        assertEquals(0.0, c.refunded());
    }

    @Test
    void killerOnlyGetsOwnMoneyBackUntaxed() {
        BountyService.Claim c = BountyService.split(List.of(b(KILLER, 1000)), KILLER, 5);
        assertEquals(0.0, c.reward());      // no profit from killing your own target
        assertEquals(1000.0, c.refunded());
    }

    @Test
    void mixedContributions() {
        BountyService.Claim c = BountyService.split(List.of(b(KILLER, 200), b(ALICE, 800)), KILLER, 10);
        assertEquals(720.0, c.reward());    // only Alice's 800, minus 10%
        assertEquals(200.0, c.refunded());
    }

    @Test
    void serverBountiesCountAsOthers() {
        BountyService.Claim c = BountyService.split(List.of(b(Bounty.SERVER, 1000)), KILLER, 5);
        assertEquals(950.0, c.reward());
        assertEquals(0.0, c.refunded());
    }

    @Test
    void zeroTaxAndRounding() {
        assertEquals(333.33, BountyService.split(List.of(b(ALICE, 333.333)), KILLER, 0).reward());
        assertEquals(0.95, BountyService.split(List.of(b(ALICE, 1)), KILLER, 5).reward());
    }
}
