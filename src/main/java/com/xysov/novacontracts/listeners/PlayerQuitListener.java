package com.xysov.novacontracts.listeners;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerQuitListener implements Listener {

    private final NovaContracts plugin;

    public PlayerQuitListener(NovaContracts plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getDataManager().savePlayerData(player);

        ActiveContract contract = plugin.getContractManager().getActiveContracts().get(player.getUniqueId());
        if (contract != null) {
            plugin.getDataManager().saveActiveContract(player.getUniqueId(), contract);
        }
    }
}