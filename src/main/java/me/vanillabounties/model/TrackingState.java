package me.vanillabounties.model;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record TrackingState(
    UUID targetUuid,
    String targetName,
    UUID enabledByUuid,
    String enabledByName,
    long enabledAt,
    @Nullable String worldName,
    int x,
    int y,
    int z,
    long lastRevealedAt,
    long warnedAt,
    long revealWarningSentFor,
    long huntStartedAt
) {
    public boolean hasLocation() {
        return worldName != null && !worldName.isBlank() && lastRevealedAt > 0;
    }
}
