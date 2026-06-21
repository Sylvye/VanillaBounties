package me.vanillabounties.command;

import me.vanillabounties.BountyService;
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

    public BountyCommand(BountyService bountyService) {
        this.bountyService = bountyService;
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

        if (args.length != 1) {
            player.sendMessage(Component.text("Usage: /bounty <player>", NamedTextColor.RED));
            return true;
        }

        Optional<KnownPlayer> target = bountyService.resolveKnownPlayer(args[0]);
        if (target.isEmpty()) {
            player.sendMessage(Component.text("That player is not online and has not joined before.", NamedTextColor.RED));
            return true;
        }

        BountyService.PlaceResult result = bountyService.placeBounty(player, target.get());
        player.sendMessage(result.message());
        return true;
    }

    private boolean handleClear(CommandSender sender, String[] args) {
        if (!sender.hasPermission("vanillabounties.admin")) {
            sender.sendMessage(Component.text("You do not have permission to clear bounties.", NamedTextColor.RED));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(Component.text("Usage: /bounty clear <all|player>", NamedTextColor.RED));
            return true;
        }

        try {
            if (args[1].equalsIgnoreCase("all")) {
                int cleared = bountyService.clearAllActiveBounties();
                sender.sendMessage(Component.text("Cleared " + cleared + " active bounty reward(s).", NamedTextColor.GREEN));
                return true;
            }

            Optional<KnownPlayer> target = bountyService.resolveKnownPlayer(args[1]);
            if (target.isEmpty()) {
                sender.sendMessage(Component.text("That player is not online and has not joined before.", NamedTextColor.RED));
                return true;
            }

            int cleared = bountyService.clearActiveBounties(target.get().uuid());
            sender.sendMessage(Component.text("Cleared " + cleared + " active bounty reward(s) for " + target.get().name() + ".", NamedTextColor.GREEN));
            return true;
        } catch (SQLException exception) {
            sender.sendMessage(Component.text("Could not clear active bounties.", NamedTextColor.RED));
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("vanillabounties.admin") && "clear".startsWith(args[0].toLowerCase(Locale.ROOT))) {
            return List.of("clear");
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

        if (args.length != 1 || !sender.hasPermission("vanillabounties.use")) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (String name : bountyService.searchKnownPlayerNames(prefix, 20)) {
            if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                names.add(name);
            }
        }
        return names;
    }
}
