package com.voidsmp.voidweapon.storage;

import com.voidsmp.voidweapon.VoidWeaponPlugin;
import com.voidsmp.voidweapon.weapon.WeaponId;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class StateStore {
    private final VoidWeaponPlugin plugin;
    private final Path file;
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "VoidWeapon-Storage");
        thread.setDaemon(true);
        return thread;
    });
    private YamlConfiguration data;

    public StateStore(VoidWeaponPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve(fileName);
        reload();
    }

    public void reload() {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.createFile(file);
            data = YamlConfiguration.loadConfiguration(file.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to load persistent VOID WEAPON data", exception);
        }
    }

    public synchronized void save() {
        String snapshot = data.saveToString();
        writer.execute(() -> writeSnapshot(snapshot));
    }

    public void close() {
        save();
        writer.shutdown();
        try {
            if (!writer.awaitTermination(10, TimeUnit.SECONDS)) {
                writer.shutdownNow();
                plugin.getLogger().severe("Timed out waiting for VOID WEAPON data to save during shutdown.");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
        }
    }

    private void writeSnapshot(String snapshot) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, snapshot);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not safely save VOID WEAPON data: " + exception.getMessage());
        }
    }

    public int ammoCount(UUID player) {
        int count = 0;
        for (ItemStack stack : ammo(player)) count += stack.getAmount();
        return count;
    }

    public List<ItemStack> ammo(UUID player) {
        List<ItemStack> result = new ArrayList<>();
        for (Object value : data.getList("players." + player + ".ammo", List.of())) {
            if (!(value instanceof Map<?, ?> map)) continue;
            Map<String, Object> serialized = new HashMap<>();
            map.forEach((key, entry) -> {
                if (key instanceof String stringKey) serialized.put(stringKey, entry);
            });
            try {
                ItemStack stack = ItemStack.deserialize(serialized);
                String type = stack.getType().name();
                boolean allowed = type.equals("ARROW")
                        || type.equals("SPECTRAL_ARROW") && plugin.getConfig().getBoolean("general.arrow-storage.allow-spectral-arrows", true)
                        || type.equals("TIPPED_ARROW") && plugin.getConfig().getBoolean("general.arrow-storage.allow-tipped-arrows", true);
                if (allowed && stack.getAmount() > 0 && stack.getAmount() <= stack.getMaxStackSize()) result.add(stack);
                else plugin.getLogger().warning("Skipped invalid stored ammunition for " + player);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Skipped invalid stored ammunition for " + player);
            }
        }
        return result;
    }

    public void setAmmo(UUID player, List<ItemStack> stacks) {
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (ItemStack stack : stacks) if (stack != null && stack.getAmount() > 0) serialized.add(stack.serialize());
        data.set("players." + player + ".ammo", serialized);
        save();
    }

    public boolean recipeUnlocked(UUID player, WeaponId weapon) {
        return data.getStringList("players." + player + ".unlocked").contains(weapon.id());
    }

    public void unlockRecipe(UUID player, WeaponId weapon) {
        List<String> unlocked = data.getStringList("players." + player + ".unlocked");
        if (!unlocked.contains(weapon.id())) unlocked.add(weapon.id());
        data.set("players." + player + ".unlocked", unlocked);
        save();
    }

    public String selection(UUID player, WeaponId weapon) {
        return data.getString("players." + player + ".selection." + weapon.id(), weapon.abilities().getFirst());
    }

    public void selection(UUID player, WeaponId weapon, String ability) {
        data.set("players." + player + ".selection." + weapon.id(), ability);
        save();
    }

    public String forgeOwner(String forgeKey) {
        return data.getString("forges." + forgeKey + ".owner");
    }

    public void setForgeOwner(String forgeKey, UUID owner) {
        data.set("forges." + forgeKey + ".owner", owner.toString());
        save();
    }

    public boolean isTrusted(String forgeKey, UUID player) {
        return data.getStringList("forges." + forgeKey + ".trusted").contains(player.toString());
    }

    public void setTrusted(String forgeKey, UUID player, boolean trusted) {
        List<String> players = data.getStringList("forges." + forgeKey + ".trusted");
        if (trusted && !players.contains(player.toString())) players.add(player.toString());
        if (!trusted) players.remove(player.toString());
        data.set("forges." + forgeKey + ".trusted", players);
        save();
    }

    public Set<UUID> trustedPlayers(String forgeKey) {
        Set<UUID> trusted = new java.util.HashSet<>();
        for (String value : data.getStringList("forges." + forgeKey + ".trusted")) {
            try {
                trusted.add(UUID.fromString(value));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Skipped invalid trusted-player UUID for forge " + forgeKey);
            }
        }
        return Set.copyOf(trusted);
    }

    public List<CraftJob> jobs() {
        List<CraftJob> jobs = new ArrayList<>();
        ConfigurationSection section = data.getConfigurationSection("jobs");
        if (section == null) return jobs;
        for (String key : section.getKeys(false)) {
            try {
                String path = "jobs." + key + ".";
                WeaponId weapon = WeaponId.parse(data.getString(path + "weapon", "")).orElse(null);
                if (weapon == null) continue;
                jobs.add(new CraftJob(UUID.fromString(key), UUID.fromString(data.getString(path + "owner")), weapon,
                        UUID.fromString(data.getString(path + "world-id")), data.getString(path + "world-name", ""),
                        data.getInt(path + "x"), data.getInt(path + "y"), data.getInt(path + "z"),
                        data.getLong(path + "started-at"), data.getLong(path + "completes-at"), data.getString(path + "status", "ACTIVE")));
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("Skipped incomplete VOID WEAPON crafting job " + key);
            }
        }
        return jobs;
    }

    public CraftJob job(UUID id) {
        return jobs().stream().filter(job -> job.id().equals(id)).findFirst().orElse(null);
    }

    public CraftJob jobForOwner(UUID owner) {
        return jobs().stream().filter(job -> job.owner().equals(owner)
                && !job.status().equals("DELIVERED") && !job.status().equals("CANCELLED")).findFirst().orElse(null);
    }

    public void putJob(CraftJob job) {
        String path = "jobs." + job.id() + ".";
        data.set(path + "owner", job.owner().toString());
        data.set(path + "weapon", job.weapon().id());
        data.set(path + "world-id", job.worldId().toString());
        data.set(path + "world-name", job.worldName());
        data.set(path + "x", job.x());
        data.set(path + "y", job.y());
        data.set(path + "z", job.z());
        data.set(path + "started-at", job.startedAt());
        data.set(path + "completes-at", job.completesAt());
        data.set(path + "status", job.status());
        save();
    }

    public void removeJob(UUID id) {
        data.set("jobs." + id, null);
        save();
    }

    public Set<String> jobKeys() {
        ConfigurationSection section = data.getConfigurationSection("jobs");
        return section == null ? Set.of() : Set.copyOf(section.getKeys(false));
    }
}