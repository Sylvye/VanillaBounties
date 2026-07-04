package me.vanillabounties.command;

import me.vanillabounties.gui.BountyGui;
import me.vanillabounties.gui.BountySettingsGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class BountiesCommand implements CommandExecutor, TabCompleter {
    private static final long MENU_COOLDOWN_MILLIS = 3_000L;

    private final BountyGui bountyGui;
    private final BountySettingsGui bountySettingsGui;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastMenuOpenAt = new ConcurrentHashMap<>();

    public BountiesCommand(BountyGui bountyGui, BountySettingsGui bountySettingsGui) {
        this(bountyGui, bountySettingsGui, System::currentTimeMillis);
    }

    BountiesCommand(BountyGui bountyGui, BountySettingsGui bountySettingsGui, LongSupplier clock) {
        this.bountyGui = bountyGui;
        this.bountySettingsGui = bountySettingsGui;
        this.clock = clock;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the bounty menu.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("settings")) {
            if (!player.hasPermission("vanillabounties.admin")) {
                player.sendMessage(Component.text("You do not have permission to manage bounty settings.", NamedTextColor.RED));
                return true;
            }
            bountySettingsGui.open(player);
            return true;
        }

        if (!player.hasPermission("vanillabounties.use")) {
            player.sendMessage(Component.text("You do not have permission to use bounties.", NamedTextColor.RED));
            return true;
        }

        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("menu"))) {
            player.sendMessage(Component.text("Usage: /bounties [menu|settings]", NamedTextColor.RED));
            return true;
        }

        long now = clock.getAsLong();
        Long lastOpenAt = lastMenuOpenAt.get(player.getUniqueId());
        long remaining = lastOpenAt == null ? 0L : MENU_COOLDOWN_MILLIS - (now - lastOpenAt);
        if (lastOpenAt != null && remaining > 0) {
            player.sendMessage(Component.text("Wait " + formatSeconds(remaining) + " before opening the bounty menu again.", NamedTextColor.YELLOW));
            return true;
        }
        lastMenuOpenAt.put(player.getUniqueId(), now);
        bountyGui.openBoard(player, 0);
        return true;
    }

    private String formatSeconds(long millis) {
        return Math.max(1L, (millis + 999L) / 1_000L) + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            if (sender.hasPermission("vanillabounties.use") && "menu".startsWith(prefix)) {
                if (sender.hasPermission("vanillabounties.admin") && "settings".startsWith(prefix)) {
                    return List.of("menu", "settings");
                }
                return List.of("menu");
            }
            if (!sender.hasPermission("vanillabounties.admin")) {
                return List.of();
            }
            if ("settings".startsWith(prefix)) {
                return List.of("settings");
            }
        }
        return List.of();
    }
}
