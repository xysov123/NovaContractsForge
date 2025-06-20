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
import com.pixelmonmod.pixelmon.api.events.ApricornEvent;
import com.pixelmonmod.pixelmon.api.events.EvolveEvent;
import com.pixelmonmod.pixelmon.api.events.CurryFinishedEvent;
import com.pixelmonmod.pixelmon.api.events.FishingEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
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
            contractManager.updateContractScoreboard(player, contract);
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

        Player player = Bukkit.getPlayer(uuid);
        if (player == null) {
            NovaContracts.getInstance().getLogger().info("[DEBUG] No Bukkit player found for UUID: " + uuid);
            return;
        }

        PixelmonWrapper pw = event.wpp.asWrapper();
        Species speciesObj = pw.getSpecies();
        String species = speciesObj.getName();

        NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " defeated a wild " + species);

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

            if (task.getType() != TaskType.DEFEAT_POKEMON || task.isComplete()) continue;

            if (task.getSpecific() != null && task.getSpecific().equalsIgnoreCase(species)) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " defeated " + species +
                        ", progress now " + task.getProgress() + "/" + task.getRequiredAmount());
                progressMade = true;
                break;
            }

            else if (task.getListSpecific() != null && task.getListSpecific().contains(species)) {
                task.incrementProgress();
                NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " defeated " + species +
                        ", progress now " + task.getProgress() + "/" + task.getRequiredAmount());
                progressMade = true;
                break;
            }
        }

        if (progressMade) {
            contractManager.updateContractScoreboard(player, contract);
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
            contractManager.updateContractScoreboard(player, contract);
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
            contractManager.updateContractScoreboard(player, contract);
            NovaContracts.getInstance().getDataManager().savePlayerData(player);
        }

        if (contract.isComplete()) {
            contractManager.completeContract(player);
        }
    }

    @SubscribeEvent
    public void onApricornPick(ApricornEvent.Pick event) {
        UUID uuid = event.getPlayer().getUUID();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        String apricornType = event.getApricorn().name();
        NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " picked apricorn: " + apricornType);

        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) return;

        boolean progressMade = false;
        for (ContractTask task : contract.getTasks()) {
            if (task.getType() != TaskType.PICK_APRICORN || task.isComplete()) continue;

            if (task.getSpecific() != null && task.getSpecific().equalsIgnoreCase(apricornType)) {
                task.incrementProgress();
                progressMade = true;
                break;
            } else if (task.getListSpecific() != null && task.getListSpecific().contains(apricornType)) {
                task.incrementProgress();
                progressMade = true;
                break;
            } else if (task.getSpecific() == null && task.getListSpecific() == null) {
                // Catch-all if it's a general apricorn pick task
                task.incrementProgress();
                progressMade = true;
                break;
            }
        }

        if (progressMade) {
            contractManager.updateContractScoreboard(player, contract);
            NovaContracts.getInstance().getDataManager().savePlayerData(player);

            if (contract.isComplete()) {
                NovaContracts.getInstance().getLogger().info("[DEBUG] Contract complete for player " + player.getName());
                contractManager.completeContract(player);
            }
        }
    }

    @SubscribeEvent
    public void onFishingCatch(FishingEvent.Catch event) {
        UUID uuid = event.player.getUUID();
        Player player = Bukkit.getPlayer(uuid);
        if (player == null) return;

        NovaContracts.getInstance().getLogger().info("[DEBUG] Player " + player.getName() + " reeled a Pokémon while fishing.");

        ActiveContract contract = contractManager.getActiveContracts().get(uuid);
        if (contract == null) return;

        boolean progressMade = false;
        for (ContractTask task : contract.getTasks()) {
            if (task.getType() != TaskType.FISH_POKEMON || task.isComplete()) continue;

            // If the task is not specific to a species, just increment
            if (task.getSpecific() == null && task.getListSpecific() == null) {
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
