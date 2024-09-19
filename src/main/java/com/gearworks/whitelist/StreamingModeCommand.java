package com.gearworks.whitelist;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class StreamingModeCommand implements SimpleCommand {

    private final DiscordWhitelistPlugin plugin;

    public StreamingModeCommand(DiscordWhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        CommandSource source = invocation.source();
        return source.hasPermission("discordwhitelist.streamingmode");
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();

        if (args.length != 2) {
            source.sendMessage(Component.text("Usage: /streamingmode <server> <on|off>", NamedTextColor.RED));
            return;
        }

        String serverName = args[0];
        String mode = args[1].toLowerCase();

        RegisteredServer server = plugin.getProxyServer().getServer(serverName).orElse(null);
        if (server == null) {
            source.sendMessage(Component.text("Server not found: " + serverName, NamedTextColor.RED));
            return;
        }

        if (mode.equals("on")) {
            plugin.setStreamingMode(serverName, true);
            source.sendMessage(Component.text("Streaming mode enabled for server: " + serverName, NamedTextColor.GREEN));
        } else if (mode.equals("off")) {
            plugin.setStreamingMode(serverName, false);
            source.sendMessage(Component.text("Streaming mode disabled for server: " + serverName, NamedTextColor.GREEN));
        } else {
            source.sendMessage(Component.text("Invalid mode. Use 'on' or 'off'.", NamedTextColor.RED));
        }
    }
}