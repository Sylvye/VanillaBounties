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
import io.papermc.paper.event.player.AsyncChatEvent;
import io.papermc.paper.event.player.PlayerTradeEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

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
        event.getDrops().removeIf(bountyService::containsHuntCompass);
        if (bountyService.isHunting(event.getEntity()) || bountyService.hasHuntCompass(event.getEntity())) {
            bountyService.stopHunt(event.getEntity());
        }

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
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!bountyService.containsHuntCompass(event.getItemDrop().getItemStack())) {
            return;
        }
        event.setCancelled(true);
        event.getItemDrop().remove();
        bountyService.removeHuntCompasses(event.getPlayer());
        bountyService.stopHuntForDroppedCompass(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && protectHuntCompassClick(event, player)) {
            return;
        }

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
                boolean topInventoryClick = event.getClickedInventory() == event.getView().getTopInventory();
                if (topInventoryClick) {
                    event.setCancelled(true);
                }
                if (!(event.getWhoClicked() instanceof Player player)) {
                    return;
                }
                if (!topInventoryClick) {
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
        if (bountyService.containsHuntCompass(event.getOldCursor()) && hasExternalTopInventory(event.getView())) {
            int topInventorySize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topInventorySize)) {
                event.setCancelled(true);
                return;
            }
        }

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
            int topInventorySize = event.getView().getTopInventory().getSize();
            if (event.getRawSlots().stream().anyMatch(slot -> slot < topInventorySize)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !bountyService.containsHuntCompass(player.getItemOnCursor())) {
            return;
        }
        player.setItemOnCursor(null);
        bountyService.stopHuntForDroppedCompass(player);
    }

    @EventHandler
    public void onPlayerTrade(PlayerTradeEvent event) {
        Inventory topInventory = event.getPlayer().getOpenInventory().getTopInventory();
        if (inventoryContainsHuntCompass(topInventory)) {
            event.setCancelled(true);
        }
    }

    boolean protectHuntCompassClick(InventoryClickEvent event, Player player) {
        if (isCursorDrop(event.getAction()) && bountyService.containsHuntCompass(event.getCursor())) {
            event.setCancelled(true);
            event.getView().setCursor(null);
            bountyService.stopHuntForDroppedCompass(player);
            return true;
        }

        if (isSlotDrop(event.getAction()) && bountyService.containsHuntCompass(event.getCurrentItem())) {
            event.setCancelled(true);
            event.setCurrentItem(null);
            bountyService.stopHuntForDroppedCompass(player);
            return true;
        }

        if (event instanceof InventoryCreativeEvent
            && (bountyService.containsHuntCompass(event.getCursor()) || bountyService.containsHuntCompass(event.getCurrentItem()))) {
            event.setCancelled(true);
            return true;
        }

        if (isBundleAction(event.getAction())
            && (bountyService.containsHuntCompass(event.getCursor()) || bountyService.containsHuntCompass(event.getCurrentItem()))) {
            event.setCancelled(true);
            return true;
        }

        if (!hasExternalTopInventory(event.getView())) {
            return false;
        }

        if (event.getView().getType() == InventoryType.MERCHANT && inventoryContainsHuntCompass(event.getView().getTopInventory())) {
            event.setCancelled(true);
            return true;
        }

        if (movesHuntCompassToExternalInventory(event, player)) {
            event.setCancelled(true);
            return true;
        }

        return false;
    }

    private boolean movesHuntCompassToExternalInventory(InventoryClickEvent event, Player player) {
        boolean topClick = event.getClickedInventory() != null && event.getClickedInventory() == event.getView().getTopInventory();
        boolean bottomClick = event.getClickedInventory() != null && event.getClickedInventory() == event.getView().getBottomInventory();

        if (topClick && protectedItemInvolved(event, player)) {
            return true;
        }

        if (!bottomClick) {
            return false;
        }

        return switch (event.getAction()) {
            case MOVE_TO_OTHER_INVENTORY, HOTBAR_MOVE_AND_READD, HOTBAR_SWAP, COLLECT_TO_CURSOR, UNKNOWN ->
                protectedItemInvolved(event, player);
            default -> false;
        };
    }

    private boolean protectedItemInvolved(InventoryClickEvent event, Player player) {
        return bountyService.containsHuntCompass(event.getCurrentItem())
            || bountyService.containsHuntCompass(event.getCursor())
            || bountyService.containsHuntCompass(hotbarItem(event, player))
            || bountyService.containsHuntCompass(player.getInventory().getItemInOffHand());
    }

    private ItemStack hotbarItem(InventoryClickEvent event, Player player) {
        int hotbarButton = event.getHotbarButton();
        if (hotbarButton < 0 || hotbarButton > 8) {
            return null;
        }
        return player.getInventory().getItem(hotbarButton);
    }

    private boolean inventoryContainsHuntCompass(Inventory inventory) {
        for (ItemStack item : inventory.getContents()) {
            if (bountyService.containsHuntCompass(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasExternalTopInventory(InventoryView view) {
        InventoryType type = view.getTopInventory().getType();
        return type != InventoryType.CRAFTING && type != InventoryType.PLAYER && type != InventoryType.CREATIVE;
    }

    private boolean isCursorDrop(InventoryAction action) {
        return action == InventoryAction.DROP_ALL_CURSOR || action == InventoryAction.DROP_ONE_CURSOR;
    }

    private boolean isSlotDrop(InventoryAction action) {
        return action == InventoryAction.DROP_ALL_SLOT || action == InventoryAction.DROP_ONE_SLOT;
    }

    private boolean isBundleAction(InventoryAction action) {
        return action == InventoryAction.PICKUP_FROM_BUNDLE
            || action == InventoryAction.PICKUP_ALL_INTO_BUNDLE
            || action == InventoryAction.PICKUP_SOME_INTO_BUNDLE
            || action == InventoryAction.PLACE_FROM_BUNDLE
            || action == InventoryAction.PLACE_ALL_INTO_BUNDLE
            || action == InventoryAction.PLACE_SOME_INTO_BUNDLE;
    }
}
