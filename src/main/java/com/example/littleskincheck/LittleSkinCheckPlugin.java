package com.example.littleskincheck;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.*;

@Plugin(
    id = "littleskincheck",
    name = "LittleSkin Check",
    version = "1.0.0",
    description = "Checks if players are using LittleSkin authentication",
    authors = {"YourName"}
)
public class LittleSkinCheckPlugin {
    private final ProxyServer server;
    private final Logger logger;
    private final WhitelistManager whitelistManager;
    private final Set<UUID> littleSkinAuthenticatedPlayers = new HashSet<>();
    private final Map<UUID, String> littleSkinTokens = new HashMap<>();

    @Inject
    public LittleSkinCheckPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.whitelistManager = new WhitelistManager(dataDirectory, logger);
        
        logger.info("LittleSkin Check plugin has been initialized!");
        logger.info("白名单文件路径: {}", dataDirectory.resolve("whitelist.json"));
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("LittleSkin Check plugin is initializing...");
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        
        // 使用新的 API 检查 LittleSkin 认证状态
        try {
            Method isLittleSkinAuthenticated = player.getClass().getMethod("isLittleSkinAuthenticated");
            Method getLittleSkinToken = player.getClass().getMethod("getLittleSkinToken");
            
            boolean isAuthenticated = (boolean) isLittleSkinAuthenticated.invoke(player);
            String token = (String) getLittleSkinToken.invoke(player);
            
            if (isAuthenticated) {
                logger.info("玩家 {} 使用 LittleSkin 认证成功", username);
                littleSkinAuthenticatedPlayers.add(player.getUniqueId());
                if (token != null) {
                    littleSkinTokens.put(player.getUniqueId(), token);
                }
                
                // 检查白名单
                if (!whitelistManager.isWhitelisted(username)) {
                    event.setResult(ResultedEvent.ComponentResult.denied(
                        Component.text("你不在白名单中！").color(NamedTextColor.RED)
                    ));
                    return;
                }
            } else {
                // 不是 LittleSkin 认证，检查是否为正版
                if (player.isOnlineMode()) {
                    logger.info("玩家 {} 使用 Mojang 正版验证成功", username);
                    // 正版玩家的白名单检查
                    if (!whitelistManager.isWhitelisted(username)) {
                        event.setResult(ResultedEvent.ComponentResult.denied(
                            Component.text("你不在白名单中！").color(NamedTextColor.RED)
                        ));
                        return;
                    }
                } else {
                    logger.info("玩家 {} 既不是 LittleSkin 也不是正版验证", username);
                    event.setResult(ResultedEvent.ComponentResult.denied(
                        Component.text("请使用 LittleSkin 或正版账户进行验证！").color(NamedTextColor.RED)
                    ));
                    return;
                }
            }
        } catch (Exception e) {
            // 如果新 API 不可用，回退到原有的验证逻辑
            logger.debug("新的 LittleSkin API 不可用，使用原有验证逻辑: {}", e.getMessage());
            handleLegacyAuthentication(event);
        }
    }

    private void handleLegacyAuthentication(LoginEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        
        if (player.isOnlineMode()) {
            // 原有的正版验证逻辑
            if (!whitelistManager.isWhitelisted(username)) {
                event.setResult(ResultedEvent.ComponentResult.denied(
                    Component.text("你不在白名单中！").color(NamedTextColor.RED)
                ));
            }
        } else {
            // 原有的离线验证逻辑
            event.setResult(ResultedEvent.ComponentResult.denied(
                Component.text("请使用 LittleSkin 或正版账户进行验证！").color(NamedTextColor.RED)
            ));
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        String username = player.getUsername();
        
        logger.info("玩家 {} 断开连接，清理认证状态...", username);
        
        if (littleSkinAuthenticatedPlayers.remove(uuid)) {
            logger.info("已移除玩家 {} 的认证状态", username);
        }
        
        if (littleSkinTokens.remove(uuid) != null) {
            logger.info("已移除玩家 {} 的认证令牌", username);
        }
        
        logger.info("玩家 {} 的认证状态清理完成", username);
        logger.info("当前认证玩家数量: {}", littleSkinAuthenticatedPlayers.size());
    }
}
