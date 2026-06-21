package me.vanillabounties.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;

public final class InventoryPlanner {
    private InventoryPlanner() {
    }

    public static Optional<ItemStack[]> planStorageContents(ItemStack[] currentStorageContents, List<ItemStack> rewards) {
        ItemStack[] planned = cloneContents(currentStorageContents);

        for (ItemStack reward : rewards) {
            ItemStack remaining = reward.clone();

            for (int slot = 0; slot < planned.length && remaining.getAmount() > 0; slot++) {
                ItemStack existing = planned[slot];
                if (existing == null || existing.getType() == Material.AIR || !existing.isSimilar(remaining)) {
                    continue;
                }

                int capacity = Math.min(existing.getMaxStackSize(), existing.getType().getMaxStackSize()) - existing.getAmount();
                if (capacity <= 0) {
                    continue;
                }

                int moved = Math.min(capacity, remaining.getAmount());
                existing.setAmount(existing.getAmount() + moved);
                remaining.setAmount(remaining.getAmount() - moved);
            }

            for (int slot = 0; slot < planned.length && remaining.getAmount() > 0; slot++) {
                ItemStack existing = planned[slot];
                if (existing != null && existing.getType() != Material.AIR) {
                    continue;
                }

                int moved = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
                ItemStack placed = remaining.clone();
                placed.setAmount(moved);
                planned[slot] = placed;
                remaining.setAmount(remaining.getAmount() - moved);
            }

            if (remaining.getAmount() > 0) {
                return Optional.empty();
            }
        }

        return Optional.of(planned);
    }

    private static ItemStack[] cloneContents(ItemStack[] contents) {
        ItemStack[] clone = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            clone[i] = contents[i] == null ? null : contents[i].clone();
        }
        return clone;
    }
}
