package me.vanillabounties.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class BountySettingsMenuHolder implements InventoryHolder {
    private Inventory inventory;

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
