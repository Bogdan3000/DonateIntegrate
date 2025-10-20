package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.logic.ActionHandler;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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
import java.util.Map;

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
        startConnection();
    }

    private void startConnection() {
        donateProvider = new DonatePayProvider(
                config.getToken(),
                config.getUserId(),
                config.getTokenUrl(),
                config.getSocketUrl(),
                don -> {
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

    private void restartConnection() {
        try {
            if (donateProvider != null) {
                donateProvider.disconnect();
                LOGGER.info("[DIntegrate] Old DonatePay connection closed.");
            }
            startConnection();
            LOGGER.info("[DIntegrate] DonatePay connection restarted.");
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Failed to restart connection", e);
        }
    }

    private void stopConnection() {
        if (donateProvider != null) {
            try {
                donateProvider.disconnect();
                LOGGER.info("[DIntegrate] DonatePay connection stopped manually.");
            } catch (Exception e) {
                LOGGER.error("[DIntegrate] Failed to stop connection", e);
            }
        } else {
            LOGGER.warn("[DIntegrate] No active DonatePay connection to stop.");
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

                // /dpi token <value>
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
                                        restartConnection();
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§aToken updated and connection restarted!"), true);
                                    }
                                    return 1;
                                })
                        )
                )

                // /dpi user <value>
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
                                        restartConnection();
                                        ctx.getSource().sendSuccess(() ->
                                                Component.literal("§aUser ID updated and connection restarted!"), true);
                                    }
                                    return 1;
                                })
                        )
                )

                // /dpi start
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            if (donateProvider == null || !donateProvider.isConnected()) {
                                startConnection();
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§aDonatePay connection started!"), true);
                            } else {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§eDonatePay is already connected."), false);
                            }
                            return 1;
                        })
                )

                // /dpi stop
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            if (donateProvider != null) {
                                stopConnection();
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§cDonatePay connection stopped."), true);
                            } else {
                                ctx.getSource().sendSuccess(() ->
                                        Component.literal("§eNo active connection to stop."), false);
                            }
                            return 1;
                        })
                )

                // /dpi restart
                .then(Commands.literal("restart")
                        .executes(ctx -> {
                            restartConnection();
                            ctx.getSource().sendSuccess(() ->
                                    Component.literal("§bDonatePay connection restarted!"), true);
                            return 1;
                        })
                )

                // /dpi test <name> <sum> <message>
                .then(Commands.literal("test")
                        .then(Commands.argument("name", StringArgumentType.string())
                                .then(Commands.argument("sum", DoubleArgumentType.doubleArg(0.01))
                                        .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    double sum = DoubleArgumentType.getDouble(ctx, "sum");
                                                    String message = StringArgumentType.getString(ctx, "message");

                                                    MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                                                    Map<Double, String> rules = config.getRules();

                                                    // Проверяем, есть ли подходящее правило
                                                    boolean hasRule = rules.keySet().stream().anyMatch(amount -> sum >= amount);

                                                    if (!hasRule) {
                                                        ctx.getSource().sendSuccess(() ->
                                                                Component.literal("§c[DIntegrate] No donation rule found for this amount."), false);
                                                        LOGGER.warn("[DIntegrate] No donation rule found for test amount {}", sum);
                                                        return 0;
                                                    }

                                                    ctx.getSource().sendSuccess(() ->
                                                            Component.literal("§d[DIntegrate] Simulating donation from §b"
                                                                    + name + " §7(" + sum + "): §f" + message), true);

                                                    server.execute(() ->
                                                            new ActionHandler(server, config)
                                                                    .execute(sum, name, message)
                                                    );

                                                    LOGGER.info("[DIntegrate] Simulated donation executed: {} sent {} ({})", name, sum, message);
                                                    return 1;
                                                })
                                        )
                                )
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