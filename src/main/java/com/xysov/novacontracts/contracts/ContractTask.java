package com.xysov.novacontracts.contracts;

import java.util.List;

public class ContractTask {

    private final TaskType type;
    private final String specific;           // e.g. "DIAMOND_PICKAXE"
    private final List<String> listSpecific; // e.g. ["DIAMOND", "EMERALD"]
    private final int requiredAmount;
    private final Integer minLevel;          // NEW: e.g. for DEFEAT_TRAINER
    private final String displayName;

    private int progress = 0;
    private boolean complete = false;

    public ContractTask(TaskType type, String specific, List<String> listSpecific, int requiredAmount, Integer minLevel, String displayName) {
        this.type = type;
        this.specific = specific;
        this.listSpecific = listSpecific;
        this.requiredAmount = requiredAmount;
        this.minLevel = minLevel;
        this.displayName = displayName;
    }

    public TaskType getType() {
        return type;
    }

    public String getSpecific() {
        return specific;
    }

    public List<String> getListSpecific() {
        return listSpecific;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isComplete() {
        return complete;
    }

    public void setProgress(int progress) {
        this.progress = progress;
        this.complete = progress >= requiredAmount;
    }

    public void setComplete(boolean complete) {
        this.complete = complete;
    }

    public void incrementProgress() {
        if (complete) return;

        progress++;
        if (progress >= requiredAmount) {
            progress = requiredAmount;
            complete = true;
        }
    }

    public String getDisplayName() {
        return displayName != null ? displayName : type.toString();
    }

    public Integer getMinLevel() {
        return minLevel;
    }

}
