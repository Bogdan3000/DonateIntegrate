package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.logic.ActionHandler;
import com.google.gson.*;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;

@Mod("dintegrate")
@Mod.EventBusSubscriber(modid = "dintegrate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DonateIntegrate {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static Config config;
    private static DonatePayProvider donateProvider;

    public DonateIntegrate() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("[DIntegrate] Client initialized");
        try {
            config = new Config();
            config.load();
        } catch (IOException e) {
            LOGGER.error("[DIntegrate] Failed to load config", e);
        }
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        var d = event.getDispatcher();

        d.register(Commands.literal("dpi")

                // === TOKEN ===
                .then(Commands.literal("token")
                        .then(Commands.argument("value", StringArgumentType.string())
                                .executes(ctx -> {
                                    String v = StringArgumentType.getString(ctx, "value");
                                    saveToJsonConfig("token", v);
                                    reloadConfig(true); // с перезапуском
                                    sendClientMessage("§aToken updated and reconnected!");
                                    return 1;
                                })))

                // === USER ===
                .then(Commands.literal("user")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int id = IntegerArgumentType.getInteger(ctx, "value");
                                    saveToJsonConfig("user_id", String.valueOf(id));
                                    reloadConfig(true); // с перезапуском
                                    sendClientMessage("§aUser ID updated and reconnected!");
                                    return 1;
                                })))

                // === RELOAD (без рестарта сокета) ===
                .then(Commands.literal("reload")
                        .executes(ctx -> {
                            reloadConfig(false);
                            sendClientMessage("§bConfig reloaded (no reconnect).");
                            return 1;
                        }))

                // === START / STOP / RESTART ===
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            startConnection();
                            sendClientMessage("§aConnection started!");
                            return 1;
                        }))
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            stopConnection();
                            sendClientMessage("§cConnection stopped.");
                            return 1;
                        }))
                .then(Commands.literal("restart")
                        .executes(ctx -> {
                            restartConnection();
                            sendClientMessage("§bConnection restarted!");
                            return 1;
                        }))

                // === TEST ===
                .then(Commands.literal("test")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("sum", DoubleArgumentType.doubleArg(0.01))
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    double sum = DoubleArgumentType.getDouble(ctx, "sum");
                                                    String msg = StringArgumentType.getString(ctx, "message");

                                                    var rule = config.getRules().entrySet().stream()
                                                            .filter(e -> Math.abs(e.getKey() - sum) < 0.0001)
                                                            .map(e -> e.getValue())
                                                            .findFirst()
                                                            .orElse(null);

                                                    if (rule == null) {
                                                        sendClientMessage("§c[DIntegrate] No rule found for this amount (" + sum + ")");
                                                        return 0;
                                                    }

                                                    sendClientMessage("§d[DIntegrate] Simulating donation " + sum + "₽ (" + rule.mode + ")");
                                                    new ActionHandler(config).execute(sum, name, msg);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }

    private static void reloadConfig(boolean restart) {
        try {
            config.load();
            int count = config.getRules().size();

            if (restart) {
                restartConnection();
                sendClientMessage("§b[DIntegrate] Config reloaded (" + count + " rules) and reconnected.");
            } else {
                sendClientMessage("§b[DIntegrate] Config reloaded (" + count + " rules).");
            }

            LOGGER.info("[DIntegrate] Config reloaded successfully ({} rules). Restart = {}", count, restart);
        } catch (IOException e) {
            LOGGER.error("[DIntegrate] Reload failed", e);
            sendClientMessage("§c[DIntegrate] Failed to reload config. Check logs.");
        }
    }

    /**
     * Обновляет dintegrate.json, а не старый .cfg.
     */
    private static void saveToJsonConfig(String key, String value) {
        try {
            Path path = Paths.get("config", "dintegrate.json");
            if (!Files.exists(path)) {
                LOGGER.warn("[DIntegrate] JSON config not found, creating new one...");
                Files.createDirectories(path.getParent());
                Files.writeString(path, "{}");
            }

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            JsonObject json;

            try (FileReader reader = new FileReader(path.toFile())) {
                json = JsonParser.parseReader(reader).getAsJsonObject();
            }

            json.addProperty(key, value);

            try (FileWriter writer = new FileWriter(path.toFile())) {
                gson.toJson(json, writer);
            }

            LOGGER.info("[DIntegrate] Updated {} in JSON config: {}", key, value);
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Failed to update JSON config", e);
        }
    }

    private static void startConnection() {
        if (donateProvider != null && donateProvider.isConnected()) return;
        donateProvider = new DonatePayProvider(
                config.getToken(),
                config.getUserId(),
                config.getTokenUrl(),
                config.getSocketUrl(),
                don -> new ActionHandler(config)
                        .execute(don.getAmount(), don.getUsername(), don.getMessage())
        );
        donateProvider.connect();
    }

    private static void stopConnection() {
        if (donateProvider != null) {
            donateProvider.disconnect();
            donateProvider = null;
        }
    }

    private static void restartConnection() {
        stopConnection();
        startConnection();
    }

    private static void sendClientMessage(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.sendSystemMessage(Component.literal(text));
    }
}