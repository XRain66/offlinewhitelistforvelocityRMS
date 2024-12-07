# LittleSkin 白名单验证插件

这是一个用于 Velocity 服务器的插件，用于管理使用 LittleSkin 验证方式登录的玩家。插件会检查玩家的验证方式，如果玩家使用 LittleSkin 验证登录但不在白名单中，将被拒绝访问。这是为了解决可能存在的安全问题

## 注意！

本插件为为定制版本velocity使用的插件，如果需要使用，请在 [VelocityRMS Releases](https://github.com/XRain66/Velocity/releases) 下载定制velocity版本


## 功能特点

- 自动检测玩家是否使用 LittleSkin 验证登录
- 维护 LittleSkin 验证玩家的白名单
- 对非白名单玩家显示友好的提示信息
- 使用 JSON 文件存储白名单数据
- 支持热重载白名单

## 系统要求

- Velocity 3.1.1 或更高版本
- Java 17 或更高版本

## 安装方法

1. 从 [Releases](../../releases) 页面下载最新版本的插件 JAR 文件
2. 将下载的 JAR 文件放入 Velocity 服务器的 `plugins` 目录中
3. 重启 Velocity 服务器
4. 插件将在首次运行时创建配置文件

## 配置说明

### 白名单配置

白名单文件位于 `plugins/littleskincheck/whitelist.json`，格式如下：

```json
[
  "player1",
  "player2",
  "player3"
]
```

将允许使用 LittleSkin 验证的玩家名称添加到数组中即可。

## 使用方法

1. 编辑 `whitelist.json` 文件，添加允许使用 LittleSkin 验证的玩家名称
2. 保存文件后，白名单将自动生效
3. 当玩家尝试使用 LittleSkin 验证登录时：
   - 如果玩家在白名单中，将允许登录
   - 如果玩家不在白名单中，将显示提示信息并拒绝登录

## 常见问题

如果有无法解决的问题请附上日志提交issues

**Q: 如何添加玩家到白名单？**
A: 直接编辑 `plugins/littleskincheck/whitelist.json` 文件，添加玩家名称即可。

**Q: 修改白名单后需要重启服务器吗？**
A: 需要重启服务器，以便插件重新加载新的白名单数据。

**Q: 使用其他验证方式的玩家会受到影响吗？**
A: 不会，插件只会检查使用 LittleSkin 验证方式的玩家。

## 开发者信息

本插件使用 Java 开发，采用 Gradle 构建系统。如果你想要自行构建插件：

1. 克隆仓库：
```bash
git clone https://github.com/yourusername/offlinewhitelistforvelocityRMS.git
```

2. 构建项目：
```bash
./gradlew build
```

构建完成后，JAR 文件将位于 `build/libs` 目录中。

## 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。
