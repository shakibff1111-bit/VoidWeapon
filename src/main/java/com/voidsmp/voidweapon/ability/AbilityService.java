package com.voidsmp.voidweapon.ability;

import com.voidsmp.voidweapon.VoidWeaponPlugin;
import com.voidsmp.voidweapon.storage.StateStore;
import com.voidsmp.voidweapon.weapon.WeaponId;
import com.voidsmp.voidweapon.weapon.WeaponService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.Trident;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class AbilityService {
    private final VoidWeaponPlugin plugin;
    private final WeaponService weapons;
    private final StateStore store;
    private final CooldownService cooldowns;
    private final NamespacedKey projectileKey;
    private final Set<BukkitTask> tasks = new HashSet<>();
    private final java.util.Map<Integer, UUID> taskOwners = new java.util.HashMap<>();
    private final java.util.Map<UUID, Long> projectiles = new java.util.HashMap<>();

    public AbilityService(VoidWeaponPlugin plugin, WeaponService weapons, StateStore store, CooldownService cooldowns) {
        this.plugin = plugin;
        this.weapons = weapons;
        this.store = store;
        this.cooldowns = cooldowns;
        this.projectileKey = new NamespacedKey(plugin, "ability_projectile");
    }

    public boolean activateSelected(Player player, WeaponId weapon) {
        return activate(player, weapon, store.selection(player.getUniqueId(), weapon));
    }

    public boolean activate(Player player, WeaponId weapon, String ability) {
        if (!player.hasPermission("voidweapon.use") || !allowedGameMode(player) || weapons.identify(player.getInventory().getItemInMainHand()) != weapon) {
            message(player, "§cHold the correct VOID WEAPON and ensure you can use it.");
            return false;
        }
        if (!weapon.abilities().contains(ability)) {
            message(player, "§cUnknown ability for " + weapon.displayName() + ".");
            return false;
        }
        if (tasks.size() >= 128) {
            message(player, "§cToo many active VOID WEAPON effects. Try again shortly.");
            return false;
        }
        String key = weapon.id() + ":" + ability;
        if (cooldowns.isCoolingDown(player.getUniqueId(), key)) {
            message(player, plugin.getConfig().getString("general.messages.cooldown", "§cRecharging for {time}s.")
                    .replace("{time}", String.valueOf(Math.max(1, (cooldowns.remainingMillis(player.getUniqueId(), key) + 999) / 1000))));
            return false;
        }
        boolean activated;
        try {
            activated = execute(player, weapon, ability);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Ability " + key + " stopped safely: " + exception.getMessage());
            message(player, "§cThat ability could not be activated safely.");
            return false;
        }
        if (activated) {
            long duration = (long) (number(weapon, ability, "cooldown-seconds", 10.0, 0.0, 3600.0) * 1000.0);
            cooldowns.start(player.getUniqueId(), key, duration);
        }
        return activated;
    }

    public void onQuit(PlayerQuitEvent event) {
        cancelTasks(event.getPlayer().getUniqueId());
    }

    public void onDeath(PlayerDeathEvent event) {
        cancelTasks(event.getEntity().getUniqueId());
    }

    public void onWorldChange(PlayerChangedWorldEvent event) {
        cancelTasks(event.getPlayer().getUniqueId());
    }

    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(projectileKey, PersistentDataType.BYTE)) {
            projectiles.remove(event.getEntity().getUniqueId());
        }
    }

    private void cancelTasks(UUID playerId) {
        for (BukkitTask task : new ArrayList<>(tasks)) {
            if (!playerId.equals(taskOwners.get(task.getTaskId()))) continue;
            Burst burst = BURSTS.get(task.getTaskId());
            if (burst != null) interrupt(task, burst);
            else {
                task.cancel();
                tasks.remove(task);
                taskOwners.remove(task.getTaskId());
            }
        }
    }

    public void shutdown() {
        for (BukkitTask task : new ArrayList<>(tasks)) {
            Burst burst = BURSTS.get(task.getTaskId());
            if (burst != null) interrupt(task, burst);
            else {
                task.cancel();
                taskOwners.remove(task.getTaskId());
            }
        }
        tasks.clear();
    }

    private boolean execute(Player player, WeaponId weapon, String ability) {
        return switch (ability) {
            case "auto_fire" -> autoFire(player, weapon, ability);
            case "phantom_volley" -> phantomVolley(player, weapon, ability);
            case "piercing_shot" -> piercingShot(player, weapon, ability);
            case "phantom_dash" -> phantomDash(player, weapon, ability);
            case "cyclone_cut" -> areaDamage(player, weapon, ability, 0.7, 0.0, false);
            case "sky_render" -> areaDamage(player, weapon, ability, 1.1, 1.0, false);
            case "thunder_throw" -> throwArrow(player, weapon, ability, "speed", "range");
            case "chain_lightning" -> chainLightning(player, weapon, ability);
            case "storm_god" -> areaDamage(player, weapon, ability, 0.0, 0.0, true);
            case "void_smash" -> areaDamage(player, weapon, ability, 1.0, 0.0, false);
            case "gravity_break" -> gravityBreak(player, weapon, ability);
            case "collapse" -> areaDamage(player, weapon, ability, 0.0, 0.0, false);
            case "demon_slash" -> directionalSlash(player, weapon, ability);
            case "crimson_burst" -> areaDamage(player, weapon, ability, 0.0, 0.0, false);
            case "hellfire_domain" -> hellfireDomain(player, weapon, ability);
            case "god_strike" -> godStrike(player, weapon, ability);
            case "divine_judgment" -> areaDamage(player, weapon, ability, 0.0, 0.0, true);
            default -> false;
        };
    }

    private boolean autoFire(Player player, WeaponId weapon, String ability) {
        int count = (int) number(weapon, ability, "count", 8, 1, 8);
        if (count != 8) count = 8;
        List<ItemStack> reserved = reserve(player.getUniqueId(), count);
        if (reserved == null) {
            message(player, plugin.getConfig().getString("general.messages.no-ammo", "§cAuto Fire requires 8 stored arrows."));
            return false;
        }
        startBurst(player, weapon, ability, count, reserved, (int) number(weapon, ability, "interval-ticks", 2, 1, 20));
        return true;
    }

    private boolean phantomVolley(Player player, WeaponId weapon, String ability) {
        int count = (int) number(weapon, ability, "count", 5, 1, 16);
        List<ItemStack> reserved = reserve(player.getUniqueId(), count);
        if (reserved == null) {
            message(player, "§cPhantom Volley requires " + count + " stored arrows.");
            return false;
        }
        double damage = number(weapon, ability, "damage", 5, 0, 100);
        double speed = number(weapon, ability, "speed", 2.8, 0.1, 6);
        double range = number(weapon, ability, "range", 40, 1, 64);
        double spread = number(weapon, ability, "spread", 0.12, 0, 0.5);
        int fired = 0;
        try {
            for (; fired < count; fired++) fireArrow(player, itemAt(reserved, fired), damage, speed, spread, range);
            return true;
        } catch (RuntimeException exception) {
            refund(player.getUniqueId(), unused(reserved, fired));
            plugin.getLogger().warning("Phantom Volley stopped safely after a projectile could not be spawned: " + exception.getMessage());
            return false;
        }
    }

    private boolean piercingShot(Player player, WeaponId weapon, String ability) {
        double range = number(weapon, ability, "range", 48, 1, 64);
        int limit = (int) number(weapon, ability, "penetrations", 2, 1, 8);
        double damage = number(weapon, ability, "damage", 14, 0, 100);
        double speed = number(weapon, ability, "speed", 4.0, 0.1, 6);
        double spread = number(weapon, ability, "spread", 0, 0, 0.5);
        AbstractArrow arrow = fireArrow(player, new ItemStack(org.bukkit.Material.matchMaterial("ARROW")), damage, speed, spread, range);
        arrow.setPierceLevel(limit);
        return true;
    }

    private boolean phantomDash(Player player, WeaponId weapon, String ability) {
        double distance = number(weapon, ability, "distance", 8, 1, 12);
        Vector direction = player.getLocation().getDirection().normalize();
        Location origin = player.getLocation();
        Location destination = origin.clone();
        for (double step = 0.5; step <= distance; step += 0.5) {
            Location candidate = origin.clone().add(direction.clone().multiply(step));
            if (!candidate.getBlock().isPassable() || !candidate.clone().add(0, 1, 0).getBlock().isPassable()
                    || !hasSafeGround(candidate)) break;
            destination = candidate;
        }
        if (destination.distanceSquared(origin) < 0.25) return false;
        boolean moved = player.teleport(destination);
        if (moved) {
            player.setVelocity(direction.multiply(number(weapon, ability, "speed", 1.8, 0.1, 3.0)));
        }
        return moved;
    }

    private boolean areaDamage(Player player, WeaponId weapon, String ability, double knockback, double launch, boolean lightning) {
        double range = number(weapon, ability, "range", 6, 1, 16);
        double damage = number(weapon, ability, "damage", 10, 0, 100);
        knockback = number(weapon, ability, "knockback", knockback, 0, 3);
        launch = number(weapon, ability, "launch", launch, 0, 3);
        int targetLimit = (int) number(weapon, ability, "targets", 8, 1, 16);
        List<LivingEntity> targets = targets(player, range, targetLimit);
        if (targets.isEmpty()) return false;
        for (LivingEntity target : targets) {
            target.damage(damage, player);
            if (knockback > 0 || launch > 0) {
                Vector push = target.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                push.setY(Math.max(launch, push.getY() * 0.35));
                target.setVelocity(push.multiply(knockback > 0 ? knockback : 0.8));
            }
            if (lightning) target.getWorld().strikeLightningEffect(target.getLocation());
        }
        if (lightning) player.getWorld().strikeLightningEffect(player.getTargetBlockExact((int) range) == null
                ? player.getLocation().add(player.getLocation().getDirection().multiply(range))
                : player.getTargetBlockExact((int) range).getLocation());
        return true;
    }

    private boolean throwArrow(Player player, WeaponId weapon, String ability, String speedKey, String rangeKey) {
        double range = number(weapon, ability, rangeKey, 45, 1, 64);
        double speed = number(weapon, ability, speedKey, 3.2, 0.1, 6);
        double damage = number(weapon, ability, "damage", 12, 0, 100);
        Vector direction = player.getEyeLocation().getDirection().normalize();
        double spread = number(weapon, ability, "spread", 0, 0, 0.5);
        if (spread > 0) direction.add(new Vector(randomSpread(spread), randomSpread(spread), randomSpread(spread))).normalize();
        Trident trident = player.launchProjectile(Trident.class);
        if (!trident.isValid()) throw new IllegalStateException("Projectile launch was cancelled");
        trident.setVelocity(direction.multiply(speed));
        trident.setDamage(damage);
        trident.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
        trident.setLifetimeTicks((int) Math.max(2, Math.min(1200, Math.ceil(range / speed))));
        trackProjectile(trident, range, speed);
        return true;
    }

    private boolean chainLightning(Player player, WeaponId weapon, String ability) {
        List<LivingEntity> targets = targets(player, number(weapon, ability, "range", 12, 1, 24),
                (int) number(weapon, ability, "jumps", 4, 1, 8));
        if (targets.isEmpty()) return false;
        double damage = number(weapon, ability, "damage", 8, 0, 100);
        double scale = number(weapon, ability, "jump-damage-scale", 0.75, 0.1, 1.0);
        for (int i = 0; i < targets.size(); i++) {
            LivingEntity target = targets.get(i);
            target.damage(damage * Math.pow(scale, i), player);
            target.getWorld().strikeLightningEffect(target.getLocation());
        }
        return true;
    }

    private boolean directionalSlash(Player player, WeaponId weapon, String ability) {
        double range = number(weapon, ability, "range", 5, 1, 8);
        double damage = number(weapon, ability, "damage", 10, 0, 100);
        int cap = (int) number(weapon, ability, "targets", 3, 1, 8);
        Vector direction = player.getEyeLocation().getDirection().normalize();
        List<LivingEntity> targets = player.getNearbyEntities(range, range, range).stream()
                .filter(entity -> entity instanceof LivingEntity && entity != player && canDamage(player, entity))
                .filter(entity -> player.hasLineOfSight(entity))
                .filter(entity -> entity.getLocation().distanceSquared(player.getLocation()) <= range * range)
                .filter(entity -> entity.getLocation().toVector().subtract(player.getEyeLocation().toVector()).normalize().dot(direction) >= 0.35)
                .map(entity -> (LivingEntity) entity)
                .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                .limit(Math.max(1, Math.min(8, cap))).toList();
        for (LivingEntity target : targets) target.damage(damage, player);
        return !targets.isEmpty();
    }

    private boolean gravityBreak(Player player, WeaponId weapon, String ability) {
        double range = number(weapon, ability, "range", 10, 1, 16);
        int duration = (int) number(weapon, ability, "duration-ticks", 40, 1, 100);
        double strength = number(weapon, ability, "pull-strength", 0.45, 0.05, 0.8);
        List<LivingEntity> affected = targets(player, range, 16);
        if (affected.isEmpty()) return false;
        BukkitTask[] reference = new BukkitTask[1];
        final int[] ticks = {0};
        reference[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || ticks[0]++ >= duration) {
                reference[0].cancel();
                tasks.remove(reference[0]);
                taskOwners.remove(reference[0].getTaskId());
                return;
            }
            for (LivingEntity target : affected) {
                if (!target.isValid() || target.getWorld() != player.getWorld() || !player.hasLineOfSight(target)) continue;
                Vector pull = player.getLocation().toVector().subtract(target.getLocation().toVector());
                if (pull.lengthSquared() < 0.25) continue;
                Vector velocity = pull.normalize().multiply(strength);
                if (target.getLocation().getY() + velocity.getY() < target.getWorld().getMinHeight() + 2) velocity.setY(0.1);
                target.setVelocity(velocity);
            }
        }, 0L, 2L);
        tasks.add(reference[0]);
        taskOwners.put(reference[0].getTaskId(), player.getUniqueId());
        return true;
    }

    private boolean hellfireDomain(Player player, WeaponId weapon, String ability) {
        double range = number(weapon, ability, "range", 6, 1, 12);
        int duration = (int) number(weapon, ability, "duration-ticks", 100, 1, 200);
        int interval = (int) number(weapon, ability, "interval-ticks", 20, 10, 40);
        double damage = number(weapon, ability, "damage", 3, 0, 30);
        int cap = (int) number(weapon, ability, "targets", 8, 1, 16);
        BukkitTask[] reference = new BukkitTask[1];
        final int[] elapsed = {0};
        reference[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || elapsed[0] >= duration) {
                reference[0].cancel();
                tasks.remove(reference[0]);
                taskOwners.remove(reference[0].getTaskId());
                return;
            }
            if (elapsed[0] % interval == 0) for (LivingEntity target : targets(player, range, cap)) target.damage(damage, player);
            elapsed[0] += 2;
        }, 0L, 2L);
        tasks.add(reference[0]);
        taskOwners.put(reference[0].getTaskId(), player.getUniqueId());
        return true;
    }

    private boolean godStrike(Player player, WeaponId weapon, String ability) {
        double range = number(weapon, ability, "range", 16, 1, 32);
        Entity target = player.getTargetEntity((int) range);
        if (!(target instanceof LivingEntity living) || target == player || !player.hasLineOfSight(target) || !canDamage(player, target)) return false;
        living.damage(number(weapon, ability, "damage", 22, 0, 100), player);
        living.getWorld().strikeLightningEffect(living.getLocation());
        return true;
    }

    private List<LivingEntity> targets(Player player, double range, int limit) {
        return player.getNearbyEntities(range, range, range).stream()
                .filter(entity -> entity instanceof LivingEntity && entity != player && entity.isValid())
                .filter(entity -> canDamage(player, entity))
                .filter(entity -> player.hasLineOfSight(entity))
                .filter(entity -> entity.getLocation().distanceSquared(player.getLocation()) <= range * range)
                .map(entity -> (LivingEntity) entity)
                .sorted(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(player.getLocation())))
                .limit(Math.max(1, Math.min(16, limit))).toList();
    }

    private AbstractArrow fireArrow(Player player, ItemStack ammunition, double damage, double speed, double spread, double range) {
        Vector direction = player.getEyeLocation().getDirection().normalize();
        if (spread > 0) {
            direction.add(new Vector(randomSpread(spread), randomSpread(spread), randomSpread(spread))).normalize();
        }
        AbstractArrow arrow = ammunition.getType().name().equals("SPECTRAL_ARROW")
                ? player.launchProjectile(SpectralArrow.class) : player.launchProjectile(Arrow.class);
        if (!arrow.isValid()) throw new IllegalStateException("Projectile launch was cancelled");
        arrow.setVelocity(direction.multiply(speed));
        arrow.setDamage(damage);
        arrow.setItemStack(ammunition.clone());
        if (arrow instanceof Arrow tipped && ammunition.getItemMeta() instanceof PotionMeta potionMeta) {
            tipped.setBasePotionType(potionMeta.getBasePotionType());
            if (potionMeta.hasCustomEffects()) {
                for (var effect : potionMeta.getCustomEffects()) tipped.addCustomEffect(effect, true);
            }
            if (potionMeta.getColor() != null) tipped.setColor(potionMeta.getColor());
        }
        arrow.setPickupStatus(AbstractArrow.PickupStatus.CREATIVE_ONLY);
        arrow.setLifetimeTicks((int) Math.max(2, Math.min(1200, Math.ceil(range / speed))));
        trackProjectile(arrow, range, speed);
        return arrow;
    }

    private void trackProjectile(AbstractArrow projectile, double range, double speed) {
        long now = System.currentTimeMillis();
        projectiles.entrySet().removeIf(entry -> entry.getValue() <= now);
        int limit = Math.max(16, Math.min(1024, plugin.getConfig().getInt("performance.max-active-projectiles", 256)));
        if (projectiles.size() >= limit) {
            projectile.remove();
            throw new IllegalStateException("Active ability projectile limit reached");
        }
        projectile.getPersistentDataContainer().set(projectileKey, PersistentDataType.BYTE, (byte) 1);
        long lifetime = (long) (Math.ceil(range / speed) + 3) * 50L + 5_000L;
        projectiles.put(projectile.getUniqueId(), now + Math.max(1_000L, lifetime));
    }

    private void startBurst(Player player, WeaponId weapon, String ability, int count, List<ItemStack> reserved, int interval) {
        Burst burst = new Burst(player.getUniqueId(), player.getWorld().getUID(), reserved, count,
                number(weapon, ability, "damage", 6, 0, 100), number(weapon, ability, "speed", 3, 0.1, 6),
                number(weapon, ability, "spread", 0.025, 0, 0.5));
        BukkitTask[] reference = new BukkitTask[1];
        reference[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player current = Bukkit.getPlayer(burst.playerId);
            if (current == null || !current.isOnline() || !current.getWorld().getUID().equals(burst.worldId)) {
                interrupt(reference[0], burst);
                return;
            }
            try {
                fireArrow(current, itemAt(burst.reserved, burst.fired), burst.damage, burst.speed, burst.spread,
                        number(weapon, ability, "range", 48, 1, 64));
                burst.fired++;
                if (burst.fired >= burst.count) finish(reference[0], burst);
            } catch (RuntimeException exception) {
                interrupt(reference[0], burst);
                plugin.getLogger().warning("Auto Fire burst stopped safely: " + exception.getMessage());
            }
        }, 0L, interval);
        burst.taskId = reference[0].getTaskId();
        BURSTS.put(burst.taskId, burst);
        tasks.add(reference[0]);
        taskOwners.put(burst.taskId, player.getUniqueId());
    }

    private void finish(BukkitTask task, Burst burst) {
        task.cancel();
        tasks.remove(task);
        BURSTS.remove(task.getTaskId());
        taskOwners.remove(task.getTaskId());
    }

    private void interrupt(BukkitTask task, Burst burst) {
        task.cancel();
        tasks.remove(task);
        BURSTS.remove(task.getTaskId());
        taskOwners.remove(task.getTaskId());
        List<ItemStack> unfired = new ArrayList<>();
        int keep = burst.count - burst.fired;
        for (ItemStack stack : burst.reserved) {
            if (keep <= 0) break;
            ItemStack refund = stack.clone();
            int amount = Math.min(keep, refund.getAmount());
            refund.setAmount(amount);
            unfired.add(refund);
            keep -= amount;
        }
        if (!unfired.isEmpty()) refund(burst.playerId, unfired);
    }

    private List<ItemStack> reserve(UUID player, int count) {
        List<ItemStack> current = store.ammo(player);
        int total = current.stream().mapToInt(ItemStack::getAmount).sum();
        if (total < count) return null;
        List<ItemStack> reserved = new ArrayList<>();
        int remaining = count;
        for (int index = 0; index < current.size() && remaining > 0; index++) {
            ItemStack stack = current.get(index);
            int amount = Math.min(remaining, stack.getAmount());
            ItemStack taken = stack.clone();
            taken.setAmount(amount);
            reserved.add(taken);
            stack.setAmount(stack.getAmount() - amount);
            remaining -= amount;
        }
        store.setAmmo(player, current);
        return reserved;
    }

    private void refund(UUID player, List<ItemStack> refund) {
        List<ItemStack> current = store.ammo(player);
        for (ItemStack incoming : refund) {
            for (ItemStack existing : current) {
                if (!existing.isSimilar(incoming) || existing.getAmount() >= existing.getMaxStackSize()) continue;
                int moved = Math.min(incoming.getAmount(), existing.getMaxStackSize() - existing.getAmount());
                existing.setAmount(existing.getAmount() + moved);
                incoming.setAmount(incoming.getAmount() - moved);
                if (incoming.getAmount() == 0) break;
            }
            if (incoming.getAmount() > 0) current.add(incoming.clone());
        }
        store.setAmmo(player, current);
    }

    private List<ItemStack> unused(List<ItemStack> reserved, int used) {
        List<ItemStack> unused = new ArrayList<>();
        for (ItemStack stack : reserved) {
            if (used >= stack.getAmount()) {
                used -= stack.getAmount();
                continue;
            }
            ItemStack refund = stack.clone();
            refund.setAmount(refund.getAmount() - used);
            unused.add(refund);
            used = 0;
        }
        return unused;
    }

    private ItemStack itemAt(List<ItemStack> stacks, int index) {
        for (ItemStack stack : stacks) {
            if (index < stack.getAmount()) {
                ItemStack ammunition = stack.clone();
                ammunition.setAmount(1);
                return ammunition;
            }
            index -= stack.getAmount();
        }
        throw new IllegalStateException("Reserved ammunition did not match projectile count");
    }

    private boolean canDamage(Player attacker, Entity target) {
        if (!(target instanceof Player victim)) return true;
        if (!attacker.getWorld().getPVP()) return false;
        var scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        if (scoreboard == null) return true;
        var attackerTeam = scoreboard.getEntryTeam(attacker.getName());
        var victimTeam = scoreboard.getEntryTeam(victim.getName());
        return attackerTeam == null || victimTeam == null || !attackerTeam.equals(victimTeam) || attackerTeam.allowFriendlyFire();
    }

    private double number(WeaponId weapon, String ability, String key, double fallback, double min, double max) {
        double value = plugin.getConfig().getDouble("weapons." + weapon.id() + ".abilities." + ability + "." + key, fallback);
        if (!Double.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private boolean allowedGameMode(Player player) {
        List<String> allowed = plugin.getConfig().getStringList("general.allowed-game-modes");
        if (!allowed.isEmpty() && !allowed.contains(player.getGameMode().name())) return false;
        List<String> worlds = plugin.getConfig().getStringList("general.allowed-worlds");
        return worlds.isEmpty() || worlds.contains(player.getWorld().getName());
    }

    private boolean hasSafeGround(Location location) {
        if (location.getY() < location.getWorld().getMinHeight() + 3) return false;
        for (int depth = 1; depth <= 4; depth++) {
            if (location.clone().subtract(0, depth, 0).getBlock().getType().isSolid()) return true;
        }
        return false;
    }

    private void message(Player player, String text) {
        player.sendMessage(plugin.getConfig().getString("general.messages.prefix", "§5[VOID WEAPON] ") + text);
    }

    private double randomSpread(double spread) {
        return ThreadLocalRandom.current().nextDouble(-spread, spread);
    }

    private static final java.util.Map<Integer, Burst> BURSTS = new java.util.HashMap<>();

    private static final class Burst {
        private final UUID playerId;
        private final UUID worldId;
        private final List<ItemStack> reserved;
        private final int count;
        private final double damage;
        private final double speed;
        private final double spread;
        private int fired;
        private int taskId;

        private Burst(UUID playerId, UUID worldId, List<ItemStack> reserved, int count, double damage, double speed, double spread) {
            this.playerId = playerId;
            this.worldId = worldId;
            this.reserved = reserved;
            this.count = count;
            this.damage = damage;
            this.speed = speed;
            this.spread = spread;
        }
    }
}