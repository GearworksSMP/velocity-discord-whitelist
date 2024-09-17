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

    public PlayerConnectionListener(AccountLinkManager accountLinkManager, JDA jda, List<String> requiredRoleIds,
                                    Set<String> whitelistedServers, Logger logger) {
        this.accountLinkManager = accountLinkManager;
        this.jda = jda;
        this.requiredRoleIds = requiredRoleIds;
        this.whitelistedServers = whitelistedServers;
        this.logger = logger;
    }

    @Subscribe
    public void onServerConnected(ServerConnectedEvent event) {
        String serverName = event.getServer().getServerInfo().getName();

        if (!whitelistedServers.contains(serverName)) {
            // The server is public; no need to check for whitelisting
            return;
        }

        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        accountLinkManager.isWhitelisted(uuid).thenAccept(isWhitelisted -> {
            if (isWhitelisted) {
                // Player is manually whitelisted; allow them to connect
                return;
            }

            accountLinkManager.getDiscordId(uuid).thenAccept(discordId -> {
                if (discordId == null) {
                    // Kick the player for not linking their Discord account
                    player.disconnect(Component.text("You have not linked your Discord account. Use /link in-game to get started."));
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
                            player.disconnect(Component.text("You do not have the required Discord role to join this server."));
                        }
                        // If the player has any of the roles, do nothing and allow them to connect

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