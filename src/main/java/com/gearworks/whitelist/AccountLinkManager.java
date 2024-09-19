package com.gearworks.whitelist;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class AccountLinkManager {

    private final DatabaseManager databaseManager;

    public AccountLinkManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    public void linkAccount(UUID uuid, String discordId, String username) {
        String sql = "REPLACE INTO linked_accounts (uuid, discord_id, is_whitelisted, username) VALUES (?, ?, ?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());
            statement.setString(2, discordId);
            statement.setBoolean(3, false); // Not manually whitelisted
            statement.setString(4, username); // Not manually whitelisted
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<String> getDiscordId(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT discord_id FROM linked_accounts WHERE uuid = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getString("discord_id");
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public CompletableFuture<UUID> getUUID(String discordId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid FROM linked_accounts WHERE discord_id = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, discordId);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return UUID.fromString(resultSet.getString("uuid"));
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
            return null;
        });
    }

    public CompletableFuture<Boolean> isWhitelisted(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT is_whitelisted FROM linked_accounts WHERE uuid = ?";

            try (Connection connection = databaseManager.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {

                statement.setString(1, uuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getBoolean("is_whitelisted");
                    }
                }

            } catch (SQLException e) {
                e.printStackTrace();
            }
            return false;
        });
    }

    public void setWhitelisted(UUID uuid, boolean isWhitelisted) {
        String sql = "REPLACE INTO linked_accounts (uuid, is_whitelisted) VALUES (?, ?)";

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {

            statement.setString(1, uuid.toString());
            statement.setBoolean(2, isWhitelisted);
            statement.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}