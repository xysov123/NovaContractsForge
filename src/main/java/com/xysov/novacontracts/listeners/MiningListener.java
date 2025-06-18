package com.xysov.novacontracts.listeners;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import com.xysov.novacontracts.contracts.ContractTask;
import com.xysov.novacontracts.contracts.TaskType;
import com.xysov.novacontracts.managers.ContractManager;
import com.xysov.novacontracts.utils.PlacedBlockTracker;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.UUID;

public class MiningListener implements Listener {

    private final ContractManager contractManager;

    public MiningListener(NovaContracts plugin) {
        this.contractManager = plugin.getContractManager();
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        Block block = event.getBlock();

        PlacedBlockTracker tracker = NovaContracts.getInstance().getPlacedBlockTracker();

        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No active contract for player " + player.getName());
            return;
        }

        Material blockType = block.getType();
        boolean progressMade = false;

        if (tracker.isPlaced(block.getLocation())) {
            tracker.remove(block.getLocation()); // prevent refarming
            return; // skip progress increment
        }

        for (ContractTask task : contract.getTasks()) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] Checking task: type=" + task.getType()
                    + ", specific=" + task.getSpecific()
                    + ", listSpecific=" + task.getListSpecific()
                    + ", progress=" + task.getProgress()
                    + "/" + task.getRequiredAmount());

            if (task.getType() != TaskType.MINE_BLOCK || task.isComplete()) continue;

            if (task.getSpecific() != null && task.getSpecific().equalsIgnoreCase(blockType.name())) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " mined " + blockType.name() +
                        ", progress now " + task.getProgress() + "/" + task.getRequiredAmount());
                progressMade = true;
                break;
            } else if (task.getListSpecific() != null && task.getListSpecific().contains(blockType.name())) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " mined " + blockType.name() +
                        ", progress now " + task.getProgress() + "/" + task.getRequiredAmount());
                progressMade = true;
                break;
            }
        }

        if (progressMade) {
            // Send updated action bar progress to player
            contractManager.updateContractScoreboard(player, contract);
        } else {
            NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " mined " + blockType.name() + " but no matching task found.");
        }

        if (contract.isComplete()) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] Contract complete for player " + player.getName());
            contractManager.completeContract(player);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        Material mat = block.getType();

        if (isTrackedMaterial(mat)) {
            NovaContracts.getInstance().getPlacedBlockTracker().add(block.getLocation());
        }
    }

    private boolean isTrackedMaterial(Material mat) {
        return switch (mat) {
            case COAL_ORE, IRON_ORE, GOLD_ORE, DIAMOND_ORE, EMERALD_ORE -> true;
            default -> false;
        };
    }
}
