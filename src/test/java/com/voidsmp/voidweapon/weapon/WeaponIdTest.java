package com.voidsmp.voidweapon.weapon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WeaponIdTest {
    @Test
    void parsesStableIdsAndEnumNamesCaseInsensitively() {
        assertEquals(WeaponId.AUTO_BOW, WeaponId.parse("auto_bow").orElseThrow());
        assertEquals(WeaponId.GOD_SLAYER, WeaponId.parse("god_slayer").orElseThrow());
        assertEquals(WeaponId.WIND_BLADE, WeaponId.parse("wind_blade").orElseThrow());
    }

    @Test
    void rejectsUnknownOrNullIds() {
        assertTrue(WeaponId.parse("not_a_weapon").isEmpty());
        assertTrue(WeaponId.parse(null).isEmpty());
    }

    @Test
    void allWeaponAndAbilityIdsAreUnique() {
        assertEquals(6, WeaponId.values().length);
        assertEquals(6, java.util.Arrays.stream(WeaponId.values()).map(WeaponId::id).distinct().count());
        assertEquals(17, java.util.Arrays.stream(WeaponId.values()).flatMap(weapon -> weapon.abilities().stream()).distinct().count());
    }
}