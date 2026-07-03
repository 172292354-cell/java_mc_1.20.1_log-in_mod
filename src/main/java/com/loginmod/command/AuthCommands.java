package com.loginmod.command;

import com.loginmod.auth.Account;
import com.loginmod.auth.AuthData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class AuthCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("register")
                .then(Commands.argument("password", StringArgumentType.string())
                    .then(Commands.argument("confirm", StringArgumentType.string())
                        .executes(ctx -> {
                            CommandSourceStack src = ctx.getSource();
                            if (!(src.getEntity() instanceof ServerPlayer)) {
                                src.sendFailure(Component.literal("只有玩家才能执行此命令").withStyle(ChatFormatting.RED));
                                return 0;
                            }
                            ServerPlayer player = (ServerPlayer) src.getEntity();
                            String password = StringArgumentType.getString(ctx, "password");
                            String confirm = StringArgumentType.getString(ctx, "confirm");
                            return handleRegister(player, password, confirm);
                        })
                    )
                )
        );

        dispatcher.register(
            Commands.literal("login")
                .then(Commands.argument("password", StringArgumentType.string())
                    .executes(ctx -> {
                        CommandSourceStack src = ctx.getSource();
                        if (!(src.getEntity() instanceof ServerPlayer)) {
                            src.sendFailure(Component.literal("只有玩家才能执行此命令").withStyle(ChatFormatting.RED));
                            return 0;
                        }
                        ServerPlayer player = (ServerPlayer) src.getEntity();
                        String password = StringArgumentType.getString(ctx, "password");
                        return handleLogin(player, password);
                    })
                )
        );

        dispatcher.register(
            Commands.literal("changepassword")
                .then(Commands.argument("oldPassword", StringArgumentType.string())
                    .then(Commands.argument("newPassword", StringArgumentType.string())
                        .then(Commands.argument("confirmNewPassword", StringArgumentType.string())
                            .executes(ctx -> {
                                CommandSourceStack src = ctx.getSource();
                                if (!(src.getEntity() instanceof ServerPlayer)) {
                                    src.sendFailure(Component.literal("只有玩家才能执行此命令").withStyle(ChatFormatting.RED));
                                    return 0;
                                }
                                ServerPlayer player = (ServerPlayer) src.getEntity();
                                String oldPwd = StringArgumentType.getString(ctx, "oldPassword");
                                String newPwd = StringArgumentType.getString(ctx, "newPassword");
                                String confirm = StringArgumentType.getString(ctx, "confirmNewPassword");
                                return handleChangePassword(player, oldPwd, newPwd, confirm);
                            })
                        )
                    )
                )
        );
    }

    private static int handleRegister(ServerPlayer player, String password, String confirm) {
        String uuid = player.getStringUUID();
        if (AuthData.hasAccount(uuid)) {
            send(player, "您已经注册过账户，无需再次注册！", ChatFormatting.RED);
            return 0;
        }
        if (!password.equals(confirm)) {
            send(player, "两次输入的密码不一致！", ChatFormatting.RED);
            return 0;
        }
        if (!AuthData.isValidPassword(password)) {
            send(player, "密码长度必须为 4-32 个字符！", ChatFormatting.RED);
            return 0;
        }
        String salt = AuthData.generateSalt();
        String hashed = AuthData.hashPassword(password, salt);
        Account account = new Account(player.getGameProfile().getName(), uuid, hashed, salt);
        AuthData.registerAccount(account);
        AuthData.markLoggedIn(uuid);
        send(player, "注册成功！您已自动登录，欢迎游玩。", ChatFormatting.GREEN);
        return 1;
    }

    private static int handleLogin(ServerPlayer player, String password) {
        String uuid = player.getStringUUID();
        if (!AuthData.hasAccount(uuid)) {
            send(player, "您还未注册，请先使用 /register <密码> <确认密码> 注册。", ChatFormatting.RED);
            return 0;
        }
        if (AuthData.isLoggedIn(uuid)) {
            send(player, "您已经登录了。", ChatFormatting.YELLOW);
            return 0;
        }
        Account account = AuthData.getAccount(uuid);
        if (!AuthData.verifyPassword(password, account)) {
            send(player, "密码错误，请重试！", ChatFormatting.RED);
            return 0;
        }
        AuthData.markLoggedIn(uuid);
        send(player, "登录成功！祝您游戏愉快。", ChatFormatting.GREEN);
        return 1;
    }

    private static int handleChangePassword(ServerPlayer player, String oldPwd, String newPwd, String confirm) {
        String uuid = player.getStringUUID();
        if (!AuthData.hasAccount(uuid)) {
            send(player, "您还未注册账号。", ChatFormatting.RED);
            return 0;
        }
        if (!AuthData.isLoggedIn(uuid)) {
            send(player, "请先登录后再修改密码。", ChatFormatting.RED);
            return 0;
        }
        Account account = AuthData.getAccount(uuid);
        if (!AuthData.verifyPassword(oldPwd, account)) {
            send(player, "原密码错误！", ChatFormatting.RED);
            return 0;
        }
        if (!newPwd.equals(confirm)) {
            send(player, "两次输入的新密码不一致！", ChatFormatting.RED);
            return 0;
        }
        if (!AuthData.isValidPassword(newPwd)) {
            send(player, "新密码长度必须为 4-32 个字符！", ChatFormatting.RED);
            return 0;
        }
        String newSalt = AuthData.generateSalt();
        String newHashed = AuthData.hashPassword(newPwd, newSalt);
        AuthData.updateAccount(uuid, newHashed, newSalt);
        send(player, "密码修改成功！", ChatFormatting.GREEN);
        return 1;
    }

    private static void send(ServerPlayer player, String message, ChatFormatting color) {
        player.sendSystemMessage(Component.literal(message).withStyle(style -> style.withColor(color)));
    }
}
