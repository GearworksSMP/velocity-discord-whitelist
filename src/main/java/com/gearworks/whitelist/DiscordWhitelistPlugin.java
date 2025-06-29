package com.gearworks.whitelist;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import com.velocitypowered.api.command.CommandManager;
import org.slf4j.Logger;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Optional;
import java.util.UUID;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Plugin(id = "whitelist", name = "Gearworks Whitelist", version = "1.0.0", authors = {"uberswe"})
public class DiscordWhitelistPlugin {

    private final Logger logger;
    private DiscordBot discordBot;
    private DatabaseManager databaseManager;
    private Set<String> whitelistedServers;
    private CodeManager codeManager;
    private List<String> requiredRoleIds;
    // Map to track streaming mode status per server
    private final ConcurrentMap<String, Boolean> streamingServers = new ConcurrentHashMap<>();

    // Cache for whitelisted players (UUID -> timestamp of last successful connection)
    private final ConcurrentMap<UUID, Long> whitelistedPlayersCache = new ConcurrentHashMap<>();

    // Cache for last connected server (UUID -> server name and timestamp)
    private final ConcurrentMap<UUID, LastServerInfo> lastServerCache = new ConcurrentHashMap<>();

    // One week in milliseconds
    private static final long ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000L;

    // One month in milliseconds
    private static final long ONE_MONTH_MS = 30 * 24 * 60 * 60 * 1000L;

    // Class to store server info and timestamp
    private static class LastServerInfo {
        private final String serverName;
        private final long timestamp;

        public LastServerInfo(String serverName) {
            this.serverName = serverName;
            this.timestamp = System.currentTimeMillis();
        }

