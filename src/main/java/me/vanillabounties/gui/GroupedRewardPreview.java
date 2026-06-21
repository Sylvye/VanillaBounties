package me.vanillabounties.gui;

import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.RewardState;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashSet;
import java.util.Set;

public final class GroupedRewardPreview {
    private final ItemStack displayItem;
    private final RewardState state;
    private final Set<String> placerNames = new LinkedHashSet<>();
    private int totalAmount;
    private int rewardCount;

    private GroupedRewardPreview(BountyReward reward) {
        this.displayItem = reward.item().clone();
        this.state = reward.state();
        this.placerNames.add(reward.placerName());
        this.totalAmount = reward.item().getAmount();
        this.rewardCount = 1;
        this.displayItem.setAmount(Math.min(displayItem.getMaxStackSize(), Math.max(1, totalAmount)));
    }

    public static GroupedRewardPreview from(BountyReward reward) {
        return new GroupedRewardPreview(reward);
    }

    public boolean canMerge(BountyReward reward) {
        return reward.state() == state
            && displayItem.isSimilar(reward.item())
            && reward.item().getMaxStackSize() > 1;
    }

    public void merge(BountyReward reward) {
        totalAmount += reward.item().getAmount();
        rewardCount++;
        placerNames.add(reward.placerName());
        displayItem.setAmount(Math.min(displayItem.getMaxStackSize(), Math.max(1, totalAmount)));
    }

    public ItemStack displayItem() {
        return displayItem.clone();
    }

    public RewardState state() {
        return state;
    }

    public String placerDisplay() {
        if (placerNames.size() == 1) {
            return placerNames.iterator().next();
        }
        return "Multiple";
    }

    public int totalAmount() {
        return totalAmount;
    }

    public int rewardCount() {
        return rewardCount;
    }
}
