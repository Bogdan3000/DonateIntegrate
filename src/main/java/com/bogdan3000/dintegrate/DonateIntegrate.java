package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.logic.ActionHandler;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

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
        } catch (Exception e) {
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
            server.execute(() ->
                    new ActionHandler(server, config)
                            .execute(don.getAmount(), don.getUsername(), don.getMessage())
            );
        });

        try {
            donateProvider.connect();
        } catch (Exception e) {
            LOGGER.error("Failed to connect to DonatePay WebSocket", e);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        if (donateProvider != null) {
            donateProvider.disconnect();
            LOGGER.info("DonatePay connection closed.");
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("dpi")
                .requires(cs -> cs.hasPermission(4))
                .then(Commands.literal("token")
                        .then(Commands.argument("value", StringArgumentType.string())
                                .executes(ctx -> {
                                    String value = StringArgumentType.getString(ctx, "value");
                                    if (config != null) {
                                        saveToConfig("token", value);
                                        try {
                                            config.load();
                                        } catch (IOException e) {
                                            LOGGER.error("[DIntegrate] Failed to reload config", e);
                                        }
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§aToken updated successfully!"), true);
                                    }
                                    return 1;
                                })
                        )
                )
                .then(Commands.literal("user")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int userId = IntegerArgumentType.getInteger(ctx, "value");
                                    if (config != null) {
                                        saveToConfig("user_id", String.valueOf(userId));
                                        try {
                                            config.load();
                                        } catch (IOException e) {
                                            LOGGER.error("[DIntegrate] Failed to reload config", e);
                                        }
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§aUser ID updated successfully!"), true);
                                    }
                                    return 1;
                                })
                        )
                )
        );
    }

    private void saveToConfig(String key, String value) {
        try {
            java.io.File file = new java.io.File("config/dintegrate.cfg");
            java.util.List<String> lines = java.nio.file.Files.readAllLines(file.toPath());
            boolean found = false;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).startsWith(key + "=")) {
                    lines.set(i, key + "=" + value);
                    found = true;
                    break;
                }
            }
            if (!found) lines.add(key + "=" + value);
            try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
                for (String line : lines) writer.println(line);
            }
            LOGGER.info("[DIntegrate] Updated {} in config: {}", key, value);
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Failed to update config", e);
        }
    }
}