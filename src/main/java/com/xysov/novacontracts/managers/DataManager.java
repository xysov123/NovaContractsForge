package com.xysov.novacontracts.managers;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class DataManager {

    private final NovaContracts plugin;
    private final File dataFile;
    private final YamlConfiguration dataConfig;

    public DataManager(NovaContracts plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!dataFile.exists()) {
            plugin.saveResource("playerdata.yml", false);
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        // TODO: load active contracts & cooldowns from dataConfig and populate ContractManager maps
    }

    public void savePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        // TODO: save active contracts & cooldowns from ContractManager into dataConfig, then save file
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save player data for " + player.getName());
            e.printStackTrace();
        }
    }

    public void saveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            savePlayerData(player);
        }
    }
}
