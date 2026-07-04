package me.vanillabounties.gui;

import me.vanillabounties.BountyService;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.KnownPlayer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public final class BountyConfirmationGui {
    private static final int INVENTORY_SIZE = 27;
    private static final int CONFIRM_SLOT = 11;
    private static final int PREVIEW_SLOT = 13;
    private static final int CANCEL_SLOT = 15;

    private final BountyService bountyService;

    public BountyConfirmationGui(BountyService bountyService) {
        this.bountyService = bountyService;
    }

    public void open(Player placer, KnownPlayer target, BountyVisibility visibility) {
        ItemStack hand = placer.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR || hand.getAmount() <= 0) {
            placer.sendMessage(Component.text("Hold the item stack you want to place as a bounty.", NamedTextColor.RED));
            return;
        }
        if (bountyService != null && bountyService.containsHuntCompass(hand)) {
            placer.sendMessage(Component.text("You cannot place a hunt compass as a bounty.", NamedTextColor.RED));
            return;
        }

        ItemStack previewItem = hand.clone();
        BountyConfirmationMenuHolder holder = new BountyConfirmationMenuHolder(placer.getUniqueId(), target, visibility, previewItem);
        Inventory inventory = Bukkit.createInventory(holder, INVENTORY_SIZE, Component.text("Confirm Bounty", NamedTextColor.GOLD));
        holder.setInventory(inventory);

        inventory.setItem(CONFIRM_SLOT, GuiItems.namedItem(Material.LIME_DYE, Component.text("Confirm Bounty", NamedTextColor.GREEN),
            List.of(Component.text("Places the previewed item stack.", NamedTextColor.GRAY))));
        inventory.setItem(PREVIEW_SLOT, previewItem(previewItem, target, visibility));
        inventory.setItem(CANCEL_SLOT, GuiItems.namedItem(Material.RED_DYE, Component.text("Cancel", NamedTextColor.RED),
            List.of(Component.text("Leaves your item in your inventory.", NamedTextColor.GRAY))));

        placer.openInventory(inventory);
    }

    public void handleClick(Player placer, BountyConfirmationMenuHolder holder, int rawSlot) {
        if (!placer.getUniqueId().equals(holder.viewerUuid())) {
            placer.closeInventory();
            return;
        }

        if (rawSlot == CANCEL_SLOT) {
            placer.closeInventory();
            placer.sendMessage(Component.text("Cancelled bounty placement.", NamedTextColor.YELLOW));
            return;
        }

        if (rawSlot != CONFIRM_SLOT) {
            return;
        }

        placer.closeInventory();
        BountyService.PlaceResult result = bountyService.placeConfirmedBounty(
            placer,
            holder.target(),
            holder.visibility(),
            holder.previewItem()
        );
        placer.sendMessage(result.message());
    }

    private ItemStack previewItem(ItemStack source, KnownPlayer target, BountyVisibility visibility) {
        ItemStack item = source.clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        lore.add(GuiItems.emptyLine());
        lore.add(Component.text("Target: " + target.name(), NamedTextColor.GRAY));
        lore.add(Component.text("Visibility: " + visibility.name().toLowerCase(), NamedTextColor.GRAY));
        lore.add(Component.text("Confirm to place this exact stack.", NamedTextColor.DARK_GRAY));
        GuiItems.lore(meta, lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}
