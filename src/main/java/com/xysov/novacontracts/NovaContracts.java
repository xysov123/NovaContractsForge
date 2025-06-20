package com.xysov.novacontracts;

import com.xysov.novacontracts.listeners.*;
import com.xysov.novacontracts.managers.SQLiteManager;
import com.xysov.novacontracts.utils.ContractPlaceholders;
import com.xysov.novacontracts.utils.PlacedBlockTracker;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import com.xysov.novacontracts.commands.ContractCommand;
import com.xysov.novacontracts.managers.ContractManager;
import com.xysov.novacontracts.managers.DataManager;
import com.xysov.novacontracts.managers.RewardManager;
import com.xysov.novacontracts.utils.ConfigLoader;
import com.pixelmonmod.pixelmon.Pixelmon;
import io.izzel.arclight.api.Arclight;

public class NovaContracts extends JavaPlugin {

    private static NovaContracts instance;

    private ContractManager contractManager;
    private RewardManager rewardManager;
    private DataManager dataManager;
    private PlacedBlockTracker placedBlockTracker;
    private SQLiteManager sqliteManager;

    public static NovaContracts getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Call the static method directly without an instance
        ConfigLoader.loadAllConfigs();

        dataManager = new DataManager(this);
        contractManager = new ContractManager(this);
        rewardManager = new RewardManager(this);
        placedBlockTracker = new PlacedBlockTracker(getDataFolder());
        sqliteManager = new SQLiteManager(getDataFolder().getAbsolutePath());

        io.izzel.arclight.api.Arclight.registerForgeEvent(
                this,
                com.pixelmonmod.pixelmon.Pixelmon.EVENT_BUS,
                new PixelmonEvents(contractManager)
        );
        getLogger().info("Registered Pixelmon Forge events via Arclight");

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ContractPlaceholders(this).register();
            getLogger().info("PlaceholderAPI hooked successfully.");
        } else {
            getLogger().warning("PlaceholderAPI not found! Placeholders will not work.");
        }


        if (getCommand("contract") != null) {
            getCommand("contract").setExecutor(new ContractCommand(this));
        } else {
            getLogger().warning("Command 'contract' not found in plugin.yml!");
        }

        Bukkit.getPluginManager().registerEvents(new PlayerJoinListener(this), this);
        Bukkit.getPluginManager().registerEvents(new CraftingListener(this), this);
        Bukkit.getPluginManager().registerEvents(new MiningListener(this), this);
        Bukkit.getPluginManager().registerEvents(new PlayerQuitListener(this), this);
        Bukkit.getPluginManager().registerEvents(new SmeltingListener(this), this);

        getLogger().info("NovaContracts has been enabled!");
    }

    @Override
    public void onDisable() {
        if (dataManager != null) {
            dataManager.saveAll();
        }
        getLogger().info("NovaContracts has been disabled.");
    }

    public ContractManager getContractManager() {
        return contractManager;
    }

    public RewardManager getRewardManager() {
        return rewardManager;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public PlacedBlockTracker getPlacedBlockTracker() {
        return placedBlockTracker;
    }

    public static String color(String input) {
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', input);
    }
}
