package com.voidsmp.voidweapon.storage;

import com.voidsmp.voidweapon.weapon.WeaponId;

import java.util.UUID;

public record CraftJob(UUID id, UUID owner, WeaponId weapon, UUID worldId, String worldName,
                       int x, int y, int z, long startedAt, long completesAt, String status) {
    public CraftJob withStatus(String newStatus) {
        return new CraftJob(id, owner, weapon, worldId, worldName, x, y, z, startedAt, completesAt, newStatus);
    }
}