package com.xysov.novacontracts.contracts;

import java.util.List;

public class ContractDefinition {

    private final String id;
    private final String tier;
    private final int durationSeconds;
    private final List<ContractTask> tasks;

    public ContractDefinition(String id, String tier, int durationSeconds, List<ContractTask> tasks) {
        this.id = id;
        this.tier = tier;
        this.durationSeconds = durationSeconds;
        this.tasks = tasks;
    }

    public String getId() {
        return id;
    }

    public String getTier() {
        return tier;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public List<ContractTask> getTasks() {
        return tasks;
    }
}
