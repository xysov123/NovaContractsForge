package com.xysov.novacontracts.managers;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import org.bukkit.entity.Player;

public class RewardManager {

    private final NovaContracts plugin;

    public RewardManager(NovaContracts plugin) {
        this.plugin = plugin;
    }

    public void giveReward(Player player, ActiveContract contract) {
        // TODO: read rewards from config for contract.getContractId()
        // and give items, money, XP, permissions, or run commands for the player
    }
}
