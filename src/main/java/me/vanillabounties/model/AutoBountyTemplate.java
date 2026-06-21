package me.vanillabounties.model;

import org.bukkit.inventory.ItemStack;

public record AutoBountyTemplate(
    long id,
    long folderId,
    int slot,
    ItemStack item
) {
}
