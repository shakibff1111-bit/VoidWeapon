package com.voidsmp.voidweapon;

import com.voidsmp.voidweapon.ability.AbilityService;
import com.voidsmp.voidweapon.ability.CooldownService;
import com.voidsmp.voidweapon.command.VoidWeaponCommand;
import com.voidsmp.voidweapon.crafting.CraftingService;
import com.voidsmp.voidweapon.gui.ArrowStorageService;
import com.voidsmp.voidweapon.listener.WeaponListener;
import com.voidsmp.voidweapon.storage.StateStore;
import com.voidsmp.voidweapon.weapon.WeaponService;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class VoidWeaponPlugin extends JavaPlugin {
    private WeaponService weaponService;
    private StateStore stateStore;
    private ArrowStorageService arrowStorageService;
    private CooldownService cooldownService;
    private AbilityService abilityService;
    private CraftingService craftingService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        validateConfig();
        weaponService = new WeaponService(this);
        stateStore = new StateStore(this, getConfig().getString("general.storage-file", "data.yml"));
        arrowStorageService = new ArrowStorageService(this, stateStore);
        cooldownService = new CooldownService();
        abilityService = new AbilityService(this, weaponService, stateStore, cooldownService);
        craftingService = new CraftingService(this, stateStore, weaponService);
        Bukkit.getPluginManager().registerEvents(arrowStorageService, this);
        Bukkit.getPluginManager().registerEvents(craftingService, this);
        Bukkit.getPluginManager().registerEvents(new WeaponListener(this, weaponService, arrowStorageService, abilityService), this);
        VoidWeaponCommand executor = new VoidWeaponCommand(this, weaponService, stateStore, craftingService, abilityService, cooldownService);
        getCommand("voidweapon").setExecutor(executor);
        getCommand("voidweapon").setTabCompleter(executor);
        getCommand("start").setExecutor(executor);
        getCommand("start").setTabCompleter(executor);
        craftingService.start();
        getLogger().info("VOID WEAPON enabled.");
    }

    @Override
    public void onDisable() {
        if (abilityService != null) abilityService.shutdown();
        if (craftingService != null) craftingService.shutdown();
        if (stateStore != null) stateStore.close();
    }

    public void validateConfig() {
        int capacity = getConfig().getInt("general.arrow-storage.capacity", 2304);
        if (capacity < 0) {
            getLogger().warning("Invalid arrow storage capacity; using 2304.");
            getConfig().set("general.arrow-storage.capacity", 2304);
        }
        int duration = getConfig().getInt("crafting.duration-seconds", 1200);
        if (duration < 1 || duration > 604800) {
            getLogger().warning("Craft duration must be between 1 and 604800 seconds; using 1200.");
            getConfig().set("crafting.duration-seconds", 1200);
        }
        String file = getConfig().getString("general.storage-file", "data.yml");
        if (file == null || file.isBlank() || file.contains("..") || file.contains("/") || file.contains("\\")) {
            getLogger().warning("Invalid storage filename; using data.yml.");
            getConfig().set("general.storage-file", "data.yml");
        }
    }

    public WeaponService weaponService() { return weaponService; }
    public StateStore stateStore() { return stateStore; }
    public ArrowStorageService arrowStorageService() { return arrowStorageService; }
    public CooldownService cooldownService() { return cooldownService; }
    public AbilityService abilityService() { return abilityService; }
    public CraftingService craftingService() { return craftingService; }
}