package com.voidsmp.voidweapon.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class CooldownService {
    private final Map<UUID, Map<String, Long>> expirations = new HashMap<>();
    private final LongSupplier clock;

    public CooldownService() {
        this(System::currentTimeMillis);
    }

    public CooldownService(LongSupplier clock) {
        this.clock = clock;
    }

    public long remainingMillis(UUID player, String ability) {
        Map<String, Long> playerCooldowns = expirations.get(player);
        if (playerCooldowns == null) return 0L;
        long remaining = playerCooldowns.getOrDefault(ability, 0L) - clock.getAsLong();
        if (remaining <= 0) {
            playerCooldowns.remove(ability);
            if (playerCooldowns.isEmpty()) expirations.remove(player);
            return 0L;
        }
        return remaining;
    }

    public boolean isCoolingDown(UUID player, String ability) {
        return remainingMillis(player, ability) > 0;
    }

    public void start(UUID player, String ability, long durationMillis) {
        long now = clock.getAsLong();
        expirations.values().forEach(cooldowns -> cooldowns.values().removeIf(expiry -> expiry <= now));
        expirations.values().removeIf(Map::isEmpty);
        expirations.computeIfAbsent(player, ignored -> new HashMap<>())
                .put(ability, now + Math.max(0L, durationMillis));
    }

    public void clear(UUID player) {
        expirations.remove(player);
    }

    public Map<String, Long> snapshot(UUID player) {
        long now = clock.getAsLong();
        Map<String, Long> result = new HashMap<>();
        expirations.getOrDefault(player, Map.of()).forEach((ability, expiry) -> {
            if (expiry > now) result.put(ability, expiry - now);
        });
        return result;
    }
}