package me.vanillabounties.command;

import me.vanillabounties.BountyService;
import me.vanillabounties.gui.AutoBountyGui;
import me.vanillabounties.storage.BountyDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.List;

public final class AutoBountiesCommand implements CommandExecutor, TabCompleter {
    private final BountyService bountyService;
    private final AutoBountyGui autoBountyGui;

    public AutoBountiesCommand(BountyService bountyService, AutoBountyGui autoBountyGui) {
        this.bountyService = bountyService;
        this.autoBountyGui = autoBountyGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vanillabounties.admin")) {
            sender.sendMessage(Component.text("You do not have permission to manage automatic bounties.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("Usage: /autobounties add <kills> or /autobounties remove <kills>", NamedTextColor.RED));
                return true;
            }
            autoBountyGui.openMain(player, 0);
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            return addThreshold(sender, args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return removeThreshold(sender, args[1]);
        }

        sender.sendMessage(Component.text("Usage: /autobounties [add <kills>|remove <kills>]", NamedTextColor.RED));
        return true;
    }

    private boolean addThreshold(CommandSender sender, String rawKills) {
        Integer kills = parseKills(sender, rawKills);
        if (kills == null) {
            return true;
        }

        try {
            BountyDatabase.CreateFolderResult result = bountyService.createAutoBountyThreshold(kills);
            switch (result) {
                case CREATED -> {
                    sender.sendMessage(Component.text("Created automatic bounty folder for " + kills + " kills.", NamedTextColor.GREEN));
                    if (sender instanceof Player player) {
                        autoBountyGui.openEditor(player, kills);
                    }
                }
                case DUPLICATE -> sender.sendMessage(Component.text("A folder for that kill streak already exists.", NamedTextColor.RED));
                case INVALID -> sender.sendMessage(Component.text("Kill streak thresholds must be 1 or higher.", NamedTextColor.RED));
            }
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Could not create automatic bounty folder.", NamedTextColor.RED));
        }
        return true;
    }

    private boolean removeThreshold(CommandSender sender, String rawKills) {
        Integer kills = parseKills(sender, rawKills);
        if (kills == null) {
            return true;
        }

        try {
            if (bountyService.deleteAutoBountyThreshold(kills)) {
                sender.sendMessage(Component.text("Removed automatic bounty folder for " + kills + " kills.", NamedTextColor.GREEN));
            } else {
                sender.sendMessage(Component.text("That automatic bounty folder does not exist or cannot be deleted.", NamedTextColor.RED));
            }
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Could not remove automatic bounty folder.", NamedTextColor.RED));
        }
        return true;
    }

    private Integer parseKills(CommandSender sender, String rawKills) {
        try {
            int kills = Integer.parseInt(rawKills);
            if (kills < 1) {
                sender.sendMessage(Component.text("Use 1 or higher. The On kill folder covers every kill.", NamedTextColor.RED));
                return null;
            }
            return kills;
        } catch (NumberFormatException exception) {
            sender.sendMessage(Component.text("Kill streak threshold must be a whole number.", NamedTextColor.RED));
            return null;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vanillabounties.admin")) {
            return List.of();
        }
        if (args.length == 1) {
            return List.of("add", "remove");
        }
        return List.of();
    }
}
