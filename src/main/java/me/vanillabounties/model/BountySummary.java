package me.vanillabounties.model;

import java.util.UUID;

public record BountySummary(
    UUID targetUuid,
    String targetName,
    int activeRewardCount,
    int claimableRewardCount,
    int killStreak
) {
    public boolean hasClaimableRewards() {
        return claimableRewardCount > 0;
    }

    public int visibleRewardCount() {
        return activeRewardCount + claimableRewardCount;
    }
}
