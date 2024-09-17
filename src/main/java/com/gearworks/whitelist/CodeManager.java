package com.gearworks.whitelist;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CodeManager {

    private final Map<String, UUID> codes = new ConcurrentHashMap<>();

    public String generateCode(UUID uuid) {
        String code = UUID.randomUUID().toString().substring(0, 8);
        codes.put(code, uuid);
        return code;
    }

    public UUID verifyCode(String code) {
        return codes.remove(code);
    }
}