package com.voidsmp.voidweapon.command;

import com.voidsmp.voidweapon.VoidWeaponPlugin;
import com.voidsmp.voidweapon.ability.CooldownService;
import com.voidsmp.voidweapon.ability.AbilityService;
import com.voidsmp.voidweapon.crafting.CraftingService;
import com.voidsmp.voidweapon.storage.CraftJob;
import com.voidsmp.voidweapon.storage.StateStore;
import com.voidsmp.voidweapon.weapon.WeaponId;
import com.voidsmp.voidweapon.weapon.WeaponService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public final class VoidWeaponCommand implements CommandExecutor, TabCompleter {
    private final VoidWeaponPlugin plugin;
    private final WeaponService weapons;
    private final StateStore store;
    private final CraftingService crafting;
    private final AbilityService abilities;
    private final CooldownService cooldowns;

    public VoidWeaponCommand(VoidWeaponPlugin plugin, WeaponService weapons, StateStore store,
                             CraftingService crafting, AbilityService abilities, CooldownService cooldowns) {
        this.plugin = plugin;
        this.weapons = weapons;
        this.store = store;
        this.crafting = crafting;
        this.abilities = abilities;
        this.cooldowns = cooldowns;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("start")) {
            if (!(sender instanceof Player player)) return reply(sender, "§cOnly players can use /start.");
            if (!player.hasPermission("voidweapon.craft") && !player.hasPermission("voidweapon.admin")) return deny(sender);
            var block = player.getTargetBlockExact(6);
            if (block == null) return reply(sender, "§cLook at your configured forge block and run /start.");
            crafting.openMenu(player, block);
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) return help(sender);
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "give" -> give(sender, args);
            case "unlock" -> unlock(sender, args);
            case "trust" -> trust(sender, args, true);
            case "untrust" -> trust(sender, args, false);
            case "list" -> list(sender);
            case "reload" -> reload(sender);
            case "crafting" -> showCrafting(sender, args);
            case "cancelcraft" -> cancelCraft(sender, args);
            case "cooldowns" -> showCooldowns(sender, args);
            case "version" -> reply(sender, "§dVOID WEAPON §7v" + plugin.getDescription().getVersion() + " §8| §7Paper 26.3, Java 25");
            case "ability" -> useAbility(sender, args);
            default -> help(sender);
        };
    }

    private boolean give(CommandSender sender, String[] args) {
        if (!admin(sender)) return deny(sender);
        if (args.length < 3 || args.length > 4) return reply(sender, "§cUsage: /voidweapon give <player> <weapon> [amount]");
        Player target = Bukkit.getPlayerExact(args[1]);
        WeaponId weapon = WeaponId.parse(args[2]).orElse(null);
        if (target == null) return reply(sender, "§cThat player must be online.");
        if (weapon == null) return reply(sender, "§cUnknown weapon. Use /voidweapon list.");
        int amount = 1;
        try { if (args.length == 4) amount = Integer.parseInt(args[3]); }
        catch (NumberFormatException exception) { return reply(sender, "§cAmount must be a whole number from 1 to 64."); }
        if (amount < 1 || amount > 64) return reply(sender, "§cAmount must be a whole number from 1 to 64.");
        for (int i = 0; i < amount; i++) {
            ItemStack item = weapons.create(weapon);
            if (item != null) for (ItemStack leftover : target.getInventory().addItem(item).values()) target.getWorld().dropItemNaturally(target.getLocation(), leftover);
        }
        return reply(sender, "§aGave " + amount + " " + weapon.displayName() + " to " + target.getName() + ".");
    }

    private boolean unlock(CommandSender sender, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("recipe") || args.length > 4) {
            return reply(sender, "§cUsage: /voidweapon unlock recipe <weapon> OR /voidweapon unlock recipe <player> <weapon>");
        }
        Player target;
        WeaponId weapon;
        if (args.length == 3) {
            if (!(sender instanceof Player player)) return reply(sender, "§cConsole must specify a player.");
            if (!player.hasPermission("voidweapon.recipe.unlock") && !player.hasPermission("voidweapon.admin")) return deny(sender);
            target = player;
            weapon = WeaponId.parse(args[2]).orElse(null);
        } else {
            if (!admin(sender)) return deny(sender);
            target = Bukkit.getPlayerExact(args[2]);
            weapon = WeaponId.parse(args[3]).orElse(null);
            if (target == null) return reply(sender, "§cThat player must be online.");
        }
        if (weapon == null) return reply(sender, "§cUnknown weapon. Use /voidweapon list.");
        store.unlockRecipe(target.getUniqueId(), weapon);
        return reply(sender, "§aUnlocked " + weapon.displayName() + " recipe for " + target.getName() + ".");
    }

    private boolean trust(CommandSender sender, String[] args, boolean trust) {
        if (args.length != 2) return reply(sender, "§cUsage: /voidweapon " + (trust ? "trust" : "untrust") + " <player>");
        if (!(sender instanceof Player actor)) return reply(sender, "§cThis command must be used by a player looking at the forge.");
        if (!actor.hasPermission("voidweapon.trust") && !actor.hasPermission("voidweapon.admin")) return deny(sender);
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) return reply(sender, "§cThat player must be online.");
        crafting.trust(actor, target, trust);
        return true;
    }

    private boolean list(CommandSender sender) {
        reply(sender, "§dVOID WEAPON arsenal:");
        for (WeaponId weapon : WeaponId.values()) reply(sender, "§7- §f" + weapon.displayName() + " §8(" + weapon.id() + ")");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!admin(sender)) return deny(sender);
        plugin.reloadConfig();
        plugin.validateConfig();
        return reply(sender, "§aVOID WEAPON configuration reloaded.");
    }

    private boolean showCrafting(CommandSender sender, String[] args) {
        Player target;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) return reply(sender, "§cConsole must specify a player.");
            target = player;
        } else {
            if (args.length != 2 || !admin(sender)) return deny(sender);
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) return reply(sender, "§cThat player must be online.");
        }
        CraftJob job = store.jobForOwner(target.getUniqueId());
        if (job == null) return reply(sender, "§7No active crafting job or pending reward for " + target.getName() + ".");
        long seconds = Math.max(0, (job.completesAt() - System.currentTimeMillis() + 999) / 1000);
        return reply(sender, "§d" + job.weapon().displayName() + " §7| status: " + job.status() + " | remaining: " + seconds + "s | forge: "
                + job.worldName() + " " + job.x() + ", " + job.y() + ", " + job.z());
    }

    private boolean cancelCraft(CommandSender sender, String[] args) {
        UUID owner;
        Player actor = null;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) return reply(sender, "§cConsole must specify a player.");
            if (!player.hasPermission("voidweapon.craft") && !player.hasPermission("voidweapon.admin")) return deny(sender);
            owner = player.getUniqueId();
            actor = player;
        } else {
            if (args.length != 2 || !admin(sender)) return deny(sender);
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) return reply(sender, "§cThat player must be online.");
            owner = target.getUniqueId();
            if (sender instanceof Player player) actor = player;
        }
        if (!crafting.cancel(owner, actor)) return reply(sender, "§cThat player has no cancellable job or you lack forge access.");
        return reply(sender, "§aCrafting job cancelled. Materials are not refunded.");
    }

    private boolean showCooldowns(CommandSender sender, String[] args) {
        Player target;
        if (args.length == 1) {
            if (!(sender instanceof Player player)) return reply(sender, "§cConsole must specify a player.");
            target = player;
        } else {
            if (args.length != 2 || !admin(sender)) return deny(sender);
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) return reply(sender, "§cThat player must be online.");
        }
        Map<String, Long> active = cooldowns.snapshot(target.getUniqueId());
        if (active.isEmpty()) return reply(sender, "§aNo active ability cooldowns for " + target.getName() + ".");
        reply(sender, "§dActive cooldowns for " + target.getName() + ":");
        active.forEach((ability, millis) -> reply(sender, "§7- §f" + ability + ": " + ((millis + 999) / 1000) + "s"));
        return true;
    }

    private boolean useAbility(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) return reply(sender, "§cOnly players can activate abilities.");
        if (!player.hasPermission("voidweapon.use")) return deny(sender);
        if (args.length != 3) return reply(sender, "§cUsage: /voidweapon ability <weapon> <ability>");
        WeaponId weapon = WeaponId.parse(args[1]).orElse(null);
        if (weapon == null) return reply(sender, "§cUnknown weapon. Use /voidweapon list.");
        String ability = args[2].toLowerCase(Locale.ROOT).replace('-', '_');
        if (!weapon.abilities().contains(ability)) return reply(sender, "§cUnknown ability. Available: " + String.join(", ", weapon.abilities()));
        boolean used = abilities.activate(player, weapon, ability);
        if (used) store.selection(player.getUniqueId(), weapon, ability);
        return true;
    }

    private boolean help(CommandSender sender) {
        reply(sender, "§5§lVOID WEAPON §8| §dVOID SMP");
        reply(sender, "§7/voidweapon list §8- §fList legendary weapons");
        reply(sender, "§7/voidweapon ability <weapon> <ability> §8- §fActivate an ability directly");
        reply(sender, "§7/voidweapon crafting [player] §8- §fShow crafting status");
        reply(sender, "§7/start §8- §fOpen the forge while looking at it");
        if (sender.hasPermission("voidweapon.admin")) reply(sender, "§7/voidweapon give, unlock, reload, cancelcraft, cooldowns §8- §fAdministrative management");
        return true;
    }

    private boolean admin(CommandSender sender) { return sender.hasPermission("voidweapon.admin"); }
    private boolean deny(CommandSender sender) { return reply(sender, plugin.getConfig().getString("general.messages.no-permission", "§cYou do not have permission.")); }
    private boolean reply(CommandSender sender, String message) {
        sender.sendMessage(plugin.getConfig().getString("general.messages.prefix", "§5[VOID WEAPON] ") + message);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("start")) return List.of();
        if (args.length == 1) return partial(args[0], List.of("help", "give", "unlock", "trust", "untrust", "list", "reload", "crafting", "cancelcraft", "cooldowns", "version", "ability"));
        if (args[0].equalsIgnoreCase("give")) {
            if (args.length == 2) return onlineNames(args[1]);
            if (args.length == 3) return weaponIds(args[2]);
            if (args.length == 4) return partial(args[3], List.of("1", "2", "3", "4", "5", "6"));
        }
        if (args[0].equalsIgnoreCase("unlock")) {
            if (args.length == 2) return partial(args[1], List.of("recipe"));
            if (args.length == 3 && sender.hasPermission("voidweapon.admin")) return onlineNames(args[2]);
            if (args.length == 3) return weaponIds(args[2]);
            if (args.length == 4) return weaponIds(args[3]);
        }
        if (args[0].equalsIgnoreCase("trust") || args[0].equalsIgnoreCase("untrust") || args[0].equalsIgnoreCase("cancelcraft") || args[0].equalsIgnoreCase("crafting") || args[0].equalsIgnoreCase("cooldowns")) {
            return args.length == 2 ? onlineNames(args[1]) : List.of();
        }
        if (args[0].equalsIgnoreCase("ability")) {
            if (args.length == 2) return weaponIds(args[1]);
            if (args.length == 3) {
                WeaponId weapon = WeaponId.parse(args[1]).orElse(null);
                return weapon == null ? List.of() : partial(args[2], weapon.abilities());
            }
        }
        return List.of();
    }

    private List<String> weaponIds(String prefix) {
        return partial(prefix, java.util.Arrays.stream(WeaponId.values()).map(WeaponId::id).toList());
    }
    private List<String> onlineNames(String prefix) {
        return partial(prefix, Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
    }
    private List<String> partial(String prefix, List<String> values) {
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))).collect(Collectors.toCollection(ArrayList::new));
    }
}