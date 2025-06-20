package com.xysov.novacontracts.utils;

import java.util.UUID;

public class LeaderboardEntry {
    private final UUID uuid;
    private final int reputation;

    public LeaderboardEntry(UUID uuid, int reputation) {
        this.uuid = uuid;
        this.reputation = reputation;
    }

    public UUID getUuid() {
        return uuid;
    }

    public int getReputation() {
        return reputation;
    }
}