package com.gearworks.whitelist;

import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.UUID;

public class DiscordListener extends ListenerAdapter {

    private final CodeManager codeManager;
    private final AccountLinkManager accountLinkManager;

    public DiscordListener(CodeManager codeManager, AccountLinkManager accountLinkManager) {
        this.codeManager = codeManager;
        this.accountLinkManager = accountLinkManager;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        String code = event.getMessage().getContentRaw();
        UUID uuid = codeManager.verifyCode(code);

        if (uuid != null) {
            accountLinkManager.linkAccount(uuid, event.getAuthor().getId());
            event.getChannel().sendMessage("Your account has been linked!").queue();
        }
    }
}
