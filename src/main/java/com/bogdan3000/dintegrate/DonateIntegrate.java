package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.logic.ActionHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.server.ServerLifecycleHooks;

@Mod("dintegrate")
public class DonateIntegrate {

    private static Config config;
    private static DonatePayProvider donateProvider;

    public DonateIntegrate() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void setup(FMLCommonSetupEvent event) {
        System.out.println("[DIntegrate] Mod initialized.");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        System.out.println("[DIntegrate] Server starting... loading config.");

        config = new Config();
        config.load();

        if (config.getUserId() <= 0) {
            System.err.println("[DIntegrate] Invalid or missing user_id in config!");
            return;
        }

        System.out.println("[DIntegrate] Connecting to DonatePay WebSocket...");
        donateProvider = new DonatePayProvider(config.getToken(), config.getUserId(), don -> {
            new ActionHandler(ServerLifecycleHooks.getCurrentServer())
                    .execute(don.getAmount(), don.getUsername(), don.getMessage());
        });
        donateProvider.connect();
    }
}