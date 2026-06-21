package me.vanillabounties.model;

import java.util.UUID;

public record KnownPlayer(UUID uuid, String name, long lastSeenAt) {
}
