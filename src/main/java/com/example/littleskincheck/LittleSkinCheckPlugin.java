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
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        GameProfile profile = player.getGameProfile();
        
        // 检查是否是 LittleSkin 验证
        boolean isLittleSkin = isLittleSkinAuthentication(profile);
        
        if (isLittleSkin) {
            String username = player.getUsername();
            if (!whitelistManager.isWhitelisted(username)) {
                event.setResult(LoginEvent.ComponentResult.denied(Component.text(
                    "对不起，该玩家不允许使用littleskin验证方式，请联系管理员",
                    NamedTextColor.RED
                )));
                logger.info("玩家 {} 使用 LittleSkin 登录被拒绝（不在白名单中）", username);
            } else {
                logger.info("玩家 {} 使用 LittleSkin 登录成功", username);
            }
        }
    }

    private boolean isLittleSkinAuthentication(GameProfile profile) {
        // LittleSkin 的特征是使用 littleskin.cn 域名
        Optional<GameProfile.Property> textures = profile.getProperties().stream()
            .filter(prop -> "textures".equals(prop.getName()))
            .findFirst();

        if (textures.isPresent()) {
            String value = textures.get().getValue();
            // 检查 textures 属性中是否包含 littleskin.cn
            return value.contains("littleskin.cn");
        }
        return false;
    }
}
