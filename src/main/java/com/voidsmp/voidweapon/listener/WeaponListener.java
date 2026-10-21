package com.voidsmp.voidweapon.listener;

import com.voidsmp.voidweapon.VoidWeaponPlugin;
import com.voidsmp.voidweapon.ability.AbilityService;
import com.voidsmp.voidweapon.gui.ArrowStorageService;
import com.voidsmp.voidweapon.weapon.WeaponId;
import com.voidsmp.voidweapon.weapon.WeaponService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class WeaponListener implements Listener {
    private final VoidWeaponPlugin plugin;
    private final WeaponService weapons;
    private final ArrowStorageService arrowStorage;
    private final AbilityService abilities;

    public WeaponListener(VoidWeaponPlugin plugin, WeaponService weapons, ArrowStorageService arrowStorage, AbilityService abilities) {
        this.plugin = plugin;
        this.weapons = weapons;
        this.arrowStorage = arrowStorage;
        this.abilities = abilities;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        WeaponId weapon = weapons.identify(item);
        if (weapon == null) return;
        Action action = event.getAction();
        if (weapon == WeaponId.AUTO_BOW && player.isSneaking()
                && plugin.getConfig().getBoolean("general.controls.sneak-left-click-arrow-storage", true)
                && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
            event.setCancelled(true);
            if (player.hasPermission("voidweapon.use")) arrowStorage.open(player);
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            event.setCancelled(true);
            if (!player.hasPermission("voidweapon.use")) return;
            if (weapon == WeaponId.AUTO_BOW) {
                if (plugin.getConfig().getBoolean("general.controls.auto-fire-right-click", true)) abilities.activate(player, weapon, "auto_fire");
            } else if (plugin.getConfig().getBoolean("general.controls.selected-ability-right-click", true)) {
                abilities.activateSelected(player, weapon);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        abilities.onQuit(event);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        abilities.onDeath(event);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        abilities.onWorldChange(event);
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        abilities.onProjectileHit(event);
    }
}