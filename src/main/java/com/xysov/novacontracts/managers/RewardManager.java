package com.xysov.novacontracts.managers;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import com.xysov.novacontracts.contracts.ContractTask;
import com.xysov.novacontracts.utils.ConfigLoader;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

public class RewardManager {

    private final NovaContracts plugin;

    public RewardManager(NovaContracts plugin) {
        this.plugin = plugin;
    }

    public void giveReward(Player player, ActiveContract contract) {
        FileConfiguration contractsConfig = ConfigLoader.getContractsConfig();
        String contractId = contract.getContractId();
        ConfigurationSection contractSection = contractsConfig.getConfigurationSection("contracts." + contractId);

        if (contractSection == null) {
            plugin.getLogger().warning("[Rewards] Could not find contract section for " + contractId);
            return;
        }

        ConfigurationSection tasksSection = contractSection.getConfigurationSection("tasks");
        if (tasksSection == null) {
            plugin.getLogger().warning("[Rewards] No tasks section for contract " + contractId);
            return;
        }

        for (ContractTask task : contract.getTasks()) {
            for (String key : tasksSection.getKeys(false)) {
                ConfigurationSection taskSec = tasksSection.getConfigurationSection(key);
                if (taskSec == null) continue;

                String type = taskSec.getString("type");
                String specific = taskSec.getString("specific");

                boolean matchType = type != null && type.equalsIgnoreCase(task.getType().name());
                boolean matchSpecific = (specific == null && task.getSpecific() == null) ||
                        (specific != null && specific.equalsIgnoreCase(task.getSpecific()));

                if (matchType && matchSpecific) {
                    List<String> commands = taskSec.getStringList("rewards");
                    for (String cmd : commands) {
                        String parsed = cmd.replace("%player%", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                    }
                }
            }
        }

        plugin.getLogger().info("[Rewards] Issued rewards to " + player.getName() + " for contract " + contractId);
    }
}
