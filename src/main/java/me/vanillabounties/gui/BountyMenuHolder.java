package me.vanillabounties.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class BountyMenuHolder implements InventoryHolder {
    public enum Type {
        BOARD,
        DETAIL
    }

    private final Type type;
    private final UUID viewerUuid;
    private final int page;
    private final @Nullable UUID targetUuid;
    private final String targetName;
    private final Map<Integer, UUID> targetSlots = new HashMap<>();
    private Inventory inventory;

    public BountyMenuHolder(Type type, UUID viewerUuid, int page, @Nullable UUID targetUuid, String targetName) {
        this.type = type;
        this.viewerUuid = viewerUuid;
        this.page = page;
        this.targetUuid = targetUuid;
        this.targetName = targetName;
    }

    public Type type() {
        return type;
    }

    public UUID viewerUuid() {
        return viewerUuid;
    }

    public int page() {
        return page;
    }

    public @Nullable UUID targetUuid() {
        return targetUuid;
    }

    public String targetName() {
        return targetName;
    }

    public Map<Integer, UUID> targetSlots() {
        return targetSlots;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
