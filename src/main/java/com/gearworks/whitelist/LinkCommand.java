package com.gearworks.whitelist;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public class LinkCommand implements SimpleCommand {

    private final CodeManager codeManager;

    public LinkCommand(CodeManager codeManager) {
        this.codeManager = codeManager;
    }

    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        if (!(source instanceof Player)) {
            source.sendMessage(Component.text("This command can only be used by players."));
            return;
        }

        Player player = (Player) source;
        String code = codeManager.generateCode(player.getUniqueId());
        player.sendMessage(Component.text("Use this code to link your Discord account: " + code));
        player.sendMessage(Component.text("Send this code to the Discord bot via direct message."));
    }
}
