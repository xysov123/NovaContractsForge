package com.xysov.novacontracts.managers;

import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import com.xysov.novacontracts.contracts.ContractTask;
import com.xysov.novacontracts.contracts.TaskType;
import com.xysov.novacontracts.utils.ConfigLoader;
import com.xysov.novacontracts.utils.LeaderboardEntry;
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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class ContractManager {

    private final NovaContracts plugin;

    private final Map<UUID, ActiveContract> activeContracts = new HashMap<>();
    private final Map<UUID, Long> contractCooldowns = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Scoreboard> contractScoreboards = new HashMap<>();
    private final Map<UUID, Integer> playerReputation = new HashMap<>();
    private long leaderboardLastRefresh = 0L;
    private final long leaderboardRefreshInterval = 5 * 60 * 1000L;
    private List<PlayerReputationEntry> cachedTopReputation = new ArrayList<>();

    public ContractManager(NovaContracts plugin) {
        this.plugin = plugin;
    }

    public void acceptContract(Player player, String tier) {
        UUID uuid = player.getUniqueId();
        FileConfiguration messages = ConfigLoader.getMessagesConfig();
        int rep = getReputation(uuid);

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

        if (tier.equalsIgnoreCase("medium") && rep < 50) {
            player.sendMessage("§cYou need 50 Reputation to accept Medium contracts.");
            return;
        }
        if (tier.equalsIgnoreCase("hard") && rep < 150) {
            player.sendMessage("§cYou need 150 Reputation to accept Hard contracts.");
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
        List<String> eligibleContracts = new ArrayList<>();

        for (String id : availableContracts) {
            ConfigurationSection section = contractsConfig.getConfigurationSection("contracts." + id);
            if (section == null) continue;

            int requirement = section.getInt("reputation-requirement", 0);
            if (rep >= requirement) {
                eligibleContracts.add(id);
            }
        }

        if (eligibleContracts.isEmpty()) {
            player.sendMessage(colorMsg(messages.getString("no_contracts_tier").replace("%tier%", capitalize(tier))));
            plugin.getLogger().info("[Contract] No eligible contracts found for tier " + tier + " with player's rep: " + rep);
            return;
        }

        String contractId = eligibleContracts.get(new Random().nextInt(eligibleContracts.size()));
        ConfigurationSection contractSection = contractsConfig.getConfigurationSection("contracts." + contractId);
        if (contractSection == null) {
            player.sendMessage(colorMsg(messages.getString("error_loading_contract").replace("%contractId%", contractId)));
            plugin.getLogger().warning("Error loading contract section: " + contractId);
            return;
        }

        long duration = contractSection.getLong("duration", 600);
        List<ContractTask> allTasks = parseTasks(contractSection.getConfigurationSection("tasks"), contractId);
        if (allTasks.isEmpty()) {
            player.sendMessage(colorMsg(messages.getString("tasks_missing_or_invalid").replace("%contractId%", contractId)));
            plugin.getLogger().warning("Tasks missing or invalid for contract: " + contractId);
            return;
        }

        ContractTask chosenTask = allTasks.get(new Random().nextInt(allTasks.size()));
        List<ContractTask> chosenTasks = Collections.singletonList(chosenTask);

        ActiveContract contract = new ActiveContract(uuid, tier, contractId, System.currentTimeMillis(), duration, chosenTasks);
        activeContracts.put(uuid, contract);

        plugin.getDataManager().saveActiveContract(uuid, contract);
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
            player.sendMessage(colorMsg(messages.getString("view_no_active", "&cYou don't have an active contract.")));
            return;
        }

        player.sendMessage(colorMsg("&8&m----------------------"));
        player.sendMessage(colorMsg("&a&lActive Contract &7(Tier: &f" + capitalize(contract.getTier()) + "&7)"));

        int taskNumber = 1;
        for (ContractTask task : contract.getTasks()) {
            if (task.isComplete()) continue;

            int remaining = Math.max(0, task.getRequiredAmount() - task.getProgress());
            String displayName = task.getDisplayName(); // Already set from config when parsing

            String taskLine = messages.getString("view_task_format",
                            "&e• &f%task% &8- &c%x% left")
                    .replace("%task%", displayName)
                    .replace("%x%", String.valueOf(remaining));

            player.sendMessage(colorMsg(taskLine));
            taskNumber++;
        }

        player.sendMessage(colorMsg("&8&m----------------------"));
    }


    private String formatTaskForDisplay(ContractTask task, int taskNumber) {
        String typeName = task.getType().name().replace('_', ' '); // e.g. CATCH_POKEMON → "CATCH POKEMON"
        String specific = (task.getSpecific() != null) ? task.getSpecific() : "Multiple";

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

        ActiveContract contract = activeContracts.remove(uuid);
        stopBossBar(uuid);
        removeContractScoreboard(uuid);

        // Reputation penalty
        FileConfiguration contractsConfig = ConfigLoader.getContractsConfig();
        int reputationLoss = 0;
        ConfigurationSection contractSection = contractsConfig.getConfigurationSection("contracts." + contract.getContractId());
        if (contractSection != null) {
            ConfigurationSection tasksSection = contractSection.getConfigurationSection("tasks");
            if (tasksSection != null) {
                for (String key : tasksSection.getKeys(false)) {
                    ConfigurationSection task = tasksSection.getConfigurationSection(key);
                    if (task != null) {
                        reputationLoss += task.getInt("reputation-fail", 0);
                    }
                }
            }
        }
        removeReputation(uuid, reputationLoss);
        plugin.getLogger().info("[DEBUG] Reputation loss for " + player.getName() + " on cancel: " + reputationLoss);

        plugin.getDataManager().removeActiveContract(uuid);

        startCooldown(uuid);
        player.sendMessage(colorMsg(messages.getString("cancelled_contract")));
        plugin.getLogger().info("[Contract] Player " + player.getName() + " cancelled their contract.");
        plugin.getDataManager().savePlayerData(player);
    }



    public void resetCooldown(String playerName) {
        Player target = Bukkit.getPlayerExact(playerName);
        FileConfiguration messages = ConfigLoader.getMessagesConfig();
        if (target != null) {
            UUID uuid = target.getUniqueId();
            contractCooldowns.remove(uuid);
            plugin.getDataManager().setCooldown(uuid, 0L);  // Reset cooldown in DB

            target.sendMessage(colorMsg(messages.getString("cooldown_reset").replace("%player%", target.getName())));
            plugin.getLogger().info("[Contract] Cooldown reset for player " + target.getName());
        }
    }

    public Map<UUID, Long> getContractCooldowns() {
        return contractCooldowns;
    }

    public void failContract(Player player) {
        UUID uuid = player.getUniqueId();
        ActiveContract contract = activeContracts.remove(uuid);
        if (contract == null) return;

        stopBossBar(uuid);
        removeContractScoreboard(uuid);

        FileConfiguration contractsConfig = ConfigLoader.getContractsConfig();
        int reputationLoss = 0;
        ConfigurationSection contractSection = contractsConfig.getConfigurationSection("contracts." + contract.getContractId());
        if (contractSection != null) {
            ConfigurationSection tasksSection = contractSection.getConfigurationSection("tasks");
            if (tasksSection != null) {
                for (String key : tasksSection.getKeys(false)) {
                    ConfigurationSection task = tasksSection.getConfigurationSection(key);
                    if (task != null) {
                        reputationLoss += task.getInt("reputation-fail", 0);
                    }
                }
            }
        }

        removeReputation(uuid, reputationLoss);

        plugin.getDataManager().removeActiveContract(uuid);

        plugin.getDataManager().savePlayerData(player);

        FileConfiguration messages = ConfigLoader.getMessagesConfig();
        sendTitle(
                player,
                messages.getString("titles.expired.title", "§cContract Failed"),
                messages.getString("titles.expired.subtitle", "§7Time ran out!"),
                10, 60, 20
        );

        plugin.getLogger().info("[Contract] Player " + player.getName() + "'s contract failed.");
        plugin.getLogger().info("[DEBUG] Reputation loss for " + player.getName() + ": " + reputationLoss);

        startCooldown(uuid);
    }

    public void completeContract(Player player) {
        UUID uuid = player.getUniqueId();
        ActiveContract contract = activeContracts.get(uuid);
        if (contract == null) return;

        plugin.getRewardManager().giveReward(player, contract);

        // Reputation
        int reputationGain = 0;
        FileConfiguration contractsConfig = ConfigLoader.getContractsConfig();
        ConfigurationSection contractSection = contractsConfig.getConfigurationSection("contracts." + contract.getContractId());
        if (contractSection != null) {
            ConfigurationSection tasksSection = contractSection.getConfigurationSection("tasks");
            if (tasksSection != null) {
                for (String key : tasksSection.getKeys(false)) {
                    ConfigurationSection task = tasksSection.getConfigurationSection(key);
                    if (task != null) {
                        reputationGain += task.getInt("reputation", 0);
                    }
                }
            }
        }

        addReputation(uuid, reputationGain);

        activeContracts.remove(uuid);
        contractCooldowns.remove(uuid);

        plugin.getDataManager().removeActiveContract(uuid);

        plugin.getDataManager().savePlayerData(player);

        plugin.getLogger().info("[DEBUG] Reputation gain for " + player.getName() + ": " + reputationGain);

        sendTitle(player, "&aContract Complete", "&fWell done!", 10, 60, 20);
        startCooldown(uuid);
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
        if (!contractCooldowns.containsKey(uuid)) {
            long dbCooldown = plugin.getDataManager().getCooldown(uuid);
            if (dbCooldown > System.currentTimeMillis()) {
                contractCooldowns.put(uuid, dbCooldown);
                return true;
            }
            return false;
        }
        return contractCooldowns.get(uuid) > System.currentTimeMillis();
    }

    public void startCooldown(UUID uuid) {
        int cooldownSec = plugin.getConfig().getInt("global-cooldown", 600);
        long cooldownTime = System.currentTimeMillis() + cooldownSec * 1000L;
        contractCooldowns.put(uuid, cooldownTime);
        plugin.getDataManager().setCooldown(uuid, cooldownTime);
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
                    failContract(player);  // reuse your existing failContract logic, including rep loss, cleanup, cooldown, save etc.
                    cancel();
                    return;
                }

                double progress = timeLeft / (double) totalDurationMillis;
                BossBar bar = bossBars.get(uuid);
                if (bar != null) {
                    if (timeLeft <= 10_000) {
                        bar.setColor(flashToggle ? BarColor.RED : BarColor.YELLOW);
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
        }.runTaskTimer(plugin, 0L, 8L);
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

    public void removeReputation(UUID uuid, int amount) {
        int before = getReputation(uuid);
        setReputation(uuid, before - amount);
        int after = getReputation(uuid);
        plugin.getLogger().info("[DEBUG] Reputation changed for " + uuid + ": " + before + " -> " + after);
        updateCachedTopReputation(10);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage("§cYou lost " + (before - after) + " Reputation. §7Total: §f" + after);
        }
    }

    public void addReputation(UUID uuid, int amount) {
        int before = getReputation(uuid);
        int after = before + amount;
        setReputation(uuid, after);
        plugin.getLogger().info("[DEBUG] Reputation added for " + uuid + ": " + before + " -> " + Math.max(0, after));
        updateCachedTopReputation(10);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            player.sendMessage("§aYou earned " + (after - before) + " Reputation! §7Total: §f" + after);
        }
    }

    public int getReputation(UUID uuid) {
        return playerReputation.getOrDefault(uuid, 0);
    }

    public void setReputation(UUID uuid, int amount) {
        int clampedAmount = Math.max(0, amount);
        int before = playerReputation.getOrDefault(uuid, 0);
        playerReputation.put(uuid, clampedAmount);
        plugin.getLogger().info("[DEBUG] Reputation set for " + uuid + ": " + before + " -> " + clampedAmount);
        updateCachedTopReputation(10);
    }

    public void refreshLeaderboardAsync() {
        if (System.currentTimeMillis() - leaderboardLastRefresh < leaderboardRefreshInterval) return;

        leaderboardLastRefresh = System.currentTimeMillis();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlayerReputationEntry> newList = new ArrayList<>();
            try (Connection conn = plugin.getDataManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid, reputation FROM player_data ORDER BY reputation DESC LIMIT 10")) {
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    int rep = rs.getInt("reputation");
                    String name = Bukkit.getOfflinePlayer(uuid).getName();
                    if (name == null) name = "Unknown";
                    newList.add(new PlayerReputationEntry(name, rep));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to refresh reputation leaderboard:");
                e.printStackTrace();
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                cachedTopReputation = newList;
                plugin.getLogger().info("[Leaderboard] Reputation leaderboard updated with top " + newList.size() + " players.");
            });
        });
    }


    public void updateCachedTopReputation(int limit) {
        List<PlayerReputationEntry> list = new ArrayList<>();

        for (UUID uuid : playerReputation.keySet()) {
            String name = Bukkit.getOfflinePlayer(uuid).getName();
            if (name == null) continue;
            int rep = playerReputation.get(uuid);
            list.add(new PlayerReputationEntry(name, rep));
        }

        list.sort((a, b) -> Integer.compare(b.getReputation(), a.getReputation()));

        if (list.size() > limit) {
            cachedTopReputation = list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
        } else {
            cachedTopReputation = list;
        }
    }

    public List<PlayerReputationEntry> getCachedTopReputation() {
        return cachedTopReputation;
    }

    public static class PlayerReputationEntry {
        private final String playerName;
        private final int reputation;

        public PlayerReputationEntry(String playerName, int reputation) {
            this.playerName = playerName;
            this.reputation = reputation;
        }

        public String getPlayerName() {
            return playerName;
        }

        public int getReputation() {
            return reputation;
        }
    }
}
