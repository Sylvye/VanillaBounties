package me.vanillabounties.command;

import me.vanillabounties.gui.BountyGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class BountiesCommand implements CommandExecutor {
    private final BountyGui bountyGui;

    public BountiesCommand(BountyGui bountyGui) {
        this.bountyGui = bountyGui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can open the bounty menu.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("vanillabounties.use")) {
            player.sendMessage(Component.text("You do not have permission to use bounties.", NamedTextColor.RED));
            return true;
        }

        bountyGui.openBoard(player, 0);
        return true;
    }
}
