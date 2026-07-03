package com.loginmod.auth;

import java.util.Objects;

public class Account {
    private String playerName;
    private String uuid;
    private String hashedPassword;
    private String salt;
    private long lastLogin;
    private long registeredAt;

    public Account() {
    }

    public Account(String playerName, String uuid, String hashedPassword, String salt) {
        this.playerName = playerName;
        this.uuid = uuid;
        this.hashedPassword = hashedPassword;
        this.salt = salt;
        this.registeredAt = System.currentTimeMillis();
        this.lastLogin = System.currentTimeMillis();
    }

    public String getPlayerName() {
        return playerName;
    }

    public void setPlayerName(String playerName) {
        this.playerName = playerName;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getHashedPassword() {
        return hashedPassword;
    }

    public void setHashedPassword(String hashedPassword) {
        this.hashedPassword = hashedPassword;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public long getLastLogin() {
        return lastLogin;
    }

    public void setLastLogin(long lastLogin) {
        this.lastLogin = lastLogin;
    }

    public long getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(long registeredAt) {
        this.registeredAt = registeredAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Account)) return false;
        Account account = (Account) o;
        return Objects.equals(uuid, account.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }
}
