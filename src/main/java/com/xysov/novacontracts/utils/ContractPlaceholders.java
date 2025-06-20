package com.xysov.novacontracts.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import com.xysov.novacontracts.NovaContracts;

public class ContractPlaceholders extends PlaceholderExpansion {

    private final NovaContracts plugin;

    public ContractPlaceholders(NovaContracts plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "novacontracts";
    }

    @Override
    public String getAuthor() {
        return "YourName";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return ""; // no player, no value

        if (identifier.equalsIgnoreCase("reputation")) {
            int rep = plugin.getContractManager().getReputation(player.getUniqueId());
            return String.valueOf(rep);
        }
        return null;
    }
}
