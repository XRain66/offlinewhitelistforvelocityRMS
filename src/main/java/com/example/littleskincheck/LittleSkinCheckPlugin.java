package com.example.littleskincheck;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import com.velocitypowered.api.util.GameProfile;

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

    public void setLittleSkinAuthenticated(Player player, String token) {
        littleSkinAuthenticatedPlayers.add(player.getUniqueId());
        if (token != null) {
            littleSkinTokens.put(player.getUniqueId(), token);
        }
        logger.info("已设置玩家 {} 的 LittleSkin 认证状态", player.getUsername());
    }

    public boolean isLittleSkinAuthenticated(Player player) {
        return littleSkinAuthenticatedPlayers.contains(player.getUniqueId());
    }

    public String getLittleSkinToken(Player player) {
        return littleSkinTokens.get(player.getUniqueId());
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        GameProfile profile = player.getGameProfile();
        String username = player.getUsername();
        
        logger.info("玩家 {} 正在尝试登录...", username);
        
        // 检查玩家的 textures 属性
        Optional<GameProfile.Property> textures = profile.getProperties().stream()
            .filter(prop -> "textures".equals(prop.getName()))
            .findFirst();

        if (textures.isPresent()) {
            try {
                String value = textures.get().getValue();
                String decoded = new String(Base64.getDecoder().decode(value));
                if (decoded.contains("littleskin.cn")) {
                    setLittleSkinAuthenticated(player, value);
                }
            } catch (Exception e) {
                logger.error("解析 textures 值时出错", e);
            }
        }
        
        logger.info("玩家 {} 是否使用 LittleSkin 验证: {}", username, isLittleSkinAuthenticated(player));
        
        if (isLittleSkinAuthenticated(player)) {
            logger.info("检查玩家 {} 是否在白名单中...", username);
            boolean isWhitelisted = whitelistManager.isWhitelisted(username);
            logger.info("玩家 {} 是否在白名单中: {}", username, isWhitelisted);
            
            if (!isWhitelisted) {
                player.disconnect(Component.text(
                    "对不起，该玩家不允许使用littleskin验证方式，请联系管理员",
                    NamedTextColor.RED
                ));
                logger.info("玩家 {} 使用 LittleSkin 登录被拒绝（不在白名单中）", username);
            } else {
                player.sendMessage(Component.text(
                    "欢迎使用 LittleSkin 验证登录！",
                    NamedTextColor.GREEN
                ));
                logger.info("玩家 {} 使用 LittleSkin 登录成功", username);
            }
        } else {
            logger.info("玩家 {} 未使用 LittleSkin 验证，允许登录", username);
        }
    }

    @Subscribe
    public void onPlayerDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        littleSkinAuthenticatedPlayers.remove(player.getUniqueId());
        littleSkinTokens.remove(player.getUniqueId());
        logger.info("已清除玩家 {} 的 LittleSkin 认证状态", player.getUsername());
    }
}
