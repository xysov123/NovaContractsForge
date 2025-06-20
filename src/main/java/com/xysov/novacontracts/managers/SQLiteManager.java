package com.xysov.novacontracts.managers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteManager {
    private final String url;

    public SQLiteManager(String pluginDataFolderPath) {
        this.url = "jdbc:sqlite:" + pluginDataFolderPath + "/novacontracts.db";
        setupTables();
    }

    private void setupTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            String sql = """
                    CREATE TABLE IF NOT EXISTS player_data (
                        uuid TEXT PRIMARY KEY,
                        reputation INTEGER NOT NULL,
                        cooldown_end BIGINT
                    );
                    """;
            stmt.execute(sql);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url);
    }
}
