package me.vanillabounties.listener;

import me.vanillabounties.BountyService;
import me.vanillabounties.gui.AutoBountyGui;
import me.vanillabounties.gui.AutoBountyMenuHolder;
import me.vanillabounties.gui.BountyGui;
import me.vanillabounties.gui.BountyMenuHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;

public final class BountyListener implements Listener {
    private final BountyService bountyService;
    private final BountyGui bountyGui;
    private final AutoBountyGui autoBountyGui;

    public BountyListener(BountyService bountyService, BountyGui bountyGui, AutoBountyGui autoBountyGui) {
        this.bountyService = bountyService;
        this.bountyGui = bountyGui;
        this.autoBountyGui = autoBountyGui;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        bountyService.recordKnownPlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        bountyService.handlePlayerKill(event.getEntity(), killer);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof BountyMenuHolder bountyMenuHolder)) {
            if (holder instanceof AutoBountyMenuHolder autoBountyMenuHolder) {
                event.setCancelled(true);
                if (!(event.getWhoClicked() instanceof Player player)) {
                    return;
                }
                autoBountyGui.handleClick(
                    player,
                    autoBountyMenuHolder,
                    event.getRawSlot(),
                    event.getClick(),
                    event.getCursor(),
                    event.getCurrentItem(),
                    event.getClickedInventory() == event.getView().getTopInventory()
                );
            }
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        bountyGui.handleClick(player, bountyMenuHolder, event.getRawSlot());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BountyMenuHolder) {
            event.setCancelled(true);
        }
        if (event.getView().getTopInventory().getHolder() instanceof AutoBountyMenuHolder) {
            event.setCancelled(true);
        }
    }
}
