package com.example.littleskincheck;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.slf4j.Logger;
import com.velocitypowered.api.util.GameProfile;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Base64;

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

    @Inject
    public LittleSkinCheckPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.whitelistManager = new WhitelistManager(dataDirectory, logger);
        
        logger.info("LittleSkin Check plugin has been initialized!");
        logger.info("白名单文件路径: {}", dataDirectory.resolve("whitelist.json"));
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        GameProfile profile = player.getGameProfile();
        String username = player.getUsername();
        
        logger.info("玩家 {} 正在尝试登录...", username);
        logger.info("正在检查玩家 {} 的验证方式...", username);
        
        // 输出所有属性用于调试
        profile.getProperties().forEach(prop -> {
            logger.info("属性名: {}", prop.getName());
            logger.info("属性值: {}", prop.getValue());
            if (prop.getName().equals("textures")) {
                try {
                    String decoded = new String(Base64.getDecoder().decode(prop.getValue()));
                    logger.info("解码后的 textures 值: {}", decoded);
                } catch (Exception e) {
                    logger.error("无法解码 textures 值", e);
                }
            }
        });

        // 检查是否是 LittleSkin 验证
        boolean isLittleSkin = isLittleSkinAuthentication(profile);
        logger.info("玩家 {} 是否使用 LittleSkin 验证: {}", username, isLittleSkin);
        
        if (isLittleSkin) {
            logger.info("检查玩家 {} 是否在白名单中...", username);
            boolean isWhitelisted = whitelistManager.isWhitelisted(username);
            logger.info("玩家 {} 是否在白名单中: {}", username, isWhitelisted);
            
            if (!isWhitelisted) {
                event.setResult(LoginEvent.ComponentResult.denied(Component.text(
                    "对不起，该玩家不允许使用littleskin验证方式，请联系管理员",
                    NamedTextColor.RED
                )));
                logger.info("玩家 {} 使用 LittleSkin 登录被拒绝（不在白名单中）", username);
            } else {
                logger.info("玩家 {} 使用 LittleSkin 登录成功", username);
            }
        } else {
            logger.info("玩家 {} 未使用 LittleSkin 验证，允许登录", username);
        }
    }

    private boolean isLittleSkinAuthentication(GameProfile profile) {
        // LittleSkin 的特征是使用 littleskin.cn 域名
        Optional<GameProfile.Property> textures = profile.getProperties().stream()
            .filter(prop -> "textures".equals(prop.getName()))
            .findFirst();

        if (textures.isPresent()) {
            String value = textures.get().getValue();
            try {
                String decoded = new String(Base64.getDecoder().decode(value));
                logger.info("检查 textures 值是否包含 littleskin.cn: {}", decoded.contains("littleskin.cn"));
                return decoded.contains("littleskin.cn");
            } catch (Exception e) {
                logger.error("解析 textures 值时出错", e);
                return false;
            }
        }
        logger.info("未找到 textures 属性");
        return false;
    }
}
