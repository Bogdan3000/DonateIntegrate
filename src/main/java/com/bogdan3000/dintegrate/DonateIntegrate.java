package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.logic.ActionHandler;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.util.logging.Logger;

@Mod("dintegrate")
public class DonateIntegrate {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static Config config;
    private static DonatePayProvider donateProvider;

    public DonateIntegrate() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void setup(FMLCommonSetupEvent event) {
        LOGGER.info("DIntegrate initialized");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting, loading config...");

        try {
            config = new Config();
            config.load();
        } catch (IOException e) {
            LOGGER.error("Failed to load config", e);
            return;
        }

        if (config.getUserId() <= 0) {
            LOGGER.error("Invalid or missing user_id in config!");
            return;
        }

        LOGGER.info("Connecting to DonatePay WebSocket...");
        donateProvider = new DonatePayProvider(config.getToken(), config.getUserId(), don -> {
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            server.execute(() -> new ActionHandler(server)
                    .execute(don.getAmount(), don.getUsername(), don.getMessage()));
        });

        donateProvider.connect();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (donateProvider != null) {
            donateProvider.disconnect();
            LOGGER.info("DonatePay connection closed.");
        }
    }
}