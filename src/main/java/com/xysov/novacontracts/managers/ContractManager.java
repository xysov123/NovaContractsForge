package com.xysov.novacontracts.managers;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import com.xysov.novacontracts.contracts.ContractTask;
import com.xysov.novacontracts.contracts.TaskType;
import com.xysov.novacontracts.utils.ConfigLoader;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ContractManager {

    private final NovaContracts plugin;

    private final Map<UUID, ActiveContract> activeContracts = new HashMap<>();
    private final Map<UUID, Long> contractCooldowns = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    public ContractManager(NovaContracts plugin) {
        this.plugin = plugin;
    }

    public void acceptContract(Player player, String tier) {
        UUID uuid = player.getUniqueId();
        FileConfiguration messages = ConfigLoader.getMessagesConfig();

        if (!player.hasPermission("novacontracts." + tier)) {
            player.sendMessage(colorMsg(messages.getString("no_permission")));
            plugin.getLogger().info("[Contract] Player " + player.getName() + " tried to accept contract tier " + tier + " without permission.");
            return;
        }

        if (activeContracts.containsKey(uuid)) {
            player.sendMessage(colorMsg(messages.getString("already_active")));
            plugin.getLogger().info("[Contract] Player " + player.getName() + " tried to accept a new contract but already has one.");
            return;
        }

        if (isOnCooldown(uuid)) {
            long remaining = (contractCooldowns.get(uuid) - System.currentTimeMillis()) / 1000;
            player.sendMessage(colorMsg(messages.getString("on_cooldown").replace("%seconds%", String.valueOf(remaining))));
            plugin.getLogger().info("[Contract] Player " + player.getName() + " is on cooldown for " + remaining + " seconds.");
            return;
        }

        FileConfiguration contractsConfig = ConfigLoader.getContractsConfig();
        if (contractsConfig == null) {
            player.sendMessage(colorMsg(messages.getString("contracts_not_loaded")));
            plugin.getLogger().warning("Contracts config not loaded.");
            return;
        }

        List<String> availableContracts = getContractIdsForTier(tier, contractsConfig);
        if (availableContracts.isEmpty()) {
            player.sendMessage(colorMsg(messages.getString("no_contracts_tier").replace("%tier%", capitalize(tier))));
            plugin.getLogger().info("[Contract] No contracts available for tier " + tier);
            return;
        }

        String contractId = availableContracts.get(new Random().nextInt(availableContracts.size()));
        ConfigurationSection contractSection = contractsConfig.getConfigurationSection("contracts." + contractId);
        if (contractSection == null) {
            player.sendMessage(colorMsg(messages.getString("error_loading_contract").replace("%contractId%", contractId)));
            plugin.getLogger().warning("Error loading contract section: " + contractId);
            return;
        }

        long duration = contractSection.getLong("duration", 600);
        List<ContractTask> tasks = parseTasks(contractSection.getConfigurationSection("tasks"));
        if (tasks.isEmpty()) {
            player.sendMessage(colorMsg(messages.getString("tasks_missing_or_invalid").replace("%contractId%", contractId)));
            plugin.getLogger().warning("Tasks missing or invalid for contract: " + contractId);
            return;
        }

        ActiveContract contract = new ActiveContract(uuid, tier, contractId, System.currentTimeMillis(), duration, tasks);
        activeContracts.put(uuid, contract);

        player.sendMessage(colorMsg(messages.getString("accepted_contract")
                .replace("%tier%", capitalize(tier))
                .replace("%contractId%", contractId)));

        plugin.getLogger().info("[Contract] Player " + player.getName() + " accepted contract " + contractId + " of tier " + tier);

        startBossBar(player, contract);
        startContractTimer(player, contract);
    }

    private List<ContractTask> parseTasks(ConfigurationSection tasksSection) {
        if (tasksSection == null) return Collections.emptyList();

        List<ContractTask> tasks = new ArrayList<>();
        for (String key : tasksSection.getKeys(false)) {
            ConfigurationSection taskSec = tasksSection.getConfigurationSection(key);
            if (taskSec == null) continue;

            String typeStr = taskSec.getString("type");
            if (typeStr == null) continue;

            TaskType type;
            try {
                type = TaskType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                continue;
            }

            int target = taskSec.getInt("target", 1);
            String specific = taskSec.getString("specific", null);
            List<String> listSpecific = taskSec.getStringList("listSpecific");
            if (listSpecific.isEmpty()) listSpecific = null;

            tasks.add(new ContractTask(type, specific, listSpecific, target));
        }
        return tasks;
    }

    public void viewContract(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration messages = ConfigLoader.getMessagesConfig();
        ActiveContract contract = activeContracts.get(uuid);

        if (contract == null) {
            player.sendMessage(colorMsg(messages.getString("view_no_active")));
            return;
        }

        player.sendMessage(colorMsg(messages.getString("view_header")));
        player.sendMessage(colorMsg(messages.getString("view_tier").replace("%tier%", capitalize(contract.getTier()))));
        player.sendMessage(colorMsg(messages.getString("view_name").replace("%contractId%", contract.getContractId())));
    }

    public void cancelContract(Player player) {
        UUID uuid = player.getUniqueId();
        FileConfiguration messages = ConfigLoader.getMessagesConfig();

        if (!activeContracts.containsKey(uuid)) {
            player.sendMessage(colorMsg(messages.getString("no_active_contract_cancel")));
            return;
        }

        activeContracts.remove(uuid);
        stopBossBar(uuid);
        startCooldown(uuid);

        player.sendMessage(colorMsg(messages.getString("cancelled_contract")));
        plugin.getLogger().info("[Contract] Player " + player.getName() + " cancelled their contract.");
    }

    public void resetCooldown(String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        FileConfiguration messages = ConfigLoader.getMessagesConfig();
        if (target != null) {
            contractCooldowns.remove(target.getUniqueId());
            target.sendMessage(colorMsg(messages.getString("cooldown_reset").replace("%player%", target.getName())));
            plugin.getLogger().info("[Contract] Cooldown reset for player " + target.getName());
        }
    }

    public Map<UUID, Long> getContractCooldowns() {
        return contractCooldowns;
    }

    public void completeContract(Player player) {
        UUID uuid = player.getUniqueId();
        ActiveContract contract = activeContracts.get(uuid);
        if (contract == null) return;

        // Give rewards for the contract
        plugin.getRewardManager().giveReward(player, contract);

        // Remove active contract
        activeContracts.remove(uuid);

        // Remove cooldown or update as needed, example removing cooldown
        contractCooldowns.remove(uuid);

        // Save player data after completion
        plugin.getDataManager().savePlayerData(player);

        player.sendMessage(colorMsg("&aYou have completed your contract!"));
    }

    public void sendTaskProgressActionBar(Player player, ActiveContract contract) {
        FileConfiguration messages = ConfigLoader.getMessagesConfig();
        String format = messages.getString("task_progress_actionbar", "&a%progress%");

        plugin.getLogger().info("[DEBUG] Sending action bar to player " + player.getName());

        StringBuilder progress = new StringBuilder();
        for (ContractTask task : contract.getTasks()) {
            plugin.getLogger().info("[DEBUG] Checking task: " + task.getDisplayName() + ", Complete? " + task.isComplete());

            if (task.isComplete()) {
                continue;
            }

            int remaining = Math.max(0, task.getRequiredAmount() - task.getProgress());
            progress.append(task.getDisplayName())
                    .append(": ")
                    .append(remaining)
                    .append(" left | ");
        }

        if (progress.length() > 3) {
            progress.setLength(progress.length() - 3); // remove trailing " | "
        }

        plugin.getLogger().info("[DEBUG] Action bar progress string: '" + progress.toString() + "'");

        if (progress.length() == 0) {
            plugin.getLogger().info("[DEBUG] No tasks to show in action bar, skipping send.");
            return;  // no progress to show, don't send empty action bar
        }

        String msg = format.replace("%progress%", progress.toString());
        plugin.getLogger().info("[DEBUG] Final action bar message: " + msg);

        player.spigot().sendMessage(
                net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(colorMsg(msg))
        );
    }

    public boolean isOnCooldown(UUID uuid) {
        return contractCooldowns.containsKey(uuid) && contractCooldowns.get(uuid) > System.currentTimeMillis();
    }

    public void startCooldown(UUID uuid) {
        int cooldownSec = plugin.getConfig().getInt("global-cooldown", 600);
        contractCooldowns.put(uuid, System.currentTimeMillis() + cooldownSec * 1000L);
    }

    private List<String> getContractIdsForTier(String tier, FileConfiguration contractsConfig) {
        ConfigurationSection all = contractsConfig.getConfigurationSection("contracts");
        plugin.getLogger().info("[DEBUG] Looking for contracts under 'contracts' section: " + all);

        if (all == null) {
            plugin.getLogger().warning("[DEBUG] Contracts section is null! Check your contracts.yml");
            return Collections.emptyList();
        }

        List<String> ids = new ArrayList<>();
        for (String key : all.getKeys(false)) {
            plugin.getLogger().info("[DEBUG] Found contract key: " + key);

            ConfigurationSection section = all.getConfigurationSection(key);
            if (section == null) continue;

            String t = section.getString("tier");
            plugin.getLogger().info("[DEBUG] Contract '" + key + "' has tier: " + t);

            if (tier.equalsIgnoreCase(t)) {
                ids.add(key);

                // Log tasks details:
                ConfigurationSection tasksSection = section.getConfigurationSection("tasks");
                if (tasksSection == null) {
                    plugin.getLogger().warning("[DEBUG] Contract '" + key + "' has no tasks section!");
                    continue;
                }

                plugin.getLogger().info("[DEBUG] Tasks for contract '" + key + "':");
                for (String taskKey : tasksSection.getKeys(false)) {
                    ConfigurationSection task = tasksSection.getConfigurationSection(taskKey);
                    if (task == null) continue;

                    String type = task.getString("type");
                    int target = task.getInt("target", 1);
                    String specific = task.getString("specific", "null");
                    List<String> listSpecific = task.getStringList("listSpecific");
                    plugin.getLogger().info(String.format("  Task '%s': type=%s, target=%d, specific=%s, listSpecific=%s",
                            taskKey, type, target, specific, listSpecific));
                }
            }
        }
        plugin.getLogger().info("[DEBUG] Contracts found for tier '" + tier + "': " + ids);
        return ids;
    }


    public Map<UUID, ActiveContract> getActiveContracts() {
        return activeContracts;
    }

    // --- Boss bar and timer handling ---

    public void startBossBar(Player player, ActiveContract contract) {
        FileConfiguration messages = ConfigLoader.getMessagesConfig();

        // Get color from messages.yml or default to GREEN
        String colorStr = messages.getString("bossbar.color", "GREEN").toUpperCase();
        BarColor color;
        try {
            color = BarColor.valueOf(colorStr);
        } catch (IllegalArgumentException e) {
            color = BarColor.GREEN;
        }

        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) {
            bar = Bukkit.createBossBar("", color, BarStyle.SOLID);
            bossBars.put(player.getUniqueId(), bar);
        } else {
            bar.setColor(color);
        }
        bar.addPlayer(player);
        bar.setProgress(1.0);
        updateBossBarTitle(player, contract, bar);
    }

    private void stopBossBar(UUID uuid) {
        BossBar bar = bossBars.remove(uuid);
        if (bar != null) {
            for (Player p : bar.getPlayers()) {
                bar.removePlayer(p);
            }
            bar.setVisible(false);
        }
    }

    private String capitalizeFirstLetter(String input) {
        if (input == null || input.isEmpty()) return input;
        return input.substring(0, 1).toUpperCase() + input.substring(1).toLowerCase();
    }

    private void updateBossBarTitle(Player player, ActiveContract contract, BossBar bar) {
        FileConfiguration messages = ConfigLoader.getMessagesConfig();
        String titleTemplate = messages.getString("bossbar.title", "Contract: %contractId% - Tier: %tier% - Time Left: %time%");
        long timeLeftMillis = contract.getTimeRemaining();

        String timeFormatted = formatTime(timeLeftMillis);
        String tier = capitalizeFirstLetter(contract.getTier());

        String title = titleTemplate
                .replace("%contractId%", contract.getContractId())
                .replace("%time%", timeFormatted)
                .replace("%tier%", tier);

        bar.setTitle(colorMsg(title));
    }

    public void startContractTimer(Player player, ActiveContract contract) {
        UUID uuid = player.getUniqueId();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!activeContracts.containsKey(uuid)) {
                    stopBossBar(uuid);
                    cancel();
                    return;
                }

                long timeLeft = contract.getTimeRemaining();
                if (timeLeft <= 0) {
                    activeContracts.remove(uuid);
                    stopBossBar(uuid);
                    player.sendMessage(colorMsg("§cYour contract has expired."));
                    plugin.getLogger().info("[Contract] Player " + player.getName() + "'s contract expired.");
                    startCooldown(uuid);
                    cancel();
                    return;
                }

                double progress = timeLeft / (double) (contract.getDuration() * 1000L);
                BossBar bar = bossBars.get(uuid);
                if (bar != null) {
                    bar.setProgress(progress);
                    updateBossBarTitle(player, contract, bar);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
    }

    private String colorMsg(String msg) {
        if (msg == null) return "";
        return msg.replace('&', '§');
    }
}
