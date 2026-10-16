package com.thatguycy.worlddynamicsengine.manager.storage;

import com.thatguycy.worlddynamicsengine.WorldDynamicsEngine;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * 统一管理 SQLite/MySQL 连接与分类实体快照，负责序列化读写，不热切换运行中的连接。
 */
public class DataStorageManager {
    /**
     * 定义 SQLite 与 MySQL 两种持久化后端。
     */
    public enum StorageType {
        SQLITE,
        MYSQL;

        public static StorageType fromConfig(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return SQLITE;
            }

            try {
                return StorageType.valueOf(rawValue.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                return SQLITE;
            }
        }
    }

    private static final String TABLE_NAME = "wde_entities";

    private final WorldDynamicsEngine plugin;
    private final StorageType storageType;
    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DataStorageManager(WorldDynamicsEngine plugin) {
        this.plugin = plugin;
        this.storageType = StorageType.fromConfig(plugin.getConfig().getString("storage.type", "sqlite"));

        if (storageType == StorageType.MYSQL) {
            String host = plugin.getConfig().getString("storage.mysql.host", "127.0.0.1");
            int port = plugin.getConfig().getInt("storage.mysql.port", 3306);
            String database = plugin.getConfig().getString("storage.mysql.database", "worlddynamics_engine");
            String parameters = plugin.getConfig().getString("storage.mysql.parameters", "useSSL=false&characterEncoding=utf8&serverTimezone=UTC");
            this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?" + parameters;
            this.username = plugin.getConfig().getString("storage.mysql.username", "root");
            this.password = plugin.getConfig().getString("storage.mysql.password", "");
        } else {
            String relativePath = plugin.getConfig().getString("storage.sqlite.file", "data/worlddynamics-engine.db");
            File databaseFile = new File(plugin.getDataFolder(), relativePath);
            File parent = databaseFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            this.jdbcUrl = "jdbc:sqlite:" + databaseFile.getAbsolutePath();
            this.username = "";
            this.password = "";
        }
    }

    public void initialize() {
        try {
            if (storageType == StorageType.MYSQL) {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } else {
                Class.forName("org.sqlite.JDBC");
            }
            createTablesIfNeeded();
            plugin.getLogger().info("Data storage initialized with " + storageType.name().toLowerCase() + ".");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize data storage: " + e.getMessage(), e);
        }
    }

    public StorageType getStorageType() {
        return storageType;
    }

    public synchronized <T extends ConfigurationSerializable> Map<String, T> loadStringKeyed(String category, Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        String sql = "SELECT entity_key, payload FROM " + TABLE_NAME + " WHERE category = ? ORDER BY entity_key";

        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String key = resultSet.getString("entity_key");
                    T value = deserialize(resultSet.getString("payload"), type);
                    if (value != null) {
                        result.put(key, value);
                    }
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load category " + category + ": " + e.getMessage(), e);
        }

        return result;
    }

    public synchronized <T extends ConfigurationSerializable> Map<UUID, T> loadUuidKeyed(String category, Class<T> type) {
        Map<UUID, T> result = new LinkedHashMap<>();
        for (Map.Entry<String, T> entry : loadStringKeyed(category, type).entrySet()) {
            try {
                result.put(UUID.fromString(entry.getKey()), entry.getValue());
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid UUID key in category " + category + ": " + entry.getKey());
            }
        }
        return result;
    }

    public synchronized <T extends ConfigurationSerializable> void saveStringKeyed(String category, Map<String, T> data) {
        String deleteSql = "DELETE FROM " + TABLE_NAME + " WHERE category = ?";
        String insertSql = "INSERT INTO " + TABLE_NAME + " (category, entity_key, payload, updated_at) VALUES (?, ?, ?, ?)";

        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement deleteStatement = connection.prepareStatement(deleteSql)) {
                deleteStatement.setString(1, category);
                deleteStatement.executeUpdate();
            }

            try (PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
                for (Map.Entry<String, T> entry : data.entrySet()) {
                    insertStatement.setString(1, category);
                    insertStatement.setString(2, entry.getKey());
                    insertStatement.setString(3, serialize(entry.getValue()));
                    insertStatement.setLong(4, System.currentTimeMillis());
                    insertStatement.addBatch();
                }
                insertStatement.executeBatch();
            }

            connection.commit();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save category " + category + ": " + e.getMessage(), e);
        }
    }

    public synchronized <T extends ConfigurationSerializable> void saveUuidKeyed(String category, Map<UUID, T> data) {
        Map<String, T> stringKeyed = new LinkedHashMap<>();
        for (Map.Entry<UUID, T> entry : data.entrySet()) {
            stringKeyed.put(entry.getKey().toString(), entry.getValue());
        }
        saveStringKeyed(category, stringKeyed);
    }

    public synchronized void saveEntity(String category, String key, ConfigurationSerializable value) {
        String sql = "INSERT INTO " + TABLE_NAME + " (category, entity_key, payload, updated_at) VALUES (?, ?, ?, ?)"
                + (storageType == StorageType.MYSQL
                ? " ON DUPLICATE KEY UPDATE payload = VALUES(payload), updated_at = VALUES(updated_at)"
                : " ON CONFLICT(category, entity_key) DO UPDATE SET payload = excluded.payload, updated_at = excluded.updated_at");
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category);
            statement.setString(2, key);
            statement.setString(3, serialize(value));
            statement.setLong(4, System.currentTimeMillis());
            statement.executeUpdate();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to save entity " + category + "/" + key + ": " + ex.getMessage(), ex);
        }
    }

    private void createTablesIfNeeded() throws SQLException {
        String ddl = storageType == StorageType.MYSQL
                ? "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "category VARCHAR(64) NOT NULL," +
                "entity_key VARCHAR(191) NOT NULL," +
                "payload LONGTEXT NOT NULL," +
                "updated_at BIGINT NOT NULL," +
                "PRIMARY KEY (category, entity_key)" +
                ")"
                : "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "category TEXT NOT NULL," +
                "entity_key TEXT NOT NULL," +
                "payload TEXT NOT NULL," +
                "updated_at INTEGER NOT NULL," +
                "PRIMARY KEY (category, entity_key)" +
                ")";

        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl);
        }
    }

    private Connection openConnection() throws SQLException {
        if (storageType == StorageType.MYSQL) {
            return DriverManager.getConnection(jdbcUrl, username, password);
        }
        return DriverManager.getConnection(jdbcUrl);
    }

    private String serialize(ConfigurationSerializable value) {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.set("data", value);
        return configuration.saveToString();
    }

    private <T extends ConfigurationSerializable> T deserialize(String payload, Class<T> type) {
        try {
            YamlConfiguration configuration = new YamlConfiguration();
            configuration.loadFromString(payload);
            Object data = configuration.get("data");
            if (type.isInstance(data)) {
                return type.cast(data);
            }
            return null;
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().warning("Failed to deserialize database payload for " + type.getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }
}
