package me.vanillabounties.gui;

import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.KnownPlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public final class BountyConfirmationMenuHolder implements InventoryHolder {
    private final UUID viewerUuid;
    private final KnownPlayer target;
    private final BountyVisibility visibility;
    private final ItemStack previewItem;
    private Inventory inventory;

    public BountyConfirmationMenuHolder(UUID viewerUuid, KnownPlayer target, BountyVisibility visibility, ItemStack previewItem) {
        this.viewerUuid = viewerUuid;
        this.target = target;
        this.visibility = visibility;
        this.previewItem = previewItem.clone();
    }

    public UUID viewerUuid() {
        return viewerUuid;
    }

    public KnownPlayer target() {
        return target;
    }

    public BountyVisibility visibility() {
        return visibility;
    }

    public ItemStack previewItem() {
        return previewItem.clone();
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
