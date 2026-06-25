package me.vanillabounties.model;

import org.bukkit.Material;

public record PluginSettings(
    boolean autoBountiesEnabled,
    boolean countNakedKills,
    long spawnKillPeriodMillis,
    boolean countRepeatKills,
    boolean allowSelfBounties,
    Material trackingItem,
    long trackingPeriodMillis,
    long trackingGlowingDurationMillis,
    boolean trackingCompassEnabled,
    HuntHudMode huntHud
) {
    public boolean trackingEnabled() {
        return trackingItem != null && trackingItem != Material.AIR;
    }
}
