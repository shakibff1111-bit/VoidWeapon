package com.voidsmp.voidweapon.weapon;

import com.voidsmp.voidweapon.VoidWeaponPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class WeaponService {
    private final NamespacedKey weaponKey;
    private final NamespacedKey rewardKey;

    public WeaponService(VoidWeaponPlugin plugin) {
        weaponKey = new NamespacedKey(plugin, "weapon_id");
        rewardKey = new NamespacedKey(plugin, "craft_reward_id");
    }

    public ItemStack create(WeaponId weapon) {
        Material material = Material.matchMaterial(weapon.material());
        if (material == null) return null;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§5§lVOID WEAPON §8| §d" + weapon.displayName());
        meta.setLore(java.util.List.of("§7VOID SMP legendary weapon", "§8Ability: /voidweapon ability " + weapon.id() + " <ability>"));
        meta.getPersistentDataContainer().set(weaponKey, PersistentDataType.STRING, weapon.id());
        item.setItemMeta(meta);
        return item;
    }

    public WeaponId identify(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(weaponKey, PersistentDataType.STRING);
        return WeaponId.parse(id).orElse(null);
    }

    public void markReward(ItemStack item, String jobId) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(rewardKey, PersistentDataType.STRING, jobId);
        item.setItemMeta(meta);
    }

    public boolean alreadyDelivered(org.bukkit.entity.Player player, String jobId) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            String id = item.getItemMeta().getPersistentDataContainer().get(rewardKey, PersistentDataType.STRING);
            if (jobId.equals(id)) return true;
        }
        return false;
    }
}