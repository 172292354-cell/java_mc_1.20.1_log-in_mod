# LoginMod — Minecraft Forge 1.20.1 服务端登录/注册模组

一个纯服务端的 Minecraft Forge 模组，为玩家提供账号注册、登录、密码修改功能；无需在客户端安装任何插件。

- 适用版本：**Minecraft 1.20.1 + Forge 47.x**
- 运行环境：**服务端**（客户端无需安装）
- 密码加密：**SHA-256 + salt + 1000 次迭代哈希**（见下方「密码加密原理」详细说明）
- 数据持久化：**JSON** 文件（位于服务端启动目录 `loginmod/loginmod_accounts.json`）

---

## 📦 功能

| 功能 | 说明 |
|------|------|
| 玩家注册 | 首次进入服务器时引导玩家使用 `/register` 注册账号 |
| 玩家登录 | 已注册玩家每次进入服务器需用 `/login` 登录 |
| 密码修改 | 已登录玩家可使用 `/changepassword` 修改密码 |
| 未登录行为限制 | 未登录玩家无法破坏方块 / 放置方块 / 发送聊天消息 / 执行其他命令，并且会被限制在出生点附近 |
| 安全存储 | 密码以 salt + SHA-256 + 1000 次迭代哈希的形式存储，永远不会明文写入磁盘 |
| 并发安全 | 使用 `ConcurrentHashMap` + `synchronized` 读写方法 |

---

## 🔐 密码加密原理（「千次迭代」详细说明）

为了避免别人误解「只做了 1 次 SHA-256」，这里把加密流程完整写清楚。源码位于
`src/main/java/com/loginmod/auth/AuthData.java` 中的 `hashPassword` 方法。

### 完整流程

```
步骤 1（第 1 次哈希）:
    hash_1 = SHA-256( salt + 玩家密码 )

步骤 2（第 2 ~ 1000 次，共 999 次迭代）:
    hash_2   = SHA-256( hash_1 )
    hash_3   = SHA-256( hash_2 )
    ...
    hash_1000 = SHA-256( hash_999 )

最终存储值（保存到 JSON 的 hashedPassword 字段）:
    Base64( hash_1000 )
```

### 在代码里对应位置（AuthData.java）

```java
// 第 1 次：把 salt 混入密码一起做 SHA-256
digest.reset();
digest.update(salt.getBytes(StandardCharsets.UTF_8));
byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));

// 第 2 ~ 1000 次：把上一轮的哈希结果再哈希，共重复 999 次
// 这样总的哈希迭代次数 = 1 + 999 = 1000 次
for (int i = 0; i < 999; i++) {
    digest.reset();
    hashed = digest.digest(hashed);
}
// 最终以 Base64 编码存入 JSON
```

所以 **1000 次迭代 = 外层 1 次 + for 循环里再做 999 次**。

### 为什么要做千次迭代？（Key Stretching / 密钥拉伸）

| 场景 | 只做 1 次 SHA-256 | 做 1000 次 SHA-256 |
|------|-------------------|--------------------|
| 普通玩家登录耗时 | 约 0.01 毫秒 | 约 1 毫秒（几乎无感知） |
| 攻击者每秒能尝试的密码数 | 几亿 ~ 几十亿次 | 几百万次（**被放大 1000 倍的防御**） |
| 原始密码是否能从存储值还原 | 不能，但暴力破解成本低 | 不能，且暴力破解成本提高 1000 倍 |

千次迭代让「攻击者猜密码」的成本乘上 1000，但对「真正知道密码的玩家」来说只是多花了不到 1 毫秒。这是一种被称为 **key stretching（密钥拉伸）** 的通用安全做法。

### 其他安全细节

- **每个玩家一个独立的 salt**（16 字节随机数，`SecureRandom` 生成），
  即使两个玩家设置了同样的密码，最终存储值也完全不同。
- **常量时间比较**（`slowEquals`）：无论密码对不对，比较的循环次数固定，
  防止攻击者通过响应时间差异推断出密码的第几位对得上。
- **原始密码不会以任何形式写入磁盘**，`loginmod_accounts.json` 中只有
  `salt` 和 `hashedPassword` 两个字段。

---

## 🎮 游戏内命令

| 命令 | 说明 | 要求 |
|------|------|------|
| `/register <密码> <确认密码>` | 注册新账号 | 未注册玩家 |
| `/login <密码>` | 登录 | 已注册但未登录玩家 |
| `/changepassword <旧密码> <新密码> <确认新密码>` | 修改密码 | 已登录玩家 |

- 密码长度 **4 – 32** 个字符
- 两次输入的密码必须一致
- 登录失败会有提示，不会踢出玩家

---

## 🚀 使用方式（一键安装，不需要编译）

> 本模组只在**服务端**运行，客户端（玩家电脑）**不需要安装任何东西**。
> 你只需要把 `loginmod-1.0.0.jar` 丢到 Forge 服务端的 `mods` 文件夹里即可。

### 步骤 1：下载模组 JAR

从下方地址下载 `loginmod-1.0.0.jar`（由作者本人上传，确保文件完整）：

```
📦 蓝奏云网盘下载地址：
https://wwamp.lanzouu.com/iwrIg3u6fzrc

提取码：ffy4
```

> 如果上面的链接在你的地区打不开，可以在浏览器里手动复制粘贴网址；或联系作者更新备用链接。

