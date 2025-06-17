package com.xysov.novacontracts.utils;

import com.xysov.novacontracts.NovaContracts;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.util.logging.Level;

public class ConfigLoader {

    private static FileConfiguration contractsConfig;
    private static FileConfiguration rewardsConfig;
    private static FileConfiguration messagesConfig;

    public static void loadAllConfigs() {
        contractsConfig = load("contracts.yml");
        rewardsConfig = load("rewards.yml");
        messagesConfig = load("messages.yml");
    }

    private static FileConfiguration load(String name) {
        NovaContracts plugin = NovaContracts.getInstance();
        File file = new File(plugin.getDataFolder(), name);

        // Check if file exists; if not, try to save from resource only if it exists in jar
        if (!file.exists()) {
            InputStream resourceStream = plugin.getResource(name);
            if (resourceStream != null) {
                // Resource exists inside jar, safe to save
                plugin.saveResource(name, false);
                plugin.getLogger().info(name + " not found, saved default.");
            } else {
                // Resource doesn't exist in jar, warn and skip
                plugin.getLogger().warning(name + " resource not found inside plugin jar. Skipping creation.");
                return null;  // return null config
            }
        }

        // Load config if file exists
        if (file.exists()) {
            return YamlConfiguration.loadConfiguration(file);
        } else {
            // This should not happen if above logic worked, but just in case
            plugin.getLogger().warning(name + " file missing and no default to save.");
            return null;
        }
    }

    public static FileConfiguration getContractsConfig() {
        return contractsConfig;
    }

    public static FileConfiguration getRewardsConfig() {
        return rewardsConfig;
    }

    public static FileConfiguration getMessagesConfig() {
        return messagesConfig;
    }

    public static void reloadConfigs() {
        loadAllConfigs();
    }
}
