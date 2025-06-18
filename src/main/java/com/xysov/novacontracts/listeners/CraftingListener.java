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
import org.bukkit.event.inventory.CraftItemEvent;
import java.util.UUID;

public class CraftingListener implements Listener {

    private final ContractManager contractManager;

    public CraftingListener(NovaContracts plugin) {
        this.contractManager = plugin.getContractManager();
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID uuid = player.getUniqueId();

        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) return;

        Material crafted = event.getRecipe().getResult().getType();
        boolean progressMade = false;

        for (ContractTask task : contract.getTasks()) {
            if (task.getType() != TaskType.CRAFT_ITEM || task.isComplete()) continue;

            if (task.getSpecific() != null && task.getSpecific().equalsIgnoreCase(crafted.name())) {
                task.incrementProgress();
                progressMade = true;
                break;

            } else if (task.getListSpecific() != null && task.getListSpecific().contains(crafted.name())) {
                task.incrementProgress();
                progressMade = true;
                break;
            }
        }

        if (progressMade) {
            contractManager.updateContractScoreboard(player, contract);
            NovaContracts.getInstance().getDataManager().savePlayerData(player);
        }

        if (contract.isComplete()) {
            contractManager.completeContract(player);
        }
    }
}
