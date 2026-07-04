package me.vanillabounties.model;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public record PluginSettings(
    boolean autoBountiesEnabled,
    boolean countNakedKills,
    long spawnKillPeriodMillis,
    boolean countRepeatKills,
    boolean allowSelfBounties,
    ItemStack trackingItem,
    long trackingPeriodMillis,
    long trackingGlowingDurationMillis,
    boolean trackingCompassEnabled,
    HuntHudMode huntHud,
    HuntHudMode huntWarningHud,
    boolean spookyHuntWarningsEnabled,
    long huntGracePeriodMillis,
    long huntRevealWarningMillis,
    long huntDurationMillis,
    boolean huntTimerBossBarEnabled
) {
    public boolean trackingEnabled() {
        return trackingItem != null && trackingItem.getType() != Material.AIR;
    }
}
