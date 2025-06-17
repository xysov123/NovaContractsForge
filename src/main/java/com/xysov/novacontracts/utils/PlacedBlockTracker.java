package com.xysov.novacontracts.utils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class PlacedBlockTracker {
    private final Set<String> placedBlockSet = new HashSet<>();
    private final File file;

    public PlacedBlockTracker(File dataFolder) {
        this.file = new File(dataFolder, "placed_blocks.yml");
        load();
    }

    public void add(Location loc) {
        placedBlockSet.add(locToString(loc));
    }

    public boolean isPlaced(Location loc) {
        return placedBlockSet.contains(locToString(loc));
    }

    public void remove(Location loc) {
        placedBlockSet.remove(locToString(loc));
    }

    private String locToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public void save() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("blocks", new java.util.ArrayList<>(placedBlockSet));
        try {
            config.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().severe("[NovaContracts] Failed to save placed_blocks.yml: " + e.getMessage());
        }
    }

    public void load() {
        if (!file.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        placedBlockSet.clear();
        placedBlockSet.addAll(config.getStringList("blocks"));
    }
}