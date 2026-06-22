package me.vanillabounties.gui;

import me.vanillabounties.BukkitTestSupport;
import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.RewardState;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupedRewardPreviewTest extends BukkitTestSupport {
    @Test
    void mergesIdenticalStackableItemsAcrossPlacers() {
        BountyReward first = reward(10L, "Alice", new ItemStack(Material.GOLDEN_APPLE, 1), RewardState.CLAIMABLE);
        BountyReward second = reward(11L, "Server", new ItemStack(Material.GOLDEN_APPLE, 3), RewardState.CLAIMABLE);

        GroupedRewardPreview preview = GroupedRewardPreview.from(first);

        assertTrue(preview.canMerge(second));
        preview.merge(second);

        assertEquals(4, preview.totalAmount());
        assertEquals(2, preview.rewardCount());
        assertEquals("Multiple", PlainTextComponentSerializer.plainText().serialize(preview.placerDisplay()));
        assertEquals(List.of(10L, 11L), preview.claimableRewardIds());
    }

    @Test
    void doesNotMergeDifferentStatesOrUnstackableItems() {
        GroupedRewardPreview activeSword = GroupedRewardPreview.from(reward("Alice", new ItemStack(Material.DIAMOND_SWORD, 1), RewardState.ACTIVE));

        assertFalse(activeSword.canMerge(reward("Alice", new ItemStack(Material.DIAMOND_SWORD, 1), RewardState.ACTIVE)));
        assertFalse(activeSword.canMerge(reward("Alice", new ItemStack(Material.DIAMOND_SWORD, 1), RewardState.CLAIMABLE)));
    }

    @Test
    void silentPlacersRenderAsPlaceholder() {
        GroupedRewardPreview preview = GroupedRewardPreview.from(reward("Alice", new ItemStack(Material.GOLDEN_APPLE, 1), RewardState.ACTIVE, BountyVisibility.SILENT));

        assertEquals("XXXXXXX", PlainTextComponentSerializer.plainText().serialize(preview.placerDisplay()));
    }

    private BountyReward reward(String placerName, ItemStack item, RewardState state) {
        return reward(1L, placerName, item, state, BountyVisibility.NORMAL);
    }

    private BountyReward reward(String placerName, ItemStack item, RewardState state, BountyVisibility visibility) {
        return reward(1L, placerName, item, state, visibility);
    }

    private BountyReward reward(long id, String placerName, ItemStack item, RewardState state) {
        return reward(id, placerName, item, state, BountyVisibility.NORMAL);
    }

    private BountyReward reward(long id, String placerName, ItemStack item, RewardState state, BountyVisibility visibility) {
        return new BountyReward(
            id,
            UUID.randomUUID(),
            "Target",
            UUID.randomUUID(),
            placerName,
            item,
            state,
            visibility
        );
    }
}
