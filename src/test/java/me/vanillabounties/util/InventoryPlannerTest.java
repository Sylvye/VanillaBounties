package me.vanillabounties.util;

import me.vanillabounties.BukkitTestSupport;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryPlannerTest extends BukkitTestSupport {
    @Test
    void mergesIntoPartialStacksBeforeUsingEmptySlots() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.DIAMOND, 60);
        contents[1] = new ItemStack(Material.STONE, 64);

        ItemStack reward = new ItemStack(Material.DIAMOND, 10);

        ItemStack[] planned = InventoryPlanner.planStorageContents(contents, List.of(reward)).orElseThrow();

        assertEquals(64, planned[0].getAmount());
        assertEquals(Material.DIAMOND, planned[2].getType());
        assertEquals(6, planned[2].getAmount());
    }

    @Test
    void rejectsWhenEveryStorageSlotIsFull() {
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = new ItemStack(Material.STONE, 64);
        }

        assertTrue(InventoryPlanner.planStorageContents(contents, List.of(new ItemStack(Material.DIAMOND, 1))).isEmpty());
    }

    @Test
    void splitsOversizedRewardsAcrossSlots() {
        ItemStack[] contents = new ItemStack[36];
        ItemStack reward = new ItemStack(Material.DIRT, 99);

        ItemStack[] planned = InventoryPlanner.planStorageContents(contents, List.of(reward)).orElseThrow();

        assertEquals(64, planned[0].getAmount());
        assertEquals(35, planned[1].getAmount());
    }

    @Test
    void doesNotMutateOriginalContents() {
        ItemStack[] contents = new ItemStack[36];
        contents[0] = new ItemStack(Material.EMERALD, 60);

        InventoryPlanner.planStorageContents(contents, List.of(new ItemStack(Material.EMERALD, 4))).orElseThrow();

        assertEquals(60, contents[0].getAmount());
    }
}
