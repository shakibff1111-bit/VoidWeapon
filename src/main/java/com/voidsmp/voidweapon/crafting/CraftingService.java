package com.voidsmp.voidweapon.crafting;

import com.voidsmp.voidweapon.VoidWeaponPlugin;
import com.voidsmp.voidweapon.storage.CraftJob;
import com.voidsmp.voidweapon.storage.StateStore;
import com.voidsmp.voidweapon.weapon.WeaponId;
import com.voidsmp.voidweapon.weapon.WeaponService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CraftingService implements Listener {
    private static final int[] SLOTS = {10, 12, 14, 16, 20, 24};
    private final VoidWeaponPlugin plugin;
    private final StateStore store;
    private final WeaponService weapons;
    private final NamespacedKey guiKey;
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Long> rewardAttempts = new HashMap<>();
    private BukkitTask ticker;

    public CraftingService(VoidWeaponPlugin plugin, StateStore store, WeaponService weapons) {
        this.plugin = plugin;
        this.store = store;
        this.weapons = weapons;
        this.guiKey = new NamespacedKey(plugin, "forge_gui");
    }

    public void start() {
        recoverJobs();
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        bossBars.values().forEach(BossBar::removeAll);
        bossBars.clear();
    }

    public void openMenu(Player player, Block block) {
        if (!player.hasPermission("voidweapon.craft") && !player.hasPermission("voidweapon.admin")) {
            message(player, plugin.getConfig().getString("general.messages.no-permission", "§cYou do not have permission to do that."));
            return;
        }
        if (!isForge(block)) {
            message(player, plugin.getConfig().getString("general.messages.not-at-forge", "§cLook at a configured forge block."));
            return;
        }
        String forgeKey = forgeKey(block.getLocation());
        String owner = store.forgeOwner(forgeKey);
        if (owner == null) store.setForgeOwner(forgeKey, player.getUniqueId());
        else if (!canUseForge(player, forgeKey, false)) {
            message(player, "§cYou are not trusted at this forge.");
            return;
        }
        ForgeHolder holder = new ForgeHolder(player.getUniqueId(), UUID.randomUUID(), forgeKey,
                block.getWorld().getUID(), block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
        Inventory inventory = Bukkit.createInventory(holder, 27, "§5§lVOID WEAPON §8| §dForge");
        holder.inventory = inventory;
        inventory.setItem(4, statusButton(holder, player));
        for (int i = 0; i < WeaponId.values().length; i++) inventory.setItem(SLOTS[i], weaponButton(holder, player, WeaponId.values()[i]));
        player.openInventory(inventory);
    }

    public void trust(Player actor, Player target, boolean trust) {
        if (!actor.hasPermission("voidweapon.trust") && !actor.hasPermission("voidweapon.admin")) {
            message(actor, plugin.getConfig().getString("general.messages.no-permission", "§cYou do not have permission to do that."));
            return;
        }
        Block block = actor.getTargetBlockExact(6);
        if (block == null || !isForge(block)) {
            message(actor, plugin.getConfig().getString("general.messages.not-at-forge", "§cLook at a configured forge block."));
            return;
        }
        String key = forgeKey(block.getLocation());
        if (!isOwnerOrAdmin(actor, key)) {
            message(actor, "§cOnly this forge's owner or an administrator can manage trust.");
            return;
        }
        store.setTrusted(key, target.getUniqueId(), trust);
        message(actor, plugin.getConfig().getString("general.messages.trust-updated", "§aForge trust updated."));
    }

    public boolean canUseForge(Player player, String key, boolean collecting) {
        if (player.hasPermission("voidweapon.admin") || isOwnerOrAdmin(player, key)) return true;
        if (!plugin.getConfig().getBoolean("crafting.forge.trust-enabled", true) || !store.isTrusted(key, player.getUniqueId())) return false;
        return plugin.getConfig().getBoolean(collecting ? "crafting.forge.trusted-can-collect" : "crafting.forge.trusted-can-craft", true);
    }

    public boolean cancel(UUID owner, Player actor) {
        CraftJob job = store.jobForOwner(owner);
        if (job == null || !job.status().equals("ACTIVE")) return false;
        if (actor != null && !actor.hasPermission("voidweapon.admin")) {
            String forgeKey = job.worldId() + "_" + job.x() + "_" + job.y() + "_" + job.z();
            if (!actor.getUniqueId().equals(owner) || !canUseForge(actor, forgeKey, false)) return false;
        }
        store.putJob(job.withStatus("CANCELLED"));
        hideBar(owner);
        return true;
    }

    public List<CraftJob> jobs() {
        return store.jobs();
    }

    public void deliverPending(Player player) {
        for (CraftJob job : store.jobs()) {
            if (!job.owner().equals(player.getUniqueId()) || !job.status().equals("READY")) continue;
            String key = job.worldId() + "_" + job.x() + "_" + job.y() + "_" + job.z();
            if (!canUseForge(player, key, true)) continue;
            deliver(player, job);
        }
    }

    public String forgeKey(Location location) {
        return location.getWorld().getUID() + "_" + location.getBlockX() + "_" + location.getBlockY() + "_" + location.getBlockZ();
    }

    private void onWeaponClick(Player player, ForgeHolder holder, int slot) {
        int index = -1;
        for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == slot) index = i;
        if (index < 0) return;
        WeaponId weapon = WeaponId.values()[index];
        if (!canUseForge(player, holder.forgeKey, false)) {
            message(player, "§cYou are no longer trusted at this forge.");
            return;
        }
        if (!player.hasPermission("voidweapon.craft") && !player.hasPermission("voidweapon.admin")) {
            message(player, "§cYou do not have permission to craft.");
            return;
        }
        CraftJob existing = store.jobForOwner(player.getUniqueId());
        if (existing != null) {
            message(player, plugin.getConfig().getString("general.messages.job-active", "§cYou already have an active job or reward."));
            return;
        }
        if (!player.hasPermission("voidweapon.admin") && !store.recipeUnlocked(player.getUniqueId(), weapon)) {
            message(player, plugin.getConfig().getString("general.messages.recipe-locked", "§cThat weapon recipe is locked."));
            return;
        }
        Map<Material, Integer> ingredients = ingredients(weapon);
        if (ingredients.isEmpty() || !hasIngredients(player, ingredients)) {
            message(player, "§cYou do not have the required ingredients for " + weapon.displayName() + ".");
            return;
        }
        long now = System.currentTimeMillis();
        long duration = Math.max(1, plugin.getConfig().getLong("crafting.duration-seconds", 1200)) * 1000L;
        CraftJob job = new CraftJob(UUID.randomUUID(), player.getUniqueId(), weapon, holder.worldId,
                player.getWorld().getName(), holder.x, holder.y, holder.z, now, now + duration, "ACTIVE");
        if (!removeIngredients(player, ingredients)) return;
        store.putJob(job);
        message(player, plugin.getConfig().getString("general.messages.started", "§aCrafting {weapon} started.")
                .replace("{weapon}", weapon.displayName()));
        player.closeInventory();
        showBar(player, job);
    }

    private boolean isForge(Block block) {
        Material configured = Material.matchMaterial(plugin.getConfig().getString("crafting.forge.material", "LODESTONE"));
        return configured != null && block.getType() == configured;
    }

    private boolean isOwnerOrAdmin(Player player, String forgeKey) {
        return player.hasPermission("voidweapon.admin") || player.getUniqueId().toString().equals(store.forgeOwner(forgeKey));
    }

    private ItemStack weaponButton(ForgeHolder holder, Player player, WeaponId weapon) {
        ItemStack item = weapons.create(weapon);
        if (item == null) return new ItemStack(Material.matchMaterial("BARRIER"));
        ItemMeta meta = item.getItemMeta();
        List<String> lore = new ArrayList<>();
        boolean unlocked = store.recipeUnlocked(player.getUniqueId(), weapon) || player.hasPermission("voidweapon.admin");
        lore.add(unlocked ? "§aRecipe unlocked" : "§cRecipe locked");
        lore.add("§7Duration: " + plugin.getConfig().getLong("crafting.duration-seconds", 1200) + "s");
        lore.add("§7Forge: " + holder.worldName + " " + holder.x + ", " + holder.y + ", " + holder.z);
        lore.add("§7Materials:");
        ingredients(weapon).forEach((material, amount) -> lore.add("§8- §f" + amount + "x " + material.name()));
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, holder.token.toString());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack statusButton(ForgeHolder holder, Player player) {
        ItemStack item = new ItemStack(Material.matchMaterial("PAPER"));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§dCurrent crafting status");
        List<String> lore = new ArrayList<>();
        CraftJob job = store.jobForOwner(player.getUniqueId());
        if (job == null) lore.add("§7No active job or pending reward.");
        else {
            long seconds = Math.max(0, (job.completesAt() - System.currentTimeMillis() + 999) / 1000);
            lore.add("§7" + job.weapon().displayName() + " §8| §f" + job.status());
            if (job.status().equals("ACTIVE")) lore.add("§7Remaining: " + seconds + "s");
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, holder.token.toString());
        item.setItemMeta(meta);
        return item;
    }

    private Map<Material, Integer> ingredients(WeaponId weapon) {
        String base = "crafting.recipes." + weapon.id();
        List<String> shape = plugin.getConfig().getStringList(base + ".shape");
        ConfigurationSection ingredients = plugin.getConfig().getConfigurationSection(base + ".ingredients");
        Map<Material, Integer> required = new HashMap<>();
        if (ingredients == null || shape.size() != 3) return required;
        for (String row : shape) {
            if (row.length() != 3) return Map.of();
            for (char symbol : row.toCharArray()) {
                if (symbol == ' ') continue;
                String materialName = ingredients.getString(String.valueOf(symbol));
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material == null) return Map.of();
                required.merge(material, 1, Integer::sum);
            }
        }
        return required;
    }

    private boolean hasIngredients(Player player, Map<Material, Integer> required) {
        Map<Material, Integer> found = new HashMap<>();
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item != null) found.merge(item.getType(), item.getAmount(), Integer::sum);
        }
        return required.entrySet().stream().allMatch(entry -> found.getOrDefault(entry.getKey(), 0) >= entry.getValue());
    }

    private boolean removeIngredients(Player player, Map<Material, Integer> required) {
        if (!hasIngredients(player, required)) return false;
        ItemStack[] contents = player.getInventory().getStorageContents();
        Map<Material, Integer> remaining = new HashMap<>(required);
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || !remaining.containsKey(item.getType())) continue;
            int take = Math.min(item.getAmount(), remaining.get(item.getType()));
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) contents[i] = null;
            remaining.put(item.getType(), remaining.get(item.getType()) - take);
        }
        if (remaining.values().stream().anyMatch(amount -> amount != 0)) return false;
        player.getInventory().setStorageContents(contents);
        return true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (CraftJob job : new ArrayList<>(store.jobs())) {
            if (job.status().equals("ACTIVE") && now >= job.completesAt()) {
                CraftJob ready = job.withStatus("READY");
                store.putJob(ready);
                hideBar(job.owner());
                Player player = Bukkit.getPlayer(job.owner());
                if (player != null) deliverPending(player);
            } else if (job.status().equals("ACTIVE")) {
                Player player = Bukkit.getPlayer(job.owner());
                if (player != null) showBar(player, job);
            } else if (job.status().equals("READY")) {
                Player player = Bukkit.getPlayer(job.owner());
                if (player != null && now - rewardAttempts.getOrDefault(job.owner(), 0L) >= 10_000) {
                    rewardAttempts.put(job.owner(), now);
                    deliverPending(player);
                }
            }
        }
    }

    private void recoverJobs() {
        for (CraftJob job : store.jobs()) {
            if (!job.status().equals("ACTIVE") || job.completesAt() > System.currentTimeMillis()) continue;
            store.putJob(job.withStatus("READY"));
        }
    }

    private void deliver(Player player, CraftJob job) {
        if (store.job(job.id()) == null || !store.job(job.id()).status().equals("READY")) return;
        String forgeKey = job.worldId() + "_" + job.x() + "_" + job.y() + "_" + job.z();
        if (!canUseForge(player, forgeKey, true)) return;
        if (weapons.alreadyDelivered(player, job.id().toString())) {
            store.putJob(job.withStatus("DELIVERED"));
            hideBar(player.getUniqueId());
            return;
        }
        ItemStack reward = weapons.create(job.weapon());
        if (reward == null) return;
        weapons.markReward(reward, job.id().toString());
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        if (!leftovers.isEmpty()) {
            message(player, plugin.getConfig().getString("general.messages.reward-pending", "§eYour weapon is ready, but your inventory is full."));
            return;
        }
        store.putJob(job.withStatus("DELIVERED"));
        hideBar(player.getUniqueId());
        message(player, plugin.getConfig().getString("general.messages.completed", "§aYour {weapon} is ready.")
                .replace("{weapon}", job.weapon().displayName()));
    }

    private void showBar(Player player, CraftJob job) {
        if (!plugin.getConfig().getBoolean("crafting.bossbar.enabled", true)) return;
        double duration = Math.max(1, job.completesAt() - job.startedAt());
        double remaining = Math.max(0, job.completesAt() - System.currentTimeMillis());
        double progress = Math.max(0, Math.min(1, 1.0 - remaining / duration));
        long seconds = (long) Math.ceil(remaining / 1000.0);
        String title = "§5VOID WEAPON §8| §aCRAFTING §d" + job.weapon().displayName() + " §8- §f" + seconds + "s §7(" + Math.round(progress * 100) + "%)";
        BarColor color;
        BarStyle style;
        try { color = BarColor.valueOf(plugin.getConfig().getString("crafting.bossbar.color", "PURPLE").toUpperCase()); }
        catch (RuntimeException exception) { color = BarColor.PURPLE; }
        try { style = BarStyle.valueOf(plugin.getConfig().getString("crafting.bossbar.style", "SOLID").toUpperCase()); }
        catch (RuntimeException exception) { style = BarStyle.SOLID; }
        final String bossBarTitle = title; final BarColor bossBarColor = color; final BarStyle bossBarStyle = style; BossBar bar = bossBars.computeIfAbsent(player.getUniqueId(), ignored -> Bukkit.createBossBar(bossBarTitle, bossBarColor, bossBarStyle));
        bar.setTitle(title);
        bar.setProgress(progress);
        if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
        if (plugin.getConfig().getBoolean("crafting.bossbar.trusted-visibility", false)) {
            String forgeKey = job.worldId() + "_" + job.x() + "_" + job.y() + "_" + job.z();
            for (UUID trustedId : store.trustedPlayers(forgeKey)) {
                Player trusted = Bukkit.getPlayer(trustedId);
                if (trusted != null && canUseForge(trusted, forgeKey, false) && !bar.getPlayers().contains(trusted)) bar.addPlayer(trusted);
            }
        }
    }

    private void hideBar(UUID player) {
        BossBar bar = bossBars.remove(player);
        if (bar != null) bar.removeAll();
    }

    private void message(Player player, String text) {
        player.sendMessage(plugin.getConfig().getString("general.messages.prefix", "§5[VOID WEAPON] ") + text);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ForgeHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) return;
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String token = item.getItemMeta().getPersistentDataContainer().get(guiKey, PersistentDataType.STRING);
        if (!holder.token.toString().equals(token)) return;
        onWeaponClick(player, holder, slot);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ForgeHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (CraftJob job : store.jobs()) {
                if (!job.owner().equals(event.getPlayer().getUniqueId())) continue;
                if (job.status().equals("ACTIVE")) showBar(event.getPlayer(), job);
                if (job.status().equals("READY")) deliverPending(event.getPlayer());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        hideBar(event.getPlayer().getUniqueId());
    }

    private static final class ForgeHolder implements InventoryHolder {
        private final UUID owner;
        private final UUID token;
        private final String forgeKey;
        private final UUID worldId;
        private final String worldName;
        private final int x;
        private final int y;
        private final int z;
        private Inventory inventory;

        private ForgeHolder(UUID owner, UUID token, String forgeKey, UUID worldId, String worldName, int x, int y, int z) {
            this.owner = owner;
            this.token = token;
            this.forgeKey = forgeKey;
            this.worldId = worldId;
            this.worldName = worldName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Inventory getInventory() { return inventory; }
    }
}