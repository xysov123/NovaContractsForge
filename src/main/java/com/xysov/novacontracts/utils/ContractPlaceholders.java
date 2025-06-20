package com.xysov.novacontracts.utils;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.managers.ContractManager.PlayerReputationEntry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

import java.util.List;

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
        return "Xysov";
    }

    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        if (player == null) return "";

        if (identifier.equalsIgnoreCase("reputation")) {
            int rep = plugin.getContractManager().getReputation(player.getUniqueId());
            return String.valueOf(rep);
        }

        if (identifier.startsWith("top_")) {
            String[] parts = identifier.split("_");
            if (parts.length == 3) {
                String type = parts[1];
                int rank;
                try {
                    rank = Integer.parseInt(parts[2]);
                } catch (NumberFormatException e) {
                    return "";
                }

                if (rank < 1 || rank > 20) return "";

                List<PlayerReputationEntry> leaderboard = plugin.getContractManager().getCachedTopReputation();

                if (leaderboard == null || rank > leaderboard.size()) return "";

                PlayerReputationEntry entry = leaderboard.get(rank - 1);

                if (type.equalsIgnoreCase("name")) {
                    return entry.getPlayerName();
                } else if (type.equalsIgnoreCase("reputation")) {
                    return String.valueOf(entry.getReputation());
                }
            }
        }

        return "";
    }
}
