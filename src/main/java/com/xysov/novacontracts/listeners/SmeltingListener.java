package com.xysov.novacontracts.listeners;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import com.xysov.novacontracts.contracts.ContractTask;
import com.xysov.novacontracts.contracts.TaskType;
import com.xysov.novacontracts.managers.ContractManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;

import java.util.UUID;

public class SmeltingListener implements Listener {

    private final ContractManager contractManager;

    public SmeltingListener(NovaContracts plugin) {
        this.contractManager = plugin.getContractManager();
    }

    @EventHandler
    public void onFurnaceExtract(FurnaceExtractEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Material itemType = event.getItemType();
        int amount = event.getItemAmount();

        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) return;

        boolean progressMade = false;

        for (ContractTask task : contract.getTasks()) {
            if (task.getType() != TaskType.SMELT_ITEM || task.isComplete()) continue;

            if (task.getSpecific() != null && task.getSpecific().equalsIgnoreCase(itemType.name())) {
                int before = task.getProgress();
                task.incrementProgress(amount);
                int after = task.getProgress();
                progressMade = true;

                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " smelted " + amount + "x " + itemType.name() +
                        " | Progress: " + before + " -> " + after);
                break;
            } else if (task.getListSpecific() != null && task.getListSpecific().contains(itemType.name())) {
                int before = task.getProgress();
                task.incrementProgress(amount);
                int after = task.getProgress();
                progressMade = true;

                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " smelted " + amount + "x " + itemType.name() +
                        " | Progress: " + before + " -> " + after);
                break;
            }
        }

        if (progressMade) {
            contractManager.updateContractScoreboard(player, contract);
        }

        if (contract.isComplete()) {
            contractManager.completeContract(player);
        }
    }
}
