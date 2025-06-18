package com.xysov.novacontracts.listeners;

import com.pixelmonmod.pixelmon.api.events.BeatWildPixelmonEvent;
import com.pixelmonmod.pixelmon.api.events.BeatTrainerEvent;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.EggHatchEvent;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import com.xysov.novacontracts.contracts.ContractTask;
import com.xysov.novacontracts.managers.ContractManager;
import com.xysov.novacontracts.contracts.TaskType;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.UUID;

public class PixelmonEvents {

    private final ContractManager contractManager;

    public PixelmonEvents(ContractManager contractManager) {
        this.contractManager = contractManager;
    }

    @SubscribeEvent
    public void onCatchPokemon(CaptureEvent.SuccessfulCapture event) {
        UUID uuid = event.getPlayer().getUUID(); // Forge UUID
        Player player = Bukkit.getPlayer(uuid);  // Bukkit player lookup

        if (player == null) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No Bukkit player found for UUID: " + uuid);
            return;
        }

        Species speciesObj = event.getPokemon().getSpecies();
        String species = speciesObj.getName();
        NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " caught a " + species);

        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No active contract for player " + player.getName());
            return;
        }

        boolean progressMade = false;
        for (ContractTask task : contract.getTasks()) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] Checking task: type=" + task.getType()
                    + ", specific=" + task.getSpecific()
                    + ", listSpecific=" + task.getListSpecific()
                    + ", progress=" + task.getProgress()
                    + "/" + task.getRequiredAmount());

            if (task.getType() != TaskType.CATCH_POKEMON || task.isComplete()) continue;

            if (task.getSpecific() != null && task.getSpecific().equalsIgnoreCase(species)) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " caught " + species +
                        ", progress now " + task.getProgress() + "/" + task.getRequiredAmount());
                progressMade = true;
                break;
            } else if (task.getListSpecific() != null && task.getListSpecific().contains(species)) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " caught " + species +
                        ", progress now " + task.getProgress() + "/" + task.getRequiredAmount());
                progressMade = true;
                break;
            }
        }

        if (progressMade) {
            contractManager.sendTaskProgressActionBar(player, contract);
            NovaContracts.getInstance().getDataManager().savePlayerData(player);
        } else {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No matching CATCH_POKEMON task for " + species);
        }

        if (contract.isComplete()) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] Contract complete for player " + player.getName());
            contractManager.completeContract(player);
        }
    }
}
