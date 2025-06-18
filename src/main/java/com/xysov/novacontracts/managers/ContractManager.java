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
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.*;

public class ContractManager {

    private final NovaContracts plugin;

    private final Map<UUID, ActiveContract> activeContracts = new HashMap<>();
    private final Map<UUID, Long> contractCooldowns = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Scoreboard> contractScoreboards = new HashMap<>();

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

        // Pick one random contract from the available contracts
        String contractId = availableContracts.get(new Random().nextInt(availableContracts.size()));
        ConfigurationSection contractSection = contractsConfig.getConfigurationSection("contracts." + contractId);
        if (contractSection == null) {
            player.sendMessage(colorMsg(messages.getString("error_loading_contract").replace("%contractId%", contractId)));
            plugin.getLogger().warning("Error loading contract section: " + contractId);
            return;
        }

        long duration = contractSection.getLong("duration", 600);
        // Pass the contractId as displayName to parseTasks, so tasks get the friendly contract name
        List<ContractTask> allTasks = parseTasks(contractSection.getConfigurationSection("tasks"), contractId);
        if (allTasks.isEmpty()) {
            player.sendMessage(colorMsg(messages.getString("tasks_missing_or_invalid").replace("%contractId%", contractId)));
            plugin.getLogger().warning("Tasks missing or invalid for contract: " + contractId);
            return;
        }

        // Pick exactly one random task from the list
        ContractTask chosenTask = allTasks.get(new Random().nextInt(allTasks.size()));
        List<ContractTask> chosenTasks = Collections.singletonList(chosenTask);

        ActiveContract contract = new ActiveContract(uuid, tier, contractId, System.currentTimeMillis(), duration, chosenTasks);
        activeContracts.put(uuid, contract);

        plugin.getLogger().info("[Contract] Player " + player.getName() + " accepted contract " + contractId + " of tier " + tier);

        sendTitle(
                player,
                messages.getString("titles.accepted.title", "&aContract Accepted"),
                messages.getString("titles.accepted.subtitle", "&fYou accepted a %tier% contract").replace("%tier%", capitalize(tier)),
                10, 60, 20
        );

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            startBossBar(player, contract);
            startContractTimer(player, contract);
        }, 90L);
    }



    private List<ContractTask> parseTasks(ConfigurationSection tasksSection, String displayName) {
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
            Integer minLevel = taskSec.contains("min-level") ? taskSec.getInt("min-level") : null;
            List<String> listSpecific = taskSec.getStringList("listSpecific");
            if (listSpecific.isEmpty()) listSpecific = null;

            tasks.add(new ContractTask(type, specific, listSpecific, target, minLevel, displayName));
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

        // Instead of contract ID, show task details:
        int taskNumber = 1;
        for (ContractTask task : contract.getTasks()) {
            String taskDisplay = formatTaskForDisplay(task, taskNumber);
            player.sendMessage(colorMsg(taskDisplay));
            taskNumber++;
        }
    }

    // Helper method to format a single task nicely
    private String formatTaskForDisplay(ContractTask task, int taskNumber) {
        String typeName = task.getType().name().replace('_', ' '); // e.g. CATCH_POKEMON → "CATCH POKEMON"
        String specific = (task.getSpecific() != null) ? task.getSpecific() : "Multiple";

        // Show progress left
        int left = Math.max(0, task.getRequiredAmount() - task.getProgress());

        return String.format("§7➤ §fTask %d: %s (%s) §8- §c%d left", taskNumber, typeName, specific, left);
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

        sendTitle(player, "&aContract Complete", "&fWell done!", 10, 60, 20);
    }

    public void updateContractScoreboard(Player player, ActiveContract contract) {
        FileConfiguration messages = ConfigLoader.getMessagesConfig();

        String title = colorMsg(messages.getString("scoreboard.title", "§a✦ Contract ✦"));
        List<ContractTask> tasks = contract.getTasks();

        String taskLine = "";
        for (ContractTask task : tasks) {
            if (task.isComplete()) continue;
            int remaining = Math.max(0, task.getRequiredAmount() - task.getProgress());

            String taskFormat = messages.getString("scoreboard.task_line", "§f%task% §8- §c%x% left");
            taskLine = colorMsg(taskFormat
                    .replace("%task%", task.getDisplayName())
                    .replace("%x%", String.valueOf(remaining)));
            break;
        }

        if (taskLine.isEmpty()) {
            taskLine = colorMsg(messages.getString("scoreboard.no_active_tasks", "§7No active tasks"));
        }

        Scoreboard scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = scoreboard.registerNewObjective("contract", "dummy", title);
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        objective.getScore(taskLine).setScore(0); // Required to show scoreboard

        player.setScoreboard(scoreboard);
        contractScoreboards.put(player.getUniqueId(), scoreboard);
    }



    public void removeContractScoreboard(UUID uuid) {
        contractScoreboards.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.setScoreboard(Bukkit.getScoreboardManager().getNewScoreboard());
        }
    }


    public boolean isOnCooldown(UUID uuid) {
        return contractCooldowns.containsKey(uuid) && contractCooldowns.get(uuid) > System.currentTimeMillis();
    }

    public void startCooldown(UUID uuid) {
        int cooldownSec = plugin.getConfig().getInt("global-cooldown", 600);
        contractCooldowns.put(uuid, System.currentTimeMillis() + cooldownSec * 1000L);
    }
    public void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        player.sendTitle(
                colorMsg(title),
                colorMsg(subtitle),
                fadeIn, stay, fadeOut
        );
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
        long totalDurationMillis = contract.getDuration() * 1000L;

        new BukkitRunnable() {
            private boolean flashToggle = false;

            @Override
            public void run() {
                if (!activeContracts.containsKey(uuid)) {
                    stopBossBar(uuid);
                    removeContractScoreboard(uuid);
                    cancel();
                    return;
                }

                long timeLeft = contract.getTimeRemaining();
                if (timeLeft <= 0) {
                    activeContracts.remove(uuid);
                    stopBossBar(uuid);
                    removeContractScoreboard(uuid);
                    FileConfiguration messages = ConfigLoader.getMessagesConfig();
                    sendTitle(
                            player,
                            messages.getString("titles.expired.title", "§cContract Failed"),
                            messages.getString("titles.expired.subtitle", "§7Time ran out!"),
                            10, 60, 20
                    );
                    plugin.getLogger().info("[Contract] Player " + player.getName() + "'s contract expired.");
                    startCooldown(uuid);
                    cancel();
                    return;
                }

                double progress = timeLeft / (double) totalDurationMillis;
                BossBar bar = bossBars.get(uuid);
                if (bar != null) {
                    if (timeLeft <= 10_000) {
                        bar.setColor(flashToggle ? BarColor.RED : BarColor.WHITE);
                        flashToggle = !flashToggle;
                    } else if (progress <= 1.0 / 6.0) {
                        bar.setColor(BarColor.RED);
                    } else if (progress <= 0.5) {
                        bar.setColor(BarColor.YELLOW);
                    } else {
                        bar.setColor(BarColor.GREEN);
                    }
                    bar.setProgress(progress);
                    updateBossBarTitle(player, contract, bar);
                }

                updateContractScoreboard(player, contract);
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
