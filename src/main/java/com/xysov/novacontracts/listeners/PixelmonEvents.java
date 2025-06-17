package com.xysov.novacontracts.listeners;

import com.pixelmonmod.pixelmon.api.events.BeatWildPixelmonEvent;
import com.pixelmonmod.pixelmon.api.events.BeatTrainerEvent;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.EggHatchEvent;
import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import com.xysov.novacontracts.contracts.ContractTask;
import com.xysov.novacontracts.managers.ContractManager;
import com.xysov.novacontracts.contracts.TaskType;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;

import java.util.UUID;

public class PixelmonEvents {

    private final ContractManager contractManager;

    public PixelmonEvents(ContractManager contractManager) {
        this.contractManager = contractManager;
    }

    public void onCatchPokemon(CaptureEvent.SuccessfulCapture event) {
        UUID uuid = event.getPlayer().getUUID(); // Forge UUID
        Player player = Bukkit.getPlayer(uuid);  // Bukkit player lookup

        if (player == null) return;

        String species = event.getPokemon().getSpecies().getName();

        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) return;

        boolean progressMade = false;
        for (ContractTask task : contract.getTasks()) {
            if (task.getType() != TaskType.CATCH_POKEMON || task.isComplete()) continue;

            if (task.getSpecific() != null && task.getSpecific().equalsIgnoreCase(species)) {
                task.incrementProgress();
                progressMade = true;
                break;
            } else if (task.getListSpecific() != null && task.getListSpecific().contains(species)) {
                task.incrementProgress();
                progressMade = true;
                break;
            }
        }

        if (progressMade) {
            contractManager.sendTaskProgressActionBar(player, contract);
            NovaContracts.getInstance().getDataManager().savePlayerData(player);
        }

        if (contract.isComplete()) {
            contractManager.completeContract(player);
        }
    }
}
