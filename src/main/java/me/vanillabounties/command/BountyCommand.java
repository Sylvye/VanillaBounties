package me.vanillabounties.command;

import me.vanillabounties.BountyService;
import me.vanillabounties.gui.BountyConfirmationGui;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.KnownPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BountyCommand implements CommandExecutor, TabCompleter {
    private final BountyService bountyService;
    private final BountyConfirmationGui bountyConfirmationGui;

    public BountyCommand(BountyService bountyService, BountyConfirmationGui bountyConfirmationGui) {
        this.bountyService = bountyService;
        this.bountyConfirmationGui = bountyConfirmationGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("clear")) {
            return handleClear(sender, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can place item bounties.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("vanillabounties.use")) {
            player.sendMessage(Component.text("You do not have permission to use bounties.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 1 || args.length > 2) {
            player.sendMessage(Component.text("Usage: /bounty <player> [silent|public]", NamedTextColor.RED));
            return true;
        }

        BountyVisibility visibility = BountyVisibility.NORMAL;
        if (args.length == 2) {
            if (args[1].equalsIgnoreCase("silent")) {
                visibility = BountyVisibility.SILENT;
            } else if (args[1].equalsIgnoreCase("public")) {
                visibility = BountyVisibility.PUBLIC;
            } else {
                player.sendMessage(Component.text("Usage: /bounty <player> [silent|public]", NamedTextColor.RED));
                return true;
            }
        }

        if (args[0].equalsIgnoreCase("clear")) {
            player.sendMessage(Component.text("Usage: /bounty clear <all|player> [refund|delete]", NamedTextColor.RED));
            return true;
        }

        Optional<KnownPlayer> target = bountyService.resolveKnownPlayer(args[0]);
        if (target.isEmpty()) {
            player.sendMessage(Component.text("That player is not online and has not joined before.", NamedTextColor.RED));
            return true;
        }

        bountyConfirmationGui.open(player, target.get(), visibility);
        return true;
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vanillabounties.admin")) {
            sender.sendMessage(Component.text("You do not have permission to clear bounties.", NamedTextColor.RED));
            return true;
        }

        if (args.length < 2 || args.length > 3) {
            sender.sendMessage(Component.text("Usage: /bounty clear <all|player> [refund|delete]", NamedTextColor.RED));
            return true;
        }

        BountyService.ClearMode mode = BountyService.ClearMode.DELETE;
        if (args.length == 3) {
            if (args[2].equalsIgnoreCase("refund")) {
                mode = BountyService.ClearMode.REFUND;
            } else if (!args[2].equalsIgnoreCase("delete")) {
                sender.sendMessage(Component.text("Usage: /bounty clear <all|player> [refund|delete]", NamedTextColor.RED));
                return true;
            }
        }

        try {
            BountyService.ClearResult result;
            if (args[1].equalsIgnoreCase("all")) {
                result = bountyService.clearAllActiveBounties(mode);
                sendClearResult(sender, result, null);
                return true;
            }

            Optional<KnownPlayer> target = bountyService.resolveKnownPlayer(args[1]);
            if (target.isEmpty()) {
                sender.sendMessage(Component.text("That player is not online and has not joined before.", NamedTextColor.RED));
                return true;
            }

            result = bountyService.clearActiveBounties(target.get().uuid(), mode);
            sendClearResult(sender, result, target.get().name());
            return true;
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Could not clear active bounties.", NamedTextColor.RED));
            return true;
        }
    }

    private void sendClearResult(CommandSender sender, BountyService.ClearResult result, String targetName) {
        if (!result.unavailablePlacers().isEmpty()) {
            sender.sendMessage(Component.text(
                "Could not refund because these bounty placers are offline: " + String.join(", ", result.unavailablePlacers()) + ". Use delete to remove permanently.",
                NamedTextColor.RED
            ));
            return;
        }

        String targetSuffix = targetName == null ? "" : " for " + targetName;
        if (result.refunded() > 0) {
            sender.sendMessage(Component.text(
                "Cleared " + result.cleared() + " active bounty reward(s)" + targetSuffix + " and refunded " + result.refunded() + ".",
                NamedTextColor.GREEN
            ));
            return;
        }

        sender.sendMessage(Component.text("Cleared " + result.cleared() + " active bounty reward(s)" + targetSuffix + ".", NamedTextColor.GREEN));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("vanillabounties.admin") && "clear".startsWith(prefix)) {
                completions.add("clear");
            }
            if (sender.hasPermission("vanillabounties.use")) {
                for (String name : bountyService.searchKnownPlayerNames(prefix, 20)) {
                    if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                        completions.add(name);
                    }
                }
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("clear") && sender.hasPermission("vanillabounties.admin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> completions = new ArrayList<>();
            if ("all".startsWith(prefix)) {
                completions.add("all");
            }
            for (String name : bountyService.searchKnownPlayerNames(prefix, 20)) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    completions.add(name);
                }
            }
            return completions;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("clear") && sender.hasPermission("vanillabounties.admin")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            List<String> modes = new ArrayList<>();
            if ("refund".startsWith(prefix)) {
                modes.add("refund");
            }
            if ("delete".startsWith(prefix)) {
                modes.add("delete");
            }
            return modes;
        }

        if (args.length == 2 && sender.hasPermission("vanillabounties.use")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> modes = new ArrayList<>();
            if ("silent".startsWith(prefix)) {
                modes.add("silent");
            }
            if ("public".startsWith(prefix)) {
                modes.add("public");
            }
            return modes;
        }

        return List.of();
    }
}
