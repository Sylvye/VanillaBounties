package me.vanillabounties.gui;

import me.vanillabounties.BountyService;
import me.vanillabounties.model.AutoBountyFolder;
import me.vanillabounties.model.AutoBountyTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

public final class AutoBountyGui {
    private static final int INVENTORY_SIZE = 54;
    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int TOGGLE_SLOT = 49;
    private static final int NEXT_SLOT = 53;
    private static final int BACK_SLOT = 45;
    private static final int DELETE_SLOT = 53;

    private final BountyService bountyService;

    public AutoBountyGui(BountyService bountyService) {
        this.bountyService = bountyService;
    }

    public void openMain(Player admin, int page) {
        List<AutoBountyFolder> folders;
        boolean enabled;
        try {
            folders = bountyService.listAutoBountyFolders().stream()
                .sorted(Comparator.comparingInt(AutoBountyFolder::threshold))
                .toList();
            enabled = bountyService.autoBountiesEnabled();
        } catch (SQLException exception) {
            admin.sendMessage(Component.text("Could not load automatic bounties.", NamedTextColor.RED));
            Bukkit.getLogger().log(Level.SEVERE, "Failed to load auto bounty menu", exception);
            return;
        }

        int maxPage = Math.max(0, (folders.size() - 1) / PAGE_SIZE);
        int clampedPage = Math.max(0, Math.min(page, maxPage));
        AutoBountyMenuHolder holder = new AutoBountyMenuHolder(AutoBountyMenuHolder.Type.MAIN, clampedPage, 0, 0, "", false);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Component.text("Automatic Bounties", NamedTextColor.GOLD));
        holder.setInventory(inventory);

        int start = clampedPage * PAGE_SIZE;
        int end = Math.min(start + PAGE_SIZE, folders.size());
        for (int index = start; index < end; index++) {
            int slot = index - start;
            AutoBountyFolder folder = folders.get(index);
            inventory.setItem(slot, createFolderItem(folder));
            holder.folderSlots().put(slot, folder.threshold());
        }

        if (clampedPage > 0) {
            inventory.setItem(PREVIOUS_SLOT, GuiItems.namedItem(Material.ARROW, Component.text("Previous Page", NamedTextColor.YELLOW), List.of()));
        }
        inventory.setItem(TOGGLE_SLOT, createToggleItem(enabled));
        if (clampedPage < maxPage) {
            inventory.setItem(NEXT_SLOT, GuiItems.namedItem(Material.ARROW, Component.text("Next Page", NamedTextColor.YELLOW), List.of()));
        }

        admin.openInventory(inventory);
    }

    public void openEditor(Player admin, int threshold) {
        AutoBountyFolder folder;
        List<AutoBountyTemplate> templates;
        try {
            Optional<AutoBountyFolder> maybeFolder = bountyService.findAutoBountyFolder(threshold);
            if (maybeFolder.isEmpty()) {
                admin.sendMessage(Component.text("That automatic bounty folder no longer exists.", NamedTextColor.RED));
                openMain(admin, 0);
                return;
            }
            folder = maybeFolder.get();
            templates = bountyService.listAutoBountyTemplates(folder.id());
        } catch (SQLException exception) {
            admin.sendMessage(Component.text("Could not load that automatic bounty folder.", NamedTextColor.RED));
            Bukkit.getLogger().log(Level.SEVERE, "Failed to load auto bounty folder", exception);
            return;
        }

        AutoBountyMenuHolder holder = new AutoBountyMenuHolder(
            AutoBountyMenuHolder.Type.EDITOR,
            0,
            folder.id(),
            folder.threshold(),
            folder.name(),
            folder.protectedFolder()
        );
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Component.text("Auto: " + folder.name(), NamedTextColor.GOLD));
        holder.setInventory(inventory);

        for (AutoBountyTemplate template : templates) {
            if (template.slot() >= 0 && template.slot() < PAGE_SIZE) {
                inventory.setItem(template.slot(), createTemplateItem(template));
            }
        }

        inventory.setItem(BACK_SLOT, GuiItems.namedItem(Material.ARROW, Component.text("Back", NamedTextColor.YELLOW), List.of()));
        inventory.setItem(TOGGLE_SLOT, GuiItems.namedItem(Material.HOPPER, Component.text("Add templates", NamedTextColor.AQUA),
            List.of(
                Component.text("Click an item in your inventory", NamedTextColor.GRAY),
                Component.text("or click here with an item cursor.", NamedTextColor.GRAY),
                Component.text("Right-click a template to remove it.", NamedTextColor.GRAY)
            )));
        if (!folder.protectedFolder()) {
            inventory.setItem(DELETE_SLOT, GuiItems.namedItem(Material.BARRIER, Component.text("Delete Folder", NamedTextColor.RED),
                List.of(Component.text("Deletes this threshold and its templates.", NamedTextColor.GRAY))));
        }

        admin.openInventory(inventory);
    }

    public void handleClick(Player admin, AutoBountyMenuHolder holder, int rawSlot, ClickType clickType, ItemStack cursor, ItemStack currentItem, boolean topInventoryClick) {
        if (!admin.hasPermission("vanillabounties.admin")) {
            admin.closeInventory();
            return;
        }

        if (holder.type() == AutoBountyMenuHolder.Type.MAIN) {
            handleMainClick(admin, holder, rawSlot);
            return;
        }

        handleEditorClick(admin, holder, rawSlot, clickType, cursor, currentItem, topInventoryClick);
    }

    private void handleMainClick(Player admin, AutoBountyMenuHolder holder, int rawSlot) {
        if (rawSlot == PREVIOUS_SLOT && holder.page() > 0) {
            openMain(admin, holder.page() - 1);
            return;
        }
        if (rawSlot == NEXT_SLOT) {
            openMain(admin, holder.page() + 1);
            return;
        }
        if (rawSlot == TOGGLE_SLOT) {
            try {
                boolean enabled = bountyService.autoBountiesEnabled();
                bountyService.setAutoBountiesEnabled(!enabled);
                admin.sendMessage(Component.text("Automatic bounties " + (!enabled ? "enabled." : "disabled."), NamedTextColor.GREEN));
            } catch (SQLException exception) {
                admin.sendMessage(Component.text("Could not toggle automatic bounties.", NamedTextColor.RED));
            }
            openMain(admin, holder.page());
            return;
        }

        Integer threshold = holder.folderSlots().get(rawSlot);
        if (threshold != null) {
            openEditor(admin, threshold);
        }
    }

    private void handleEditorClick(Player admin, AutoBountyMenuHolder holder, int rawSlot, ClickType clickType, ItemStack cursor, ItemStack currentItem, boolean topInventoryClick) {
        if (topInventoryClick && rawSlot == BACK_SLOT) {
            openMain(admin, 0);
            return;
        }
        if (topInventoryClick && rawSlot == DELETE_SLOT && !holder.protectedFolder()) {
            try {
                if (bountyService.deleteAutoBountyThreshold(holder.threshold())) {
                    admin.sendMessage(Component.text("Deleted automatic bounty folder.", NamedTextColor.GREEN));
                    openMain(admin, 0);
                } else {
                    admin.sendMessage(Component.text("That folder cannot be deleted.", NamedTextColor.RED));
                    openEditor(admin, holder.threshold());
                }
            } catch (SQLException exception) {
                admin.sendMessage(Component.text("Could not delete automatic bounty folder.", NamedTextColor.RED));
            }
            return;
        }

        if (topInventoryClick && rawSlot >= 0 && rawSlot < PAGE_SIZE && clickType.isRightClick()) {
            try {
                if (bountyService.removeAutoBountyTemplate(holder.folderId(), rawSlot)) {
                    admin.sendMessage(Component.text("Removed template item.", NamedTextColor.GREEN));
                }
            } catch (SQLException exception) {
                admin.sendMessage(Component.text("Could not remove template item.", NamedTextColor.RED));
            }
            openEditor(admin, holder.threshold());
            return;
        }

        ItemStack sample = topInventoryClick ? usableItem(cursor) : usableItem(currentItem);
        if (sample == null) {
            return;
        }

        try {
            if (bountyService.addAutoBountyTemplate(holder.folderId(), sample)) {
                admin.sendMessage(Component.text("Added template item.", NamedTextColor.GREEN));
            } else {
                admin.sendMessage(Component.text("That folder is full.", NamedTextColor.RED));
            }
        } catch (SQLException exception) {
            admin.sendMessage(Component.text("Could not add template item.", NamedTextColor.RED));
        }
        openEditor(admin, holder.threshold());
    }

    private ItemStack usableItem(ItemStack item) {
        if (item != null && item.getType() != Material.AIR && item.getAmount() > 0) {
            return item.clone();
        }
        return null;
    }

    private ItemStack createFolderItem(AutoBountyFolder folder) {
        Material material = folder.protectedFolder() ? Material.NETHER_STAR : Material.CHEST;
        Component name = folder.onKill()
            ? Component.text("On kill", NamedTextColor.GREEN)
            : Component.text(folder.threshold() + " Kill Streak", NamedTextColor.YELLOW);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Templates: " + folder.templateCount(), NamedTextColor.GRAY));
        if (folder.onKill()) {
            lore.add(Component.text("Runs on every player kill.", NamedTextColor.GRAY));
            lore.add(Component.text("This folder cannot be deleted.", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text("Runs at exactly " + folder.threshold() + " kills.", NamedTextColor.GRAY));
        }
        lore.add(Component.text("Click to edit.", NamedTextColor.DARK_GRAY));
        return GuiItems.namedItem(material, name, lore);
    }

    private ItemStack createTemplateItem(AutoBountyTemplate template) {
        ItemStack item = template.item().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(GuiItems.emptyLine());
            lore.add(Component.text("Automatic bounty template", NamedTextColor.AQUA));
            lore.add(Component.text("Right-click to remove.", NamedTextColor.DARK_GRAY));
            GuiItems.lore(meta, lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createToggleItem(boolean enabled) {
        Material material = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        NamedTextColor color = enabled ? NamedTextColor.GREEN : NamedTextColor.RED;
        return GuiItems.namedItem(material, Component.text("Automatic Bounties: " + (enabled ? "Enabled" : "Disabled"), color),
            List.of(Component.text("Click to toggle all automatic bounty placement.", NamedTextColor.GRAY)));
    }
}
