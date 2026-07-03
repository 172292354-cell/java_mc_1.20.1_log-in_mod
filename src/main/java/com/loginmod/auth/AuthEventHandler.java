package com.loginmod.auth;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AuthEventHandler {

    private static final Map<UUID, Long> TICK_COUNTER = new HashMap<>();

    @SubscribeEvent
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        String uuid = player.getStringUUID();

        if (!AuthData.hasAccount(uuid)) {
            sendMessage(player, "欢迎来到服务器！请输入指令完成注册：", ChatFormatting.GOLD);
            sendMessage(player, "/register <密码> <确认密码>", ChatFormatting.YELLOW);
        } else {
            sendMessage(player, "欢迎回来！请输入密码登录：", ChatFormatting.GOLD);
            sendMessage(player, "/login <密码>", ChatFormatting.YELLOW);
        }
    }

    @SubscribeEvent
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        AuthData.markLoggedOut(player.getStringUUID());
        TICK_COUNTER.remove(player.getUUID());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.PlayerTickEvent event) {
        if (!(event.player instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.player;
        String uuid = player.getStringUUID();

        if (AuthData.isLoggedIn(uuid)) return;

        Long counter = TICK_COUNTER.getOrDefault(player.getUUID(), 0L);
        counter = counter + 1;
        TICK_COUNTER.put(player.getUUID(), counter);

        if (counter % 160 == 0) {
            if (!AuthData.hasAccount(uuid)) {
                sendMessage(player, "【系统】您还未注册账号，请先输入 /register <密码> <确认密码> 完成注册。", ChatFormatting.RED);
            } else {
                sendMessage(player, "【系统】您还未登录，请输入 /login <密码> 完成登录。", ChatFormatting.RED);
            }
        }

        // 未登录玩家每隔 40 tick 把位置重置回出生点，防止移动
        if (counter % 20 == 0) {
            net.minecraft.server.level.ServerLevel serverLevel = player.serverLevel();
            BlockPos spawn = serverLevel.getSharedSpawnPos();
            if (spawn != null && player.blockPosition().distManhattan(spawn) > 4) {
                player.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getPlayer();
        String uuid = player.getStringUUID();
        if (!AuthData.isLoggedIn(uuid)) {
            event.setCanceled(true);
            sendMessage(player, "【系统】未登录玩家无法破坏方块！", ChatFormatting.RED);
        }
    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        String uuid = player.getStringUUID();
        if (!AuthData.isLoggedIn(uuid)) {
            event.setCanceled(true);
            sendMessage(player, "【系统】未登录玩家无法放置方块！", ChatFormatting.RED);
        }
    }

    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        String uuid = player.getStringUUID();
        if (!AuthData.isLoggedIn(uuid)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String uuid = player.getStringUUID();
        if (!AuthData.isLoggedIn(uuid)) {
            event.setCanceled(true);
            sendMessage(player, "【系统】未登录玩家无法发送聊天消息！", ChatFormatting.RED);
        }
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        if (!(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer)) return;
        ServerPlayer player = (ServerPlayer) event.getParseResults().getContext().getSource().getEntity();
        String uuid = player.getStringUUID();

        String rawCmd = event.getParseResults().getReader().getString().toLowerCase(java.util.Locale.ROOT).trim();
        // 允许登录相关命令
        if (rawCmd.startsWith("register") || rawCmd.startsWith("login") || rawCmd.startsWith("changepassword")) {
            return;
        }
        if (!AuthData.isLoggedIn(uuid)) {
            event.setCanceled(true);
            sendMessage(player, "【系统】未登录玩家无法执行此命令！", ChatFormatting.RED);
        }
    }

    private static void sendMessage(ServerPlayer player, String message, ChatFormatting color) {
        Component text = Component.literal(message).withStyle(style -> style.withColor(color));
        player.sendSystemMessage(text);
    }
}
