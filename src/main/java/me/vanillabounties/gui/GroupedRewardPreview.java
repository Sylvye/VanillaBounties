package me.vanillabounties.gui;

import me.vanillabounties.model.BountyReward;
import me.vanillabounties.model.BountyVisibility;
import me.vanillabounties.model.RewardState;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.List;

public final class GroupedRewardPreview {
    private static final String SILENT_PLACER_KEY = "silent:XXXXXXX";

    private final ItemStack displayItem;
    private final RewardState state;
    private final Set<String> placerDisplayKeys = new LinkedHashSet<>();
    private final List<Long> claimableRewardIds = new ArrayList<>();
    private int totalAmount;
    private int rewardCount;

    private GroupedRewardPreview(BountyReward reward) {
        this.displayItem = reward.item().clone();
        this.state = reward.state();
        this.placerDisplayKeys.add(placerDisplayKey(reward));
        if (reward.state() == RewardState.CLAIMABLE) {
            this.claimableRewardIds.add(reward.id());
        }
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
        placerDisplayKeys.add(placerDisplayKey(reward));
        if (reward.state() == RewardState.CLAIMABLE) {
            claimableRewardIds.add(reward.id());
        }
        displayItem.setAmount(Math.min(displayItem.getMaxStackSize(), Math.max(1, totalAmount)));
    }

    public ItemStack displayItem() {
        return displayItem.clone();
    }

    public RewardState state() {
        return state;
    }

    public Component placerDisplay() {
        if (placerDisplayKeys.size() != 1) {
            return Component.text("Multiple", NamedTextColor.GRAY);
        }

        String key = placerDisplayKeys.iterator().next();
        if (SILENT_PLACER_KEY.equals(key)) {
            return Component.text("XXXXXXX", NamedTextColor.DARK_GRAY, TextDecoration.OBFUSCATED);
        }
        return Component.text(key.substring("name:".length()), NamedTextColor.GRAY);
    }

    public int totalAmount() {
        return totalAmount;
    }

    public int rewardCount() {
        return rewardCount;
    }

    public List<Long> claimableRewardIds() {
        return List.copyOf(claimableRewardIds);
    }

    private String placerDisplayKey(BountyReward reward) {
        if (reward.visibility() == BountyVisibility.SILENT) {
            return SILENT_PLACER_KEY;
        }
        return "name:" + reward.placerName();
    }
}
