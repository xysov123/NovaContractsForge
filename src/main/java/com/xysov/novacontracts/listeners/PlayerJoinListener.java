package com.xysov.novacontracts.listeners;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import com.xysov.novacontracts.managers.ContractManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public class PlayerJoinListener implements Listener {

    private final NovaContracts plugin;
    private final ContractManager contractManager;

    public PlayerJoinListener(NovaContracts plugin) {
        this.plugin = plugin;
        this.contractManager = plugin.getContractManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Load reputation and cooldown from DB
        plugin.getDataManager().loadPlayerData(player);

        // Load active contract from DB
        ActiveContract contract = plugin.getDataManager().loadActiveContract(uuid);
        if (contract != null) {
            contractManager.getActiveContracts().put(uuid, contract);

            // Restore the boss bar and timer for this player
            contractManager.startBossBar(player, contract);
            contractManager.startContractTimer(player, contract);
            contractManager.updateContractScoreboard(player, contract);
        }
    }
}