        public String getServerName() {
            return serverName;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    // UUID of uberswe
    private static final UUID CONTENT_CREATOR_UUID = UUID.fromString("eacc6702-0fe8-4ef2-9143-72d34c5c423e");

    @Inject
    private ProxyServer server; // Ensure you have the ProxyServer injected


    @Inject
    public DiscordWhitelistPlugin(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    // Method to toggle streaming mode
    public void setStreamingMode(String serverName, boolean enabled) {
        streamingServers.put(serverName, enabled);

        // If streaming mode is being enabled, kick other players from the server
        if (enabled) {
            kickNonContentCreatorPlayers(serverName);
        }
    }

    // Method to check if a server is in streaming mode
    public boolean isStreamingMode(String serverName) {
        return streamingServers.getOrDefault(serverName, false);
    }

    private ConfigurationNode loadConfiguration() {
        Path configPath = Paths.get("plugins", "DiscordWhitelist", "config.yml");
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .path(configPath)
                .nodeStyle(NodeStyle.FLOW)
                .build();

        try {
            if (Files.notExists(configPath)) {
                // Create default configuration
                ConfigurationNode defaultConfig = loader.createNode();

                // Add comments and set default values
                defaultConfig.node("database");
                defaultConfig.node("database", "host")
                        .set("localhost");
                defaultConfig.node("database", "port")
                        .set(3306);
                defaultConfig.node("database", "name")
                        .set("whitelist_db");
                defaultConfig.node("database", "user")
                        .set("dbuser");
                defaultConfig.node("database", "password")
                        .set("dbpassword");

                defaultConfig.node("discord");
                defaultConfig.node("discord", "token")
                        .set("YOUR_DISCORD_BOT_TOKEN");
                defaultConfig.node("discord", "requiredRoleIds")
                        .setList(String.class, Arrays.asList("ROLE_ID_1", "ROLE_ID_2"));

                defaultConfig.node("whitelisted_servers")
                        .setList(String.class, Arrays.asList("whitelisted_server1", "whitelisted_server2"));

                // Ensure the parent directories exist
                Files.createDirectories(configPath.getParent());

                // Save the default configuration to the file
                loader.save(defaultConfig);
                logger.info("Default configuration file created at " + configPath.toString());

                return defaultConfig;
            } else {
                // Load existing configuration
                return loader.load();
            }
        } catch (IOException e) {
            logger.error("Failed to load or create configuration", e);
            return loader.createNode();
        }
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        // Load configuration
        ConfigurationNode config = loadConfiguration();

        // Get database credentials
        String dbHost = config.node("database", "host").getString("localhost");
        int dbPort = config.node("database", "port").getInt(3306);
        String dbName = config.node("database", "name").getString("whitelist_db");
        String dbUser = config.node("database", "user").getString("dbuser");
        String dbPassword = config.node("database", "password").getString("dbpassword");

        // Get whitelisted servers
        List<String> serverList;
        try {
            serverList = config.node("whitelisted_servers").getList(String.class);
        } catch (SerializationException e) {
            logger.error("Failed to load whitelisted_servers from configuration", e);
            serverList = new ArrayList<>();
        }
        whitelistedServers = ImmutableSet.copyOf(serverList);

        // Get Discord credentials
        String discordToken = config.node("discord", "token").getString();

        try {
            requiredRoleIds = config.node("discord", "requiredRoleIds").getList(String.class);
        } catch (SerializationException e) {
            logger.error("Failed to load requiredRoleIds from configuration", e);
            requiredRoleIds = new ArrayList<>();
        }

        databaseManager = new DatabaseManager(dbHost, dbPort, dbName, dbUser, dbPassword);
        AccountLinkManager accountLinkManager = new AccountLinkManager(databaseManager);

        // Initialize CodeManager
        codeManager = new CodeManager();

        if (discordToken == null || requiredRoleIds == null || requiredRoleIds.isEmpty()) {
            logger.error("Discord token or required role IDs not set in configuration.");
            return;
        }

        // Initialize Discord bot
        discordBot = new DiscordBot(discordToken, codeManager, accountLinkManager);
        try {
            discordBot.start();
        } catch (LoginException e) {
            logger.error("Failed to login to Discord", e);
            return;
        }

        CommandManager commandManager = server.getCommandManager();
        // Register the /link command
        commandManager.register("link", new LinkCommand(codeManager));

        // Register the broadcast command
        CommandMeta meta = commandManager.metaBuilder("broadcast")
                .aliases("bc")
                .build();
        commandManager.register(meta, new BroadcastCommand(server));

        // Register event listeners with the updated requiredRoleIds
        server.getEventManager().register(this, new PlayerConnectionListener(
                accountLinkManager, discordBot.getJDA(), requiredRoleIds, whitelistedServers, logger, codeManager, this));

        // Register the streamingmode command
        commandManager.register("streamingmode", new StreamingModeCommand(this));

        logger.info("DiscordWhitelist has been enabled!");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (discordBot != null) {
            discordBot.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.shutdown();
        }
        logger.info("DiscordWhitelist has been disabled!");
    }

    // Getter for ProxyServer if needed
    public ProxyServer getProxyServer() {
        return server;
    }

    /**
     * Checks if a player is in the whitelist cache and the cache entry is still valid (less than 1 week old)
     * @param uuid The UUID of the player to check
     * @return true if the player is in the cache and the entry is still valid, false otherwise
     */
    public boolean isPlayerCached(UUID uuid) {
        Long timestamp = whitelistedPlayersCache.get(uuid);
        if (timestamp == null) {
            return false;
        }

        // Check if the cache entry is less than 1 week old
        long currentTime = System.currentTimeMillis();
        return (currentTime - timestamp) < ONE_WEEK_MS;
    }

    /**
     * Adds a player to the whitelist cache with the current timestamp
     * @param uuid The UUID of the player to add to the cache
     */
    public void cachePlayer(UUID uuid) {
        whitelistedPlayersCache.put(uuid, System.currentTimeMillis());
        logger.info("Player " + uuid + " added to whitelist cache");
    }

    /**
     * Stores the last server a player connected to
     * @param uuid The UUID of the player
     * @param serverName The name of the server
     */
    public void cacheLastServer(UUID uuid, String serverName) {
        lastServerCache.put(uuid, new LastServerInfo(serverName));
        logger.info("Cached last server " + serverName + " for player " + uuid);
    }

    /**
     * Checks if a player has a cached last server and if the cache entry is still valid (less than 1 month old)
     * @param uuid The UUID of the player to check
     * @return true if the player has a valid cached last server, false otherwise
     */
    public boolean hasValidLastServer(UUID uuid) {
        LastServerInfo info = lastServerCache.get(uuid);
        if (info == null) {
            return false;
        }

        // Check if the cache entry is less than 1 month old
        long currentTime = System.currentTimeMillis();
        return (currentTime - info.getTimestamp()) < ONE_MONTH_MS;
    }

    /**
     * Gets the name of the last server a player connected to
     * @param uuid The UUID of the player
     * @return The name of the last server, or null if not found or expired
     */
    public String getLastServerName(UUID uuid) {
        if (!hasValidLastServer(uuid)) {
            return null;
        }
        return lastServerCache.get(uuid).getServerName();
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();
        String serverName = event.getOriginalServer().getServerInfo().getName();

        // Check if the server is in streaming mode
        if (isStreamingMode(serverName)) {
            if (playerUuid.equals(CONTENT_CREATOR_UUID)) {
                // Allow the content creator to connect
                return;
            } else {
                // Deny connection and send message
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(Component.text("Server is being used for content creation, please check back later.", NamedTextColor.RED));
                return;
            }
        }

        // Only redirect if the player is first joining the proxy (doesn't have a current server yet)
        // This prevents redirection when using /server commands
        if (!player.getCurrentServer().isPresent() && this.hasValidLastServer(playerUuid)) {
            String lastServer = this.getLastServerName(playerUuid);

            // Don't redirect if player is explicitly trying to connect to a specific server
            // or if they're already connecting to their last server
            if (!serverName.equals(lastServer)) {
                // Get the server instance for the last connected server
                Optional<RegisteredServer> targetServer = server.getServer(lastServer);

                if (targetServer.isPresent()) {
                    // Check if the target server is in streaming mode
                    if (isStreamingMode(lastServer) && !playerUuid.equals(CONTENT_CREATOR_UUID)) {
                        // Don't redirect to a streaming server if player is not the content creator
                        logger.info("Not redirecting player " + player.getUsername() + " to " + lastServer + " because it's in streaming mode");
                        return;
                    }

                    // Redirect the player to their last connected server
                    event.setResult(ServerPreConnectEvent.ServerResult.allowed(targetServer.get()));
                    logger.info("Redirecting player " + player.getUsername() + " to their last server: " + lastServer);
                } else {
                    logger.warn("Last server " + lastServer + " for player " + player.getUsername() + " not found");
                }
            }
        }
    }

    private void kickNonContentCreatorPlayers(String serverName) {
        // Get the server instance
        Optional<RegisteredServer> optionalServer = server.getServer(serverName);
        if (!optionalServer.isPresent()) {
            logger.warn("Server not found: " + serverName);
            return;
        }

        RegisteredServer registeredServer = optionalServer.get();

        // Schedule the task on the main thread
        server.getScheduler().buildTask(this, () -> {
            UUID contentCreatorUUID = UUID.fromString("eacc6702-0fe8-4ef2-9143-72d34c5c423e");

            // Iterate over all players connected to the server
            for (Player player : registeredServer.getPlayersConnected()) {
                if (!player.getUniqueId().equals(contentCreatorUUID)) {
                    // Disconnect the player with a custom message
                    player.disconnect(Component.text("Server is being used for content creation, please check back later.", NamedTextColor.RED));
                    logger.info("Kicked player " + player.getUsername() + " from server " + serverName + " due to streaming mode activation.");
                }
            }
        }).schedule();
    }

    @Subscribe
    public void onCommandExecute(CommandExecuteEvent event) {
        if (event.getCommandSource() instanceof Player) {
            Player player = (Player) event.getCommandSource();
            String command = event.getCommand();

            RegisteredServer server = player.getCurrentServer().get().getServer();
            // Check if the command is the one you want to block typing for
            if (command.startsWith("/server") && !server.getServerInfo().getName().equals("lobby")) {
                // Cancel the command to block it from being displayed
                event.setResult(CommandExecuteEvent.CommandResult.denied());
            }
        }
    }
}
