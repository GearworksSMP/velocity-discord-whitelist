package com.gearworks.whitelist;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
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

        // Register the /link command
        server.getCommandManager().register("link", new LinkCommand(codeManager));

        // Register event listeners with the updated requiredRoleIds
        server.getEventManager().register(this, new PlayerConnectionListener(
                accountLinkManager, discordBot.getJDA(), requiredRoleIds, whitelistedServers, logger));

        // Register the streamingmode command
        CommandManager commandManager = server.getCommandManager();
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

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        String serverName = event.getOriginalServer().getServerInfo().getName();

        // Check if the server is in streaming mode
        if (isStreamingMode(serverName)) {
            if (player.getUniqueId().equals(CONTENT_CREATOR_UUID)) {
                // Allow the content creator to connect
                return;
            } else {
                // Deny connection and send message
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
                player.sendMessage(Component.text("Server is being used for content creation, please check back later.", NamedTextColor.RED));
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
}
