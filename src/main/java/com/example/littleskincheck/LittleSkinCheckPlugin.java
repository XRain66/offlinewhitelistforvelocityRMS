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
        logger.info("正在设置玩家 {} 的 LittleSkin 认证状态...", player.getUsername());
        UUID uuid = player.getUniqueId();
        littleSkinAuthenticatedPlayers.add(uuid);
        logger.info("已添加玩家 UUID {} 到认证列表", uuid);
        
        if (token != null) {
            littleSkinTokens.put(uuid, token);
            logger.info("已保存玩家 {} 的认证令牌", player.getUsername());
        }
        
        logger.info("玩家 {} 的 LittleSkin 认证状态设置完成", player.getUsername());
        logger.info("当前认证玩家数量: {}", littleSkinAuthenticatedPlayers.size());
    }

    public boolean isLittleSkinAuthenticated(Player player) {
        boolean result = littleSkinAuthenticatedPlayers.contains(player.getUniqueId());
        logger.info("检查玩家 {} 的认证状态: {}", player.getUsername(), result);
        return result;
    }

    public String getLittleSkinToken(Player player) {
        String token = littleSkinTokens.get(player.getUniqueId());
        logger.info("获取玩家 {} 的认证令牌: {}", player.getUsername(), token != null ? "存在" : "不存在");
        return token;
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        GameProfile profile = player.getGameProfile();
        String username = player.getUsername();
        
        logger.info("玩家 {} 正在尝试登录...", username);
        logger.info("检查玩家 {} 的属性...", username);
        logger.info("玩家 {} 的所有属性: {}", username, profile.getProperties());
        
        // 获取所有属性并打印
        profile.getProperties().forEach(prop -> {
            logger.info("属性: {} = {}", prop.getName(), prop.getValue());
            if (prop.getSignature() != null) {
                logger.info("签名: {}", prop.getSignature());
            }
        });

        // 检查玩家的 textures 属性
        Optional<GameProfile.Property> textures = profile.getProperties().stream()
            .filter(prop -> "textures".equals(prop.getName()))
            .findFirst();

        logger.info("玩家 {} 是否有 textures 属性: {}", username, textures.isPresent());
        
        boolean isLittleSkin = false;
        if (textures.isPresent()) {
            try {
                String value = textures.get().getValue();
                logger.info("玩家 {} 的 textures 值: {}", username, value);
                
                if (value != null && !value.isEmpty()) {
                    String decoded = new String(Base64.getDecoder().decode(value));
                    logger.info("玩家 {} 的解码后 textures 值: {}", username, decoded);
                    
                    if (decoded.contains("littleskin.cn")) {
                        logger.info("检测到玩家 {} 使用 LittleSkin 验证", username);
                        isLittleSkin = true;
                        setLittleSkinAuthenticated(player, value);
                    } else {
                        logger.info("玩家 {} 未使用 LittleSkin 验证", username);
                    }
                }
            } catch (Exception e) {
                logger.error("处理玩家 {} 的 textures 值时出错: {}", username, e.getMessage());
                logger.debug("错误详情:", e);
            }
        }
        
        logger.info("玩家 {} 是否使用 LittleSkin 验证: {}", username, isLittleSkin);
        
        if (isLittleSkin) {
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
