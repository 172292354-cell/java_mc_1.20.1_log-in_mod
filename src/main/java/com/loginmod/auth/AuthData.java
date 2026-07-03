package com.loginmod.auth;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.loginmod.LoginMod;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AuthData {
    private static final Map<String, Account> ACCOUNTS = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> LOGGED_IN = new ConcurrentHashMap<>();
    private static final String DATA_FILE = "loginmod_accounts.json";
    private static File dataFile;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final SecureRandom RANDOM = new SecureRandom();

    public static synchronized void init(MinecraftServer server) {
        File saveDir = new File(server.getServerDirectory(), "loginmod");
        if (!saveDir.exists()) {
            saveDir.mkdirs();
        }
        dataFile = new File(saveDir, DATA_FILE);
        load();
    }

    public static synchronized void load() {
        if (dataFile == null) return;
        ACCOUNTS.clear();
        LOGGED_IN.clear();
        if (!dataFile.exists()) {
            LoginMod.LOGGER.info("[LoginMod] 未找到账户数据文件，将创建新文件: {}", dataFile.getAbsolutePath());
            save();
            return;
        }
        try (FileReader reader = new FileReader(dataFile, StandardCharsets.UTF_8)) {
            Type listType = new TypeToken<List<Account>>() {}.getType();
            List<Account> accounts = GSON.fromJson(reader, listType);
            if (accounts != null) {
                for (Account acc : accounts) {
                    ACCOUNTS.put(acc.getUuid(), acc);
                }
            }
            LoginMod.LOGGER.info("[LoginMod] 已加载 {} 个账户", ACCOUNTS.size());
        } catch (IOException e) {
            LoginMod.LOGGER.error("[LoginMod] 加载账户数据失败", e);
        }
    }

    public static synchronized void save() {
        if (dataFile == null) return;
        try {
            if (dataFile.getParentFile() != null && !dataFile.getParentFile().exists()) {
                dataFile.getParentFile().mkdirs();
            }
            List<Account> accountList = new ArrayList<>(ACCOUNTS.values());
            try (FileWriter writer = new FileWriter(dataFile, StandardCharsets.UTF_8)) {
                GSON.toJson(accountList, writer);
            }
        } catch (IOException e) {
            LoginMod.LOGGER.error("[LoginMod] 保存账户数据失败", e);
        }
    }

    public static boolean hasAccount(String uuid) {
        return ACCOUNTS.containsKey(uuid);
    }

    public static Account getAccount(String uuid) {
        return ACCOUNTS.get(uuid);
    }

    public static void registerAccount(Account account) {
        ACCOUNTS.put(account.getUuid(), account);
        save();
    }

    public static void updateAccount(String uuid, String newHashedPassword, String newSalt) {
        Account acc = ACCOUNTS.get(uuid);
        if (acc != null) {
            acc.setHashedPassword(newHashedPassword);
            acc.setSalt(newSalt);
            acc.setLastLogin(System.currentTimeMillis());
            save();
        }
    }

    public static void markLoggedIn(String uuid) {
        LOGGED_IN.put(uuid, true);
    }

    public static void markLoggedOut(String uuid) {
        LOGGED_IN.remove(uuid);
    }

    public static boolean isLoggedIn(String uuid) {
        return Boolean.TRUE.equals(LOGGED_IN.get(uuid));
    }

    public static int getAccountCount() {
        return ACCOUNTS.size();
    }

    // ================= 密码相关 (SHA-256 + salt + 1000 次迭代哈希) =================
    //
    // 「千次迭代」的实现原理：
    //   1) 先用 SHA-256 对 salt + 原始密码做第 1 次哈希；
    //   2) 然后把上一轮得到的哈希结果再喂给 SHA-256，重复做 999 次；
    //   3) 因此总共 = 1 + 999 = 1000 次 SHA-256 迭代。
    //
    // 为什么要做千次迭代？
    //   如果只做 1 次 SHA-256，攻击者拿到存储的 JSON 后，可以用常见弱密码表
    //   （彩虹表 / 字典攻击）每秒计算几亿次，很快就能撞出原密码。
    //   做 1000 次迭代意味着每猜一个密码都要重复 1000 次哈希运算，暴力破解
    //   的成本直接被放大 1000 倍，对合法用户只多花不到 1 毫秒。
    //
    // 最终存储到 loginmod_accounts.json 的字段：
    //   - salt            : 随机生成的 16 字节 salt（Base64 编码）
    //   - hashedPassword  : 经过 1000 次 SHA-256 迭代后的最终哈希（Base64 编码）
    //   原始密码不会以任何形式保存在磁盘上。

    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    public static String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
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
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            LoginMod.LOGGER.error("[LoginMod] 密码哈希失败，SHA-256 不可用", e);
            throw new RuntimeException("SHA-256 不可用", e);
        }
    }

    public static boolean verifyPassword(String password, Account account) {
        if (account == null) return false;
        // 用玩家设置的密码 + 存储的 salt，用相同算法再算 1000 次 SHA-256，
        // 然后与数据库中保存的哈希做「常量时间比较」(slowEquals) 防止时序侧信道攻击。
        String computed = hashPassword(password, account.getSalt());
        return slowEquals(computed, account.getHashedPassword());
    }

    // 常量时间比较：无论字符串相等与否，循环次数始终相同，
    // 避免攻击者通过耗时差异推断出「第几位开始对不上」。
    private static boolean slowEquals(String a, String b) {
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        int diff = ba.length ^ bb.length;
        for (int i = 0; i < ba.length && i < bb.length; i++) {
            diff |= ba[i] ^ bb[i];
        }
        return diff == 0;
    }

    public static boolean isValidPassword(String password) {
        return password != null && password.length() >= 4 && password.length() <= 32;
    }
}
