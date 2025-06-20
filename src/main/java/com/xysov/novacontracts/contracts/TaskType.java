package com.xysov.novacontracts.contracts;

public enum TaskType {
    CATCH_POKEMON,
    DEFEAT_POKEMON,
    HATCH_EGG,
    DEFEAT_TRAINER,
    MINE_BLOCK,
    CRAFT_ITEM,
    PICK_APRICORN,
    FISH_POKEMON,
    SMELT_ITEM;

    public static TaskType fromString(String str) {
        return TaskType.valueOf(str.toUpperCase());
    }
}
