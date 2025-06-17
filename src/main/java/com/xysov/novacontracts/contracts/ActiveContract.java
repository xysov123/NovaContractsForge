package com.xysov.novacontracts.contracts;

import java.util.List;
import java.util.UUID;

public class ActiveContract {

    private final UUID playerId;
    private final String tier;
    private final String contractId;
    private final long startTime;
    private final long duration; // in seconds
    private final List<ContractTask> tasks;

    public ActiveContract(UUID playerId, String tier, String contractId, long startTime, long duration, List<ContractTask> tasks) {
        this.playerId = playerId;
        this.tier = tier;
        this.contractId = contractId;
        this.startTime = startTime;
        this.duration = duration;
        this.tasks = tasks;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getTier() {
        return tier;
    }

    public String getContractId() {
        return contractId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getDuration() {
        return duration;
    }

    public List<ContractTask> getTasks() {
        return tasks;
    }

    public boolean isComplete() {
        return tasks.stream().allMatch(ContractTask::isComplete);
    }

    public long getTimeRemaining() {
        long end = startTime + (duration * 1000L);
        return Math.max(0, end - System.currentTimeMillis());
    }
}
