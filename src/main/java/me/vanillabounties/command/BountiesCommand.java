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

public final class BountiesCommand implements CommandExecutor, TabCompleter {
    private final BountyGui bountyGui;
    private final BountySettingsGui bountySettingsGui;

    public BountiesCommand(BountyGui bountyGui, BountySettingsGui bountySettingsGui) {
        this.bountyGui = bountyGui;
        this.bountySettingsGui = bountySettingsGui;
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

        bountyGui.openBoard(player, 0);
        return true;
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
