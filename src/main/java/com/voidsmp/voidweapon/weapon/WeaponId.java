package com.voidsmp.voidweapon.weapon;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public enum WeaponId {
    AUTO_BOW("auto_bow", "AUTO BOW", "BOW", List.of("auto_fire", "piercing_shot", "phantom_volley")),
    WIND_BLADE("wind_blade", "WIND BLADE", "DIAMOND_SWORD", List.of("phantom_dash", "cyclone_cut", "sky_render")),
    THUNDER_SPEAR("thunder_spear", "THUNDER SPEAR", "TRIDENT", List.of("thunder_throw", "chain_lightning", "storm_god")),
    VOID_CRUSHER("void_crusher", "VOID CRUSHER", "NETHERITE_AXE", List.of("void_smash", "gravity_break", "collapse")),
    BLADE_OF_DEMON("blade_of_demon", "THE BLADE OF DEMON", "NETHERITE_SWORD", List.of("demon_slash", "crimson_burst", "hellfire_domain")),
    GOD_SLAYER("god_slayer", "GOD SLAYER", "NETHERITE_SWORD", List.of("god_strike", "divine_judgment"));

    private final String id;
    private final String displayName;
    private final String material;
    private final List<String> abilities;

    WeaponId(String id, String displayName, String material, List<String> abilities) {
        this.id = id;
        this.displayName = displayName;
        this.material = material;
        this.abilities = abilities;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public String material() { return material; }
    public List<String> abilities() { return abilities; }

    public static Optional<WeaponId> parse(String value) {
        if (value == null) return Optional.empty();
        return Arrays.stream(values()).filter(weapon -> weapon.id.equalsIgnoreCase(value)
                || weapon.name().equalsIgnoreCase(value)).findFirst();
    }
}