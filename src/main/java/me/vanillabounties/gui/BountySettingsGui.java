package me.vanillabounties.gui;

import me.vanillabounties.BountyService;
import me.vanillabounties.model.HuntHudMode;
import me.vanillabounties.model.PluginSettings;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class BountySettingsGui {
    private static final int INVENTORY_SIZE = 27;
    private static final int AUTO_BOUNTIES_SLOT = 0;
    private static final int COUNT_NAKED_KILLS_SLOT = 2;
    private static final int SPAWN_KILL_PERIOD_SLOT = 3;
    private static final int COUNT_REPEAT_KILLS_SLOT = 4;
    private static final int ALLOW_SELF_BOUNTIES_SLOT = 5;
    private static final int TRACKING_ITEM_SLOT = 11;
    private static final int TRACKING_PERIOD_SLOT = 12;
    private static final int TRACKING_GLOWING_SLOT = 13;
    private static final int TRACKING_COMPASS_SLOT = 14;
    private static final int HUNT_HUD_SLOT = 15;

    private final Plugin plugin;
    private final BountyService bountyService;
    private final Map<UUID, PendingDuration> pendingDurations = new ConcurrentHashMap<>();

    public BountySettingsGui(Plugin plugin, BountyService bountyService) {
        this.plugin = plugin;
        this.bountyService = bountyService;
    }

    public void open(Player admin) {
        if (!admin.hasPermission("vanillabounties.admin")) {
            admin.sendMessage(Component.text("You do not have permission to manage bounty settings.", NamedTextColor.RED));
            return;
        }

        PluginSettings settings;
        try {
            settings = bountyService.getPluginSettings();
        } catch (SQLException exception) {
            admin.sendMessage(Component.text("Could not load bounty settings.", NamedTextColor.RED));
            Bukkit.getLogger().log(Level.SEVERE, "Failed to load bounty settings", exception);
            return;
        }

        BountySettingsMenuHolder holder = new BountySettingsMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Component.text("Bounty Settings", NamedTextColor.GOLD));
        holder.setInventory(inventory);

        inventory.setItem(AUTO_BOUNTIES_SLOT, toggleItem("Automatic Bounties", settings.autoBountiesEnabled()));
        inventory.setItem(COUNT_NAKED_KILLS_SLOT, toggleItem("Count Naked Kills", settings.countNakedKills()));
        inventory.setItem(SPAWN_KILL_PERIOD_SLOT, durationItem("Spawn Kill Period", settings.spawnKillPeriodMillis()));
        inventory.setItem(COUNT_REPEAT_KILLS_SLOT, toggleItem("Count Repeat Kills", settings.countRepeatKills()));
        inventory.setItem(ALLOW_SELF_BOUNTIES_SLOT, toggleItem("Allow Self Bounties", settings.allowSelfBounties()));
        inventory.setItem(TRACKING_ITEM_SLOT, trackingItem(settings));
        inventory.setItem(TRACKING_PERIOD_SLOT, durationItem("Tracking Period", settings.trackingPeriodMillis()));
        inventory.setItem(TRACKING_GLOWING_SLOT, durationItem("Tracking Glowing Duration", settings.trackingGlowingDurationMillis()));
        inventory.setItem(TRACKING_COMPASS_SLOT, toggleItem("Tracking Compass", settings.trackingCompassEnabled()));
        inventory.setItem(HUNT_HUD_SLOT, namedItem(Material.COMPASS, Component.text("Hunt HUD: " + settings.huntHud().name(), NamedTextColor.AQUA),
            List.of(Component.text("Click to cycle action bar, chat, bossbar.", NamedTextColor.GRAY))));

        admin.openInventory(inventory);
    }

    public void handleClick(Player admin, int rawSlot, ClickType clickType, ItemStack cursor) {
        if (!admin.hasPermission("vanillabounties.admin")) {
            admin.closeInventory();
            return;
        }

        try {
            PluginSettings settings = bountyService.getPluginSettings();
            switch (rawSlot) {
                case AUTO_BOUNTIES_SLOT -> bountyService.setAutoBountiesEnabled(!settings.autoBountiesEnabled());
                case COUNT_NAKED_KILLS_SLOT -> bountyService.setCountNakedKills(!settings.countNakedKills());
                case SPAWN_KILL_PERIOD_SLOT -> promptDuration(admin, PendingDuration.SPAWN_KILL_PERIOD);
                case COUNT_REPEAT_KILLS_SLOT -> bountyService.setCountRepeatKills(!settings.countRepeatKills());
                case ALLOW_SELF_BOUNTIES_SLOT -> bountyService.setAllowSelfBounties(!settings.allowSelfBounties());
                case TRACKING_ITEM_SLOT -> setTrackingItem(admin, clickType, cursor);
                case TRACKING_PERIOD_SLOT -> promptDuration(admin, PendingDuration.TRACKING_PERIOD);
                case TRACKING_GLOWING_SLOT -> promptDuration(admin, PendingDuration.TRACKING_GLOWING);
                case TRACKING_COMPASS_SLOT -> bountyService.setTrackingCompassEnabled(!settings.trackingCompassEnabled());
                case HUNT_HUD_SLOT -> bountyService.setHuntHud(next(settings.huntHud()));
                default -> {
                    return;
                }
            }
        } catch (SQLException exception) {
            admin.sendMessage(Component.text("Could not update bounty settings.", NamedTextColor.RED));
            Bukkit.getLogger().log(Level.SEVERE, "Failed to update bounty setting", exception);
            return;
        }

        if (!pendingDurations.containsKey(admin.getUniqueId())) {
            open(admin);
        }
    }

    public boolean handleChat(Player admin, String message) {
        PendingDuration pending = pendingDurations.remove(admin.getUniqueId());
        if (pending == null) {
            return false;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (message.equalsIgnoreCase("cancel")) {
                admin.sendMessage(Component.text("Cancelled setting change.", NamedTextColor.YELLOW));
                open(admin);
                return;
            }

            Long millis = parseDurationMillis(message);
            if (millis == null) {
                admin.sendMessage(Component.text("Use a duration like 0s, 5s, 5m, 1h, or cancel.", NamedTextColor.RED));
                pendingDurations.put(admin.getUniqueId(), pending);
                return;
            }

            try {
                switch (pending) {
                    case SPAWN_KILL_PERIOD -> bountyService.setSpawnKillPeriodMillis(millis);
                    case TRACKING_PERIOD -> bountyService.setTrackingPeriodMillis(millis);
                    case TRACKING_GLOWING -> bountyService.setTrackingGlowingDurationMillis(millis);
                }
                admin.sendMessage(Component.text("Updated bounty setting.", NamedTextColor.GREEN));
                open(admin);
            } catch (SQLException exception) {
                admin.sendMessage(Component.text("Could not update bounty setting.", NamedTextColor.RED));
                Bukkit.getLogger().log(Level.SEVERE, "Failed to update duration setting", exception);
            }
        });
        return true;
    }

    private void setTrackingItem(Player admin, ClickType clickType, ItemStack cursor) throws SQLException {
        if (clickType.isRightClick()) {
            bountyService.setTrackingItem(Material.AIR);
            return;
        }
        if (cursor == null || cursor.getType() == Material.AIR || !cursor.getType().isItem()) {
            admin.sendMessage(Component.text("Click with an item cursor, or right-click to disable tracking.", NamedTextColor.RED));
            return;
        }
        bountyService.setTrackingItem(cursor.getType());
    }

    private void promptDuration(Player admin, PendingDuration pending) {
        pendingDurations.put(admin.getUniqueId(), pending);
        admin.closeInventory();
        admin.sendMessage(Component.text("Type a duration like 0s, 5s, 5m, 1h, or cancel.", NamedTextColor.YELLOW));
    }

    private HuntHudMode next(HuntHudMode mode) {
        return switch (mode) {
            case ACTION_BAR -> HuntHudMode.CHAT;
            case CHAT -> HuntHudMode.BOSSBAR;
            case BOSSBAR -> HuntHudMode.ACTION_BAR;
        };
    }

    private Long parseDurationMillis(String raw) {
        String value = raw.trim().toLowerCase();
        if (value.isBlank()) {
            return null;
        }

        long multiplier = 1L;
        if (value.endsWith("ms")) {
            value = value.substring(0, value.length() - 2);
        } else if (value.endsWith("s")) {
            multiplier = 1_000L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("m")) {
            multiplier = 60_000L;
            value = value.substring(0, value.length() - 1);
        } else if (value.endsWith("h")) {
            multiplier = 3_600_000L;
            value = value.substring(0, value.length() - 1);
        }

        try {
            long amount = Long.parseLong(value);
            if (amount < 0) {
                return null;
            }
            return amount * multiplier;
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private ItemStack toggleItem(String label, boolean enabled) {
        return namedItem(enabled ? Material.LIME_DYE : Material.GRAY_DYE,
            Component.text(label + ": " + (enabled ? "Enabled" : "Disabled"), enabled ? NamedTextColor.GREEN : NamedTextColor.RED),
            List.of(Component.text("Click to toggle.", NamedTextColor.GRAY)));
    }

    private ItemStack durationItem(String label, long millis) {
        return namedItem(Material.CLOCK, Component.text(label + ": " + formatDuration(millis), NamedTextColor.YELLOW),
            List.of(Component.text("Click to edit in chat.", NamedTextColor.GRAY)));
    }

    private ItemStack trackingItem(PluginSettings settings) {
        if (!settings.trackingEnabled()) {
            return namedItem(Material.BARRIER, Component.text("Tracking Item: None", NamedTextColor.RED),
                List.of(Component.text("Click with an item to set.", NamedTextColor.GRAY)));
        }
        return namedItem(settings.trackingItem(), Component.text("Tracking Item: " + settings.trackingItem().name(), NamedTextColor.AQUA),
            List.of(
                Component.text("Click with an item to set.", NamedTextColor.GRAY),
                Component.text("Right-click to disable tracking.", NamedTextColor.GRAY)
            ));
    }

    private String formatDuration(long millis) {
        if (millis % 3_600_000L == 0) {
            return (millis / 3_600_000L) + "h";
        }
        if (millis % 60_000L == 0) {
            return (millis / 60_000L) + "m";
        }
        if (millis % 1_000L == 0) {
            return (millis / 1_000L) + "s";
        }
        return millis + "ms";
    }

    private ItemStack namedItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name);
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private enum PendingDuration {
        SPAWN_KILL_PERIOD,
        TRACKING_PERIOD,
        TRACKING_GLOWING
    }
}
