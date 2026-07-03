package com.loginmod;

import com.loginmod.auth.AuthData;
import com.loginmod.auth.AuthEventHandler;
import com.loginmod.command.AuthCommands;
import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(LoginMod.MOD_ID)
public class LoginMod {
    public static final String MOD_ID = "loginmod";
    public static final Logger LOGGER = LogUtils.getLogger();

    public LoginMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new AuthEventHandler());

        modEventBus.addListener(this::onRegisterCommands);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStart);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStop);

        LOGGER.info("[LoginMod] 服务端登录/注册模组已加载");
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        AuthCommands.register(event.getDispatcher());
    }

    private void onServerStart(ServerAboutToStartEvent event) {
        AuthData.init(event.getServer());
        LOGGER.info("[LoginMod] 认证数据已加载，共 {} 个账户", AuthData.getAccountCount());
    }

    private void onServerStop(ServerStoppingEvent event) {
        AuthData.save();
        LOGGER.info("[LoginMod] 认证数据已保存，服务端关闭");
    }
}
