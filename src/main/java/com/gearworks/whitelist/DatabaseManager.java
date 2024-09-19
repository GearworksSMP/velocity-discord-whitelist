package com.gearworks.whitelist;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {

    private HikariDataSource dataSource;

    public DatabaseManager(String host, int port, String database, String user, String password) {
        HikariConfig config = new HikariConfig();

        // Set the JDBC URL
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?serverTimezone=UTC";
        config.setJdbcUrl(jdbcUrl);

        // Set the driver class name explicitly
        config.setDriverClassName("com.mysql.cj.jdbc.Driver"); // For MySQL Connector/J 8.x

        // Set database credentials
        config.setUsername(user);
        config.setPassword(password);

        // Optional settings
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        dataSource = new HikariDataSource(config);

        // Initialize the database tables
        initDatabase();
    }

    private void initDatabase() {
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {

            // Create table for linked accounts and manual whitelisting
            String sql = "CREATE TABLE IF NOT EXISTS linked_accounts (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "discord_id VARCHAR(64)," +
                    "is_whitelisted BOOLEAN DEFAULT FALSE," +
                    "username VARCHAR(256)" +
                    ")";
            statement.executeUpdate(sql);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}