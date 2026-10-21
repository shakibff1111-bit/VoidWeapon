package com.voidsmp.voidweapon.gui;

import com.voidsmp.voidweapon.VoidWeaponPlugin;
import com.voidsmp.voidweapon.storage.StateStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArrowStorageService implements Listener {
    private final VoidWeaponPlugin plugin;
    private final StateStore store;
    private final NamespacedKey guiKey;

    public ArrowStorageService(VoidWeaponPlugin plugin, StateStore store) {
        this.plugin = plugin;
        this.store = store;
        this.guiKey = new NamespacedKey(plugin, "arrow_storage_gui");
    }

    public void open(Player player) {
        if (!player.hasPermission("voidweapon.use")) return;
        int size = plugin.getConfig().getInt("general.arrow-storage.gui-size", 27);
        size = Math.max(27, Math.min(54, ((size + 8) / 9) * 9));
        ArrowHolder holder = new ArrowHolder(player.getUniqueId(), UUID.randomUUID());
        Inventory inventory = Bukkit.createInventory(holder, size, "§5§lVOID WEAPON §8| §dArrow Storage");
        holder.inventory = inventory;
        refresh(holder);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ArrowHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (!player.hasPermission("voidweapon.use")) {
            player.closeInventory();
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !isButton(clicked, holder)) return;
        if (event.getRawSlot() == 11) deposit(player);
        if (event.getRawSlot() == 15) withdraw(player);
        refresh(holder);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ArrowHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onMove(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof ArrowHolder || event.getDestination().getHolder() instanceof ArrowHolder) {
            event.setCancelled(true);
        }
    }

    private void deposit(Player player) {
        int capacity = Math.max(0, plugin.getConfig().getInt("general.arrow-storage.capacity", 2304));
        int remaining = Math.max(0, capacity - store.ammoCount(player.getUniqueId()));
        if (remaining == 0) return;
        List<ItemStack> stored = store.ammo(player.getUniqueId());
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!isArrow(item)) continue;
            int moved = Math.min(remaining, item.getAmount());
            ItemStack deposit = item.clone();
            deposit.setAmount(moved);
            addStack(stored, deposit);
            remaining -= moved;
            if (moved == item.getAmount()) contents[slot] = null;
            else item.setAmount(item.getAmount() - moved);
        }
        player.getInventory().setStorageContents(contents);
        store.setAmmo(player.getUniqueId(), stored);
    }

    private void withdraw(Player player) {
        List<ItemStack> stored = store.ammo(player.getUniqueId());
        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack stack : stored) {
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(stack.clone());
            remaining.addAll(leftovers.values());
        }
        store.setAmmo(player.getUniqueId(), remaining);
    }

    private void addStack(List<ItemStack> stored, ItemStack incoming) {
        for (ItemStack current : stored) {
            if (current.isSimilar(incoming) && current.getAmount() < current.getMaxStackSize()) {
                int moved = Math.min(incoming.getAmount(), current.getMaxStackSize() - current.getAmount());
                current.setAmount(current.getAmount() + moved);
                incoming.setAmount(incoming.getAmount() - moved);
                if (incoming.getAmount() == 0) return;
            }
        }
        while (incoming.getAmount() > 0) {
            int amount = Math.min(incoming.getAmount(), incoming.getMaxStackSize());
            ItemStack part = incoming.clone();
            part.setAmount(amount);
            stored.add(part);
            incoming.setAmount(incoming.getAmount() - amount);
        }
    }

    private boolean isArrow(ItemStack item) {
        if (item == null) return false;
        String type = item.getType().name();
        if (type.equals("ARROW")) return true;
        if (type.equals("SPECTRAL_ARROW")) return plugin.getConfig().getBoolean("general.arrow-storage.allow-spectral-arrows", true);
        return type.equals("TIPPED_ARROW") && plugin.getConfig().getBoolean("general.arrow-storage.allow-tipped-arrows", true);
    }

    private void refresh(ArrowHolder holder) {
        int stored = store.ammoCount(holder.owner);
        int capacity = plugin.getConfig().getInt("general.arrow-storage.capacity", 2304);
        holder.inventory.clear();
        holder.inventory.setItem(11, button(holder, "ARROW", "§aDeposit arrows", "§7Deposit arrows from your inventory."));
        holder.inventory.setItem(13, button(holder, "PAPER", "§dStored: §f" + stored + "§7/§f" + capacity, "§7Sneak + left-click with AUTO BOW to open."));
        holder.inventory.setItem(15, button(holder, "CHEST", "§bWithdraw arrows", "§7Withdraw all available stored arrows."));
    }

    private ItemStack button(ArrowHolder holder, String materialName, String name, String lore) {
        Material material = Material.matchMaterial(materialName);
        if (material == null) throw new IllegalStateException("Missing button material " + materialName);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        meta.getPersistentDataContainer().set(guiKey, PersistentDataType.STRING, holder.token.toString());
        item.setItemMeta(meta);
        return item;
    }

    private boolean isButton(ItemStack item, ArrowHolder holder) {
        if (!item.hasItemMeta()) return false;
        String token = item.getItemMeta().getPersistentDataContainer().get(guiKey, PersistentDataType.STRING);
        return holder.token.toString().equals(token);
    }

    private static final class ArrowHolder implements InventoryHolder {
        private final UUID owner;
        private final UUID token;
        private Inventory inventory;

        private ArrowHolder(UUID owner, UUID token) {
            this.owner = owner;
            this.token = token;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}