package com.gearworks.whitelist;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import javax.security.auth.login.LoginException;

public class DiscordBot {

    private JDA jda;
    private final String token;
    private final CodeManager codeManager;
    private final AccountLinkManager accountLinkManager;

    public DiscordBot(String token, CodeManager codeManager, AccountLinkManager accountLinkManager) {
        this.token = token;
        this.codeManager = codeManager;
        this.accountLinkManager = accountLinkManager;
    }

    public void start() throws LoginException {
        jda = JDABuilder.createDefault(token)
                .addEventListeners(new DiscordListener(codeManager, accountLinkManager))
                .build();
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    public JDA getJDA() {
        return jda;
    }
}