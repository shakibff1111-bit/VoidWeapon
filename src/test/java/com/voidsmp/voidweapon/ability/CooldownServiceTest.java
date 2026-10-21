package com.voidsmp.voidweapon.ability;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class CooldownServiceTest {
    @Test
    void tracksCooldownsIndependentlyByPlayerAndAbility() {
        AtomicLong clock = new AtomicLong(1_000);
        CooldownService service = new CooldownService(clock::get);
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();

        service.start(firstPlayer, "auto_bow:auto_fire", 5_000);

        assertTrue(service.isCoolingDown(firstPlayer, "auto_bow:auto_fire"));
        assertFalse(service.isCoolingDown(firstPlayer, "auto_bow:phantom_volley"));
        assertFalse(service.isCoolingDown(secondPlayer, "auto_bow:auto_fire"));
        assertEquals(5_000, service.remainingMillis(firstPlayer, "auto_bow:auto_fire"));
    }

    @Test
    void cooldownExpiresAndPlayerCleanupRemovesTheirEntries() {
        AtomicLong clock = new AtomicLong(0);
        CooldownService service = new CooldownService(clock::get);
        UUID player = UUID.randomUUID();
        service.start(player, "god_slayer:god_strike", 2_000);

        clock.set(2_001);
        assertFalse(service.isCoolingDown(player, "god_slayer:god_strike"));

        service.start(player, "god_slayer:divine_judgment", 10_000);
        service.clear(player);
        assertTrue(service.snapshot(player).isEmpty());
    }
}