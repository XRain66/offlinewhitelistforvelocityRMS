package com.example.littleskincheck;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class WhitelistManager {
    private final Path configPath;
    private final Logger logger;
    private final Gson gson;
    private Set<String> whitelist;

    public WhitelistManager(Path dataDirectory, Logger logger) {
        this.logger = logger;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configPath = dataDirectory.resolve("whitelist.json");
        this.whitelist = new HashSet<>();
        
        loadWhitelist();
    }

    private void loadWhitelist() {
        try {
            if (!Files.exists(configPath)) {
                Files.createDirectories(configPath.getParent());
                whitelist = new HashSet<>();
                saveWhitelist();
            } else {
                try (Reader reader = Files.newBufferedReader(configPath)) {
                    whitelist = gson.fromJson(reader, new TypeToken<HashSet<String>>(){}.getType());
                    if (whitelist == null) {
                        whitelist = new HashSet<>();
                    }
                }
            }
        } catch (IOException e) {
            logger.error("无法加载白名单文件", e);
            whitelist = new HashSet<>();
        }
    }

    private void saveWhitelist() {
        try {
            Files.createDirectories(configPath.getParent());
            try (Writer writer = Files.newBufferedWriter(configPath)) {
                gson.toJson(whitelist, writer);
            }
        } catch (IOException e) {
            logger.error("无法保存白名单文件", e);
        }
    }

    public boolean isWhitelisted(String username) {
        return whitelist.contains(username.toLowerCase());
    }

    public void addPlayer(String username) {
        whitelist.add(username.toLowerCase());
        saveWhitelist();
    }

    public void removePlayer(String username) {
        whitelist.remove(username.toLowerCase());
        saveWhitelist();
    }

    public Set<String> getWhitelistedPlayers() {
        return new HashSet<>(whitelist);
    }
}
