package me.vanillabounties.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public record BountyReward(
    long id,
    UUID targetUuid,
    String targetName,
    UUID placerUuid,
    String placerName,
    ItemStack item,
    RewardState state
) {
}
