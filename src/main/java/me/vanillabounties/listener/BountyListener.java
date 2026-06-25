package me.vanillabounties.listener;

import me.vanillabounties.BountyService;
import me.vanillabounties.gui.AutoBountyGui;
import me.vanillabounties.gui.AutoBountyMenuHolder;
import me.vanillabounties.gui.BountyConfirmationGui;
import me.vanillabounties.gui.BountyConfirmationMenuHolder;
import me.vanillabounties.gui.BountyGui;
import me.vanillabounties.gui.BountyMenuHolder;
import me.vanillabounties.gui.BountySettingsGui;
import me.vanillabounties.gui.BountySettingsMenuHolder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.InventoryHolder;

public final class BountyListener implements Listener {
    private final BountyService bountyService;
    private final BountyConfirmationGui bountyConfirmationGui;
    private final BountyGui bountyGui;
    private final AutoBountyGui autoBountyGui;
    private final BountySettingsGui bountySettingsGui;

    public BountyListener(BountyService bountyService, BountyConfirmationGui bountyConfirmationGui, BountyGui bountyGui, AutoBountyGui autoBountyGui, BountySettingsGui bountySettingsGui) {
        this.bountyService = bountyService;
        this.bountyConfirmationGui = bountyConfirmationGui;
        this.bountyGui = bountyGui;
        this.autoBountyGui = autoBountyGui;
        this.bountySettingsGui = bountySettingsGui;
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
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        bountyService.recordRespawn(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        bountyService.stopHunt(event.getPlayer());
    }

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (bountySettingsGui.handleChat(event.getPlayer(), message)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof BountyMenuHolder bountyMenuHolder)) {
            if (holder instanceof BountyConfirmationMenuHolder bountyConfirmationMenuHolder) {
                event.setCancelled(true);
                if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
                    return;
                }
                if (!(event.getWhoClicked() instanceof Player player)) {
                    return;
                }
                bountyConfirmationGui.handleClick(player, bountyConfirmationMenuHolder, event.getRawSlot());
            }
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
            if (holder instanceof BountySettingsMenuHolder) {
                event.setCancelled(true);
                if (!(event.getWhoClicked() instanceof Player player)) {
                    return;
                }
                bountySettingsGui.handleClick(
                    player,
                    event.getRawSlot(),
                    event.getClick(),
                    event.getCursor()
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
        if (event.getView().getTopInventory().getHolder() instanceof BountyConfirmationMenuHolder) {
            event.setCancelled(true);
        }
        if (event.getView().getTopInventory().getHolder() instanceof AutoBountyMenuHolder) {
            event.setCancelled(true);
        }
        if (event.getView().getTopInventory().getHolder() instanceof BountySettingsMenuHolder) {
            event.setCancelled(true);
        }
    }
}
