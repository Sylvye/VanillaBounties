package me.vanillabounties.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class AutoBountyMenuHolder implements InventoryHolder {
    public enum Type {
        MAIN,
        EDITOR
    }

    private final Type type;
    private final int page;
    private final long folderId;
    private final int threshold;
    private final String folderName;
    private final boolean protectedFolder;
    private final Map<Integer, Integer> folderSlots = new HashMap<>();
    private Inventory inventory;

    public AutoBountyMenuHolder(Type type, int page, long folderId, int threshold, String folderName, boolean protectedFolder) {
        this.type = type;
        this.page = page;
        this.folderId = folderId;
        this.threshold = threshold;
        this.folderName = folderName;
        this.protectedFolder = protectedFolder;
    }

    public Type type() {
        return type;
    }

    public int page() {
        return page;
    }

    public long folderId() {
        return folderId;
    }

    public int threshold() {
        return threshold;
    }

    public String folderName() {
        return folderName;
    }

    public boolean protectedFolder() {
        return protectedFolder;
    }

    public Map<Integer, Integer> folderSlots() {
        return folderSlots;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
