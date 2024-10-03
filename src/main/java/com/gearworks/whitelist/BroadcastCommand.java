package com.gearworks.whitelist;


import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;

public class BroadcastCommand implements SimpleCommand {

    private final ProxyServer server;

    public BroadcastCommand(ProxyServer server) {
        this.server = server;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();

        // Ensure the command is run from the console
        if (!(source instanceof ConsoleCommandSource)) {
            source.sendMessage(Component.text("This command can only be run from the console."));
            return;
        }

        String[] args = invocation.arguments();
        if (args.length == 0) {
            source.sendMessage(Component.text("Usage: /broadcast <message>"));
            return;
        }

        String message = String.join(" ", args);
        Component broadcastMessage = Component.text("[ANNOUNCEMENT] ").append(Component.text(message));

        // Send the message to all players
        server.getAllPlayers().forEach(player -> player.sendMessage(broadcastMessage));

        // Optionally, log the message or send confirmation to the console
        source.sendMessage(Component.text("Message broadcasted to all players."));
    }
}
