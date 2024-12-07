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
                logger.info("白名单文件不存在，创建新文件");
                Files.createDirectories(configPath.getParent());
                whitelist = new HashSet<>();
                saveWhitelist();
            } else {
                logger.info("正在加载白名单文件: {}", configPath);
                String content = new String(Files.readAllBytes(configPath));
                logger.info("白名单文件内容: {}", content);
                
                try (Reader reader = Files.newBufferedReader(configPath)) {
                    Set<String> loadedList = gson.fromJson(reader, new TypeToken<HashSet<String>>(){}.getType());
                    whitelist = new HashSet<>();
                    
                    if (loadedList == null) {
                        logger.warn("白名单为空，创建新的白名单");
                    } else {
                        // 确保所有用户名都转换为小写
                        for (String name : loadedList) {
                            whitelist.add(name.toLowerCase());
                            logger.info("添加玩家到白名单: {} -> {}", name, name.toLowerCase());
                        }
                        logger.info("成功加载白名单，包含 {} 个玩家: {}", whitelist.size(), whitelist);
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
        String lowercaseUsername = username.toLowerCase();
        boolean result = whitelist.contains(lowercaseUsername);
        logger.info("检查白名单 - 玩家: {}, 小写: {}, 结果: {}, 当前白名单: {}", 
            username, lowercaseUsername, result, whitelist);
        return result;
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
