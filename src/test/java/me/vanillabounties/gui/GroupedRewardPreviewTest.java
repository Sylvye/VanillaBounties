package me.vanillabounties.gui;

import me.vanillabounties.BukkitTestSupport;
import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.RewardState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupedRewardPreviewTest extends BukkitTestSupport {
    @Test
    void mergesIdenticalStackableItemsAcrossPlacers() {
        BountyReward first = reward("Alice", new ItemStack(Material.GOLDEN_APPLE, 1), RewardState.ACTIVE);
        BountyReward second = reward("Server", new ItemStack(Material.GOLDEN_APPLE, 3), RewardState.ACTIVE);

        GroupedRewardPreview preview = GroupedRewardPreview.from(first);

        assertTrue(preview.canMerge(second));
        preview.merge(second);

        assertEquals(4, preview.totalAmount());
        assertEquals(2, preview.rewardCount());
        assertEquals("Multiple", preview.placerDisplay());
    }

    @Test
    void doesNotMergeDifferentStatesOrUnstackableItems() {
        GroupedRewardPreview activeSword = GroupedRewardPreview.from(reward("Alice", new ItemStack(Material.DIAMOND_SWORD, 1), RewardState.ACTIVE));

        assertFalse(activeSword.canMerge(reward("Alice", new ItemStack(Material.DIAMOND_SWORD, 1), RewardState.ACTIVE)));
        assertFalse(activeSword.canMerge(reward("Alice", new ItemStack(Material.DIAMOND_SWORD, 1), RewardState.CLAIMABLE)));
    }

    private BountyReward reward(String placerName, ItemStack item, RewardState state) {
        return new BountyReward(
            1L,
            UUID.randomUUID(),
            "Target",
            UUID.randomUUID(),
            placerName,
            item,
            state
        );
    }
}
