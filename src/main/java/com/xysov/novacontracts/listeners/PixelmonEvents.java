package com.xysov.novacontracts.listeners;

import com.pixelmonmod.pixelmon.api.events.BeatWildPixelmonEvent;
import com.pixelmonmod.pixelmon.api.events.BeatTrainerEvent;
import com.pixelmonmod.pixelmon.api.events.CaptureEvent;
import com.pixelmonmod.pixelmon.api.events.EggHatchEvent;
import com.pixelmonmod.pixelmon.api.pokemon.species.Species;
import com.pixelmonmod.pixelmon.battles.controller.participants.PixelmonWrapper;
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

    @SubscribeEvent
    public void onDefeatWildPokemon(BeatWildPixelmonEvent event) {
        // Get Forge player object
        net.minecraft.entity.player.ServerPlayerEntity forgePlayer = event.player;
        UUID uuid = forgePlayer.getUUID();

        // Get Bukkit Player from UUID (can be null if offline)
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No Bukkit player found for UUID: " + uuid);
            return;
        }

        // Get species of defeated Pokémon
        PixelmonWrapper pw = event.wpp.asWrapper();
        Species speciesObj = pw.getSpecies();
        String species = speciesObj.getName();

        NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " defeated a wild " + species);

        // Retrieve active contract for player
        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No active contract for player " + player.getName());
            return;
        }

        boolean progressMade = false;

        // Iterate over contract tasks, checking for a defeat task matching this species
        for (ContractTask task : contract.getTasks()) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] Checking task: type=" + task.getType()
                    + ", specific=" + task.getSpecific()
                    + ", listSpecific=" + task.getListSpecific()
                    + ", progress=" + task.getProgress()
                    + "/" + task.getRequiredAmount());

            // Assuming you have a TaskType for defeating wild Pokemon, e.g., TaskType.DEFEAT_WILD_POKEMON
            if (task.getType() != TaskType.DEFEAT_POKEMON || task.isComplete()) continue;

            // Check if the task targets a specific species
            if (task.getSpecific() != null && task.getSpecific().equalsIgnoreCase(species)) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " defeated " + species +
                        ", progress now " + task.getProgress() + "/" + task.getRequiredAmount());
                progressMade = true;
                break;
            }
            // Or if the task targets any from a list of species
            else if (task.getListSpecific() != null && task.getListSpecific().contains(species)) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " defeated " + species +
                        ", progress now " + task.getProgress() + "/" + task.getRequiredAmount());
                progressMade = true;
                break;
            }
        }

        if (progressMade) {
            contractManager.sendTaskProgressActionBar(player, contract);
            NovaContracts.getInstance().getDataManager().savePlayerData(player);
        } else {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No matching DEFEAT_WILD_POKEMON task for " + species);
        }

        if (contract.isComplete()) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] Contract complete for player " + player.getName());
            contractManager.completeContract(player);
        }
    }

    @SubscribeEvent
    public void onEggHatch(EggHatchEvent.Post event) {
        net.minecraft.entity.player.ServerPlayerEntity forgePlayer = event.getPlayer();
        if (forgePlayer == null) return;

        UUID uuid = forgePlayer.getUUID();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No Bukkit player found for UUID: " + uuid);
            return;
        }

        String species = event.getPokemon().getSpecies().getName();
        NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " hatched a " + species);

        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No active contract for player " + player.getName());
            return;
        }

        boolean progressMade = false;

        for (ContractTask task : contract.getTasks()) {
            if (task.getType() != TaskType.HATCH_EGG || task.isComplete()) continue;

            String target = task.getSpecific(); // could be null
            if (target == null || target.equalsIgnoreCase(species)) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " hatched " + species +
                        ", progress now " + task.getProgress() + "/" + task.getRequiredAmount());
                progressMade = true;
                break;
            }
        }

        if (progressMade) {
            contractManager.sendTaskProgressActionBar(player, contract);
            NovaContracts.getInstance().getDataManager().savePlayerData(player);
        } else {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No matching HATCH_EGG task for " + species);
        }

        if (contract.isComplete()) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] Contract complete for player " + player.getName());
            contractManager.completeContract(player);
        }
    }

    @SubscribeEvent
    public void onDefeatTrainer(BeatTrainerEvent event) {
        UUID uuid = event.player.getUUID();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        String trainerName = event.trainer.getName().getString();
        int trainerLevel = event.trainer.getTrainerLevel();

        NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() +
                " defeated trainer " + trainerName + " (Level " + trainerLevel + ")");

        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) return;

        boolean progressMade = false;

        for (ContractTask task : contract.getTasks()) {
            if (task.getType() != TaskType.DEFEAT_TRAINER || task.isComplete()) continue;

            String target = task.getSpecific(); // can be null
            Integer minLevel = task.getMinLevel(); // You'll add this getter

            if ((target == null || target.equalsIgnoreCase(trainerName))
                    && (minLevel == null || trainerLevel >= minLevel)) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Trainer match met, progress now " +
                        task.getProgress() + "/" + task.getRequiredAmount());
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
