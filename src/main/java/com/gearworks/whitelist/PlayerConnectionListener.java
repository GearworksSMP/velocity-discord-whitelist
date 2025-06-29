package com.gearworks.whitelist;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.*;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class PlayerConnectionListener {

    private final AccountLinkManager accountLinkManager;
    private final JDA jda;
    private final List<String> requiredRoleIds;
    private final Set<String> whitelistedServers;
    private final Logger logger;
    private final CodeManager codeManager;
    private final DiscordWhitelistPlugin plugin;

    public PlayerConnectionListener(AccountLinkManager accountLinkManager, JDA jda, List<String> requiredRoleIds,
                                    Set<String> whitelistedServers, Logger logger, CodeManager codeManager,
                                    DiscordWhitelistPlugin plugin) {
        this.accountLinkManager = accountLinkManager;
        this.jda = jda;
        this.requiredRoleIds = requiredRoleIds;
        this.whitelistedServers = whitelistedServers;
        this.logger = logger;
        this.codeManager = codeManager;
        this.plugin = plugin;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        String serverName = event.getServer().getServerInfo().getName();
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Cache the last server the player connected to, regardless of whether it's whitelisted
        plugin.cacheLastServer(uuid, serverName);
        logger.info("Player " + player.getUsername() + " (" + uuid + ") connected to server " + serverName);

        if (!whitelistedServers.contains(serverName)) {
            // The server is public; no need to check for whitelisting
            return;
        }

        // Check if player is in the cache
        if (plugin.isPlayerCached(uuid)) {
            logger.info("Player " + player.getUsername() + " (" + uuid + ") is in the whitelist cache; allowing connection");
            return;
        }

        accountLinkManager.isWhitelisted(uuid).thenAccept(isWhitelisted -> {
            if (isWhitelisted) {
                // Player is manually whitelisted; allow them to connect
                // Add to cache for future connections
                plugin.cachePlayer(uuid);
                return;
            }

            accountLinkManager.getDiscordId(uuid).thenAccept(discordId -> {
                if (discordId == null) {
                    // Kick the player for not linking their Discord account
                    String code = codeManager.generateCode(player.getUniqueId());
                    player.disconnect(Component.text("Use this code to link your Discord account: " + code + "\n" +
                            "Send this code in the #bot-commands channel in Uberswe's Discord.\n" +
                            "Make sure you are a paid Patreon member and that your Patreon account is linked to your Discord\n" +
                            "Patreon: https://www.patreon.com/c/Uberswe\n" +
                            "Discord: https://discord.gg/NQJuhb6stv"));
                    return;
                }

                // Fetch the Discord member asynchronously
                jda.retrieveUserById(discordId).queue(user -> {
                    // Replace with your guild retrieval logic
                    Guild guild = jda.getGuilds().get(0);

                    guild.retrieveMember(user).queue(member -> {
                        boolean hasRequiredRole = member.getRoles().stream()
                                .anyMatch(role -> requiredRoleIds.contains(role.getId()));

                        if (!hasRequiredRole) {
                            // Player does not have any of the required roles; kick them
                            player.disconnect(Component.text("Make sure you are a paid Patreon member and that your Patreon account is linked to your Discord\n" +
                                    "Patreon: https://www.patreon.com/c/Uberswe\n" +
                                    "Discord: https://discord.gg/NQJuhb6stv"));
                        } else {
                            // Player has the required role; add to cache for future connections
                            plugin.cachePlayer(uuid);
                            logger.info("Player " + player.getUsername() + " (" + uuid + ") has required role; added to whitelist cache");
                        }

                    }, throwable -> {
                        // Error retrieving member
                        logger.error("Failed to retrieve Discord member for user ID: " + discordId, throwable);
                        player.disconnect(Component.text("An error occurred while verifying your Discord role. Please try again later."));
                    });
                }, throwable -> {
                    // Error retrieving user
                    logger.error("Failed to retrieve Discord user with ID: " + discordId, throwable);
                    player.disconnect(Component.text("An error occurred while verifying your Discord account. Please try again later."));
                });
            });
        });
    }
}
