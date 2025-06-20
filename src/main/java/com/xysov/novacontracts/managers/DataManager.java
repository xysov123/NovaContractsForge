package com.xysov.novacontracts.managers;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xysov.novacontracts.NovaContracts;
import com.xysov.novacontracts.contracts.ActiveContract;
import com.xysov.novacontracts.contracts.ContractTask;
import com.xysov.novacontracts.contracts.TaskType;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DataManager {

    private final NovaContracts plugin;
    private Connection connection;
    private final Gson gson = new Gson();

    public DataManager(NovaContracts plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    private void initDatabase() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "playerdata.db");
            if (!dbFile.exists()) plugin.getDataFolder().mkdirs();

            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_data (
                        uuid TEXT PRIMARY KEY,
                        reputation INTEGER NOT NULL DEFAULT 0,
                        cooldown LONG DEFAULT 0
                    );
                """);

                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS active_contracts (
                        uuid TEXT PRIMARY KEY,
                        contract_id TEXT NOT NULL,
                        tier TEXT NOT NULL,
                        start_time INTEGER NOT NULL,
                        duration INTEGER NOT NULL,
                        tasks_progress TEXT NOT NULL
                    );
                """);
            }

            plugin.getLogger().info("[SQLite] Player database initialized.");
        } catch (SQLException e) {
            plugin.getLogger().severe("[SQLite] Failed to initialize database:");
            e.printStackTrace();
        }
    }

    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        int rep = getReputation(uuid);
        long cooldown = getCooldown(uuid);

        plugin.getContractManager().setReputation(uuid, rep);
        plugin.getContractManager().getContractCooldowns().put(uuid, cooldown);
    }


    public void savePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        int reputation = plugin.getContractManager().getReputation(uuid);
        setReputation(uuid, reputation);
    }

    public void saveAll() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            savePlayerData(player);
        }
    }

    public void setReputation(UUID uuid, int reputation) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_data (uuid, reputation) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET reputation=excluded.reputation;
            """)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, reputation);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public int getReputation(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT reputation FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("reputation");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void setCooldown(UUID uuid, long cooldown) {
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO player_data (uuid, cooldown) VALUES (?, ?)
                ON CONFLICT(uuid) DO UPDATE SET cooldown=excluded.cooldown;
            """)) {
            ps.setString(1, uuid.toString());
            ps.setLong(2, cooldown);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public long getCooldown(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT cooldown FROM player_data WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getLong("cooldown");
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0L;
    }

    private static class TaskProgressDTO {
        String specific;
        int progress;

        TaskProgressDTO(String specific, int progress) {
            this.specific = specific;
            this.progress = progress;
        }
    }

    public void removeActiveContract(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("DELETE FROM active_contracts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void saveActiveContract(UUID uuid, ActiveContract contract) {
        try (PreparedStatement ps = connection.prepareStatement("""
            INSERT INTO active_contracts (uuid, contract_id, tier, start_time, duration, tasks_progress)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                contract_id=excluded.contract_id,
                tier=excluded.tier,
                start_time=excluded.start_time,
                duration=excluded.duration,
                tasks_progress=excluded.tasks_progress;
            """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, contract.getContractId());
            ps.setString(3, contract.getTier());
            ps.setLong(4, contract.getStartTime());
            ps.setLong(5, contract.getDuration());

            String tasksJson = serializeTasksProgress(contract.getTasks());
            ps.setString(6, tasksJson);

            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public ActiveContract loadActiveContract(UUID uuid) {
        try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM active_contracts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String contractId = rs.getString("contract_id");
                String tier = rs.getString("tier");
                long startTime = rs.getLong("start_time");
                int duration = rs.getInt("duration");
                String tasksJson = rs.getString("tasks_progress");

                List<ContractTask> tasks = deserializeTasksProgress(tasksJson);

                return new ActiveContract(uuid, tier, contractId, startTime, duration, tasks);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    private String serializeTasksProgress(List<ContractTask> tasks) {
        List<ContractTask.ContractTaskDTO> dtos = new ArrayList<>();
        for (ContractTask task : tasks) {
            ContractTask.ContractTaskDTO dto = new ContractTask.ContractTaskDTO();
            dto.type = task.getType().toString();
            dto.specific = task.getSpecific();
            dto.listSpecific = task.getListSpecific();
            dto.requiredAmount = task.getRequiredAmount();
            dto.minLevel = task.getMinLevel();
            dto.displayName = task.getDisplayName();
            dto.progress = task.getProgress();
            dtos.add(dto);
        }
        return gson.toJson(dtos);
    }


    private List<ContractTask> deserializeTasksProgress(String json) {
        Type listType = new TypeToken<List<ContractTask.ContractTaskDTO>>() {}.getType();
        List<ContractTask.ContractTaskDTO> dtos = gson.fromJson(json, listType);
        List<ContractTask> tasks = new ArrayList<>();

        for (ContractTask.ContractTaskDTO dto : dtos) {
            TaskType type = TaskType.valueOf(dto.type); // convert string back to enum
            ContractTask task = new ContractTask(type, dto.specific, dto.listSpecific, dto.requiredAmount, dto.minLevel, dto.displayName);
            task.setProgress(dto.progress);
            tasks.add(task);
        }
        return tasks;
    }


}