### 步骤 2：把 JAR 丢进 Forge 服务端的 mods 文件夹

打开你的 Forge 1.20.1 服务端根目录（里面通常有 `mods/`、`libraries/`、`minecraft_server.1.20.1.jar`、`forge-1.20.1-47.3.0.jar` 等）：

```
你的服务器目录/
├── mods/
│   └── loginmod-1.0.0.jar   ← 把下载到的 JAR 放在这里
├── libraries/
├── minecraft_server.1.20.1.jar
├── forge-1.20.1-47.3.0.jar
└── start.bat
```

### 步骤 3：启动服务端

启动你的 Forge 服务端（双击 `start.bat` 或在命令行运行启动脚本）。

首次启动时，本模组会自动在服务端根目录创建账户数据文件夹：

```
你的服务器目录/
└── loginmod/
    └── loginmod_accounts.json   ← 所有玩家的账户数据保存在这里
                                    （只存 salt + 1000 次迭代 SHA-256，
                                    绝不保存明文密码）
```

### 玩家进入服务器后的流程

1. 首次进入 → 聊天栏看到提示：**「请输入 `/register <密码> <确认密码>` 完成注册」**
   - 例：玩家输入 `/register 123456 123456` → 注册成功并自动登录。
2. 之后每次进入 → 提示：**「请输入 `/login <密码>` 完成登录」**
3. 已登录后 → 可正常游玩（破坏/放置/聊天/命令）。
4. 未登录时 → 无法破坏方块、放置方块、发送聊天消息、执行其他命令，位置被限制在出生点附近。

### 想修改密码？

已登录玩家在游戏内输入：

```
/changepassword <旧密码> <新密码> <确认新密码>
```

例如：`/changepassword 123456 abcdef abcdef`。

---

## 📂 项目结构

```
java_mc_1.20.1_log-in_mod/
├── build.bat                        ← 一键构建脚本（仅作者本人从源码生成 JAR 时使用）
├── build.gradle                     ← Forge Gradle 构建配置
├── settings.gradle
├── gradle.properties                ← JVM 参数（内存 / 编码）
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties ← Gradle 版本声明 (8.5)
├── README.md                        ← 本文件
└── src/main/
    ├── resources/META-INF/
    │   └── mods.toml                ← Forge 模组元数据
    └── java/com/loginmod/
        ├── LoginMod.java            ← 模组入口：注册事件/命令/生命周期
        ├── auth/
        │   ├── Account.java         ← 账户数据模型
        │   ├── AuthData.java        ← JSON 持久化 + SHA-256/salt 密码验证
        │   └── AuthEventHandler.java← 玩家加入/离开/Tick/破坏/放置/聊天/命令事件
        └── command/
            └── AuthCommands.java    ← /register /login /changepassword 命令注册
```

---

## 📜 协议 / 许可（License）

**本项目采用 「Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International」
（CC BY-NC-SA 4.0，知识共享 - 署名 - 非商业性使用 - 相同方式共享 4.0 国际）作为开源协议。**

### 文件位置

| 文件 | 说明 |
|------|------|
| [`LICENSE`](LICENSE) | **正式、具有法律效力的原文（英文）** — 任何争议以此为准 |
| [`LICENSE_ZH.md`](LICENSE_ZH.md) | **中文翻译版（仅供参考，不具法律效力）** — 帮助快速理解 |

### 三条核心规则（简明版）

| 权利 | 条件 |
|------|------|
| ✅ 个人学习、个人服务器使用 | **自由** |
| ✅ 下载、修改、二次分发 | **保留原作者署名** + **修改后的版本必须以相同协议（CC BY-NC-SA 4.0）开源**（Copyleft / 相同方式共享） |
| ❌ 用于**任何商业用途**（包括但不限于：收费入服、VIP 服务、广告变现、售卖账号权限、出售模组本体） | **严格禁止** |

### 如果你觉得上面太绕，请记住这三条

1. **禁止商用** — 任何形式的商业盈利行为（含间接盈利）都不被允许。
2. **署名保留** — 无论是二次分发、公开分享、还是在自己服务器宣传中提及本模组，
   都必须保留 `LICENSE` 和 `LICENSE_ZH.md` 文件以及原作者信息。
3. **修改后必须开源** — 如果你对本模组做了修改，并把修改后的版本放入自己的服务器
   / 公开分享 / 分发给其他人，**必须把修改后的完整源码也以 CC BY-NC-SA 4.0 方式公开发布**
   （例如上传到 GitHub），让下游玩家也能看到并继续修改。

---

## 🙋 FAQ

**Q: 客户端需要装模组吗？**  
A: 不需要。所有功能在服务端实现，玩家使用原版客户端即可。

**Q: 数据存放在哪里？**  
A: 服务端启动目录下的 `loginmod/loginmod_accounts.json`。

**Q: 密码安全吗？**  
A: 采用 SHA-256 + 随机 salt + 千次迭代哈希，数据库泄露不会还原出原始密码。

**Q: 可以改密码长度限制吗？**  
A: 可以，修改 `src/main/java/com/loginmod/auth/AuthData.java` 中 `isValidPassword` 的长度判断即可。

**Q: 忘记密码怎么办？**  
A: 直接在服务端删除 `loginmod/loginmod_accounts.json` 中对应用户的那一条记录，玩家重新注册即可。
