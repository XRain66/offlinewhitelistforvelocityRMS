package com.example.littleskincheck;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
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
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
    private final Map<String, String> playerServerIds = new ConcurrentHashMap<>();

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
    public void onPreLogin(PreLoginEvent event) {
        String username = event.getUsername();
        // 生成一个随机的 serverId
        String serverId = UUID.randomUUID().toString().replace("-", "");
        playerServerIds.put(username, serverId);
        logger.debug("为玩家 {} 生成 serverId: {}", username, serverId);
    }

    private String fetchLittleSkinProfile(String username, String serverId) {
        try {
            String apiUrl = String.format(
                "https://littleskin.cn/api/yggdrasil/sessionserver/session/minecraft/hasJoined?username=%s&serverId=%s",
                URLEncoder.encode(username, StandardCharsets.UTF_8),
                URLEncoder.encode(serverId, StandardCharsets.UTF_8)
            );
            logger.info("正在从 {} 获取玩家信息", apiUrl);
            
            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int responseCode = conn.getResponseCode();
            logger.info("API 响应代码: {}", responseCode);
            
            if (responseCode == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                String responseBody = response.toString();
                logger.info("API 响应内容: {}", responseBody);
                
                JsonObject jsonResponse = JsonParser.parseString(responseBody).getAsJsonObject();
                
                if (jsonResponse.has("properties")) {
                    JsonArray properties = jsonResponse.getAsJsonArray("properties");
                    for (JsonElement prop : properties) {
                        JsonObject property = prop.getAsJsonObject();
                        if ("textures".equals(property.get("name").getAsString())) {
                            String value = property.get("value").getAsString();
                            logger.info("成功获取到玩家 {} 的 textures 值", username);
                            return value;
                        }
                    }
                }
                
                logger.info("API 响应中没有找到 textures 属性");
            } else if (responseCode == 404) {
                logger.info("玩家 {} 不存在或未使用 LittleSkin", username);
            } else {
                logger.warn("API 请求失败，响应代码: {}", responseCode);
                
                try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    StringBuilder errorResponse = new StringBuilder();
                    String line;
                    while ((line = errorReader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                    logger.warn("错误响应: {}", errorResponse.toString());
                } catch (Exception e) {
                    logger.error("读取错误响应时出错", e);
                }
            }
        } catch (Exception e) {
            logger.error("获取 LittleSkin 配置文件时出错: {}", e.getMessage());
            logger.debug("错误详情:", e);
        }
        return null;
    }

    private boolean isMojangPlayer(Player player) {
        try {
            GameProfile profile = player.getGameProfile();
            Optional<GameProfile.Property> textures = profile.getProperties().stream()
                .filter(prop -> "textures".equals(prop.getName()))
                .findFirst();

            if (textures.isPresent()) {
                String value = textures.get().getValue();
                if (value != null && !value.isEmpty()) {
                    String decoded = new String(Base64.getDecoder().decode(value));
                    logger.debug("玩家 {} 的皮肤信息: {}", player.getUsername(), decoded);
                    return decoded.contains("textures.minecraft.net");
                }
            }
        } catch (Exception e) {
            logger.error("检查玩家 {} 的正版状态时出错: {}", player.getUsername(), e.getMessage());
            logger.debug("错误详情:", e);
        }
        return false;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String username = player.getUsername();
        
        logger.info("玩家 {} 正在尝试登录...", username);
        
        // 检查是否为正版玩家
        if (isMojangPlayer(player)) {
            logger.info("玩家 {} 是正版玩家，允许登录", username);
            return;
        }
        
        logger.info("玩家 {} 不是正版玩家，开始尝试littleskin登录", username);
        
        // 获取之前生成的 serverId
        String serverId = playerServerIds.remove(username);
        if (serverId == null) {
            logger.error("无法获取玩家 {} 的 serverId", username);
            event.setResult(LoginEvent.ComponentResult.denied(Component.text(
                "验证失败，请重新进入服务器",
                NamedTextColor.RED
            )));
            return;
        }
        
        // 尝试从 LittleSkin API 获取玩家信息
        String texturesValue = fetchLittleSkinProfile(username, serverId);
        
        if (texturesValue != null) {
            try {
                // 解码并检查是否包含 littleskin.cn
                String decoded = new String(Base64.getDecoder().decode(texturesValue));
                logger.info("玩家 {} 的解码后 textures 值: {}", username, decoded);
                
                if (decoded.contains("littleskin.cn")) {
                    logger.info("检测到玩家 {} 使用 LittleSkin 验证", username);
                    
                    // 检查白名单
                    boolean isWhitelisted = whitelistManager.isWhitelisted(username);
                    logger.info("玩家 {} 是否在白名单中: {}", username, isWhitelisted);
                    
                    if (!isWhitelisted) {
                        event.setResult(LoginEvent.ComponentResult.denied(Component.text(
                            "对不起，该玩家不允许使用littleskin验证方式，请联系管理员",
                            NamedTextColor.RED
                        )));
                        logger.info("玩家 {} 使用 LittleSkin 登录被拒绝（不在白名单中）", username);
                        return;
                    }
                    
                    // 设置玩家的 textures 属性
                    GameProfile.Property texturesProperty = new GameProfile.Property("textures", texturesValue, "");
                    GameProfile profile = player.getGameProfile();
                    Collection<GameProfile.Property> properties = new ArrayList<>(profile.getProperties());
                    properties.add(texturesProperty);
                    
                    // 保存认证状态
                    setLittleSkinAuthenticated(player, texturesValue);
                    
                    logger.info("已为玩家 {} 设置 LittleSkin 属性", username);
                } else {
                    logger.info("玩家 {} 未使用 LittleSkin 验证", username);
                }
            } catch (Exception e) {
                logger.error("处理玩家 {} 的 textures 值时出错: {}", username, e.getMessage());
                logger.debug("错误详情:", e);
            }
        } else {
            logger.info("无法获取玩家 {} 的 LittleSkin 配置文件", username);
            // 检查白名单
            boolean isWhitelisted = whitelistManager.isWhitelisted(username);
            logger.info("玩家 {} 是否在白名单中: {}", username, isWhitelisted);
            
            if (isWhitelisted) {
                event.setResult(LoginEvent.ComponentResult.denied(Component.text(
                    "您在白名单中，但必须使用 LittleSkin 验证登录",
                    NamedTextColor.RED
                )));
                logger.info("玩家 {} 在白名单中但未使用 LittleSkin 验证，拒绝登录", username);
                return;
            }
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
        
        playerServerIds.remove(username);
        
        logger.info("玩家 {} 的认证状态清理完成", username);
        logger.info("当前认证玩家数量: {}", littleSkinAuthenticatedPlayers.size());
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
}
