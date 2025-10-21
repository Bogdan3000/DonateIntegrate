package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.logic.ActionHandler;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

@Mod("dintegrate")
@Mod.EventBusSubscriber(modid = "dintegrate", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class DonateIntegrate {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static Config config;
    private static DonatePayProvider donateProvider;
    private static Thread configWatcherThread;

    public DonateIntegrate() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("[DIntegrate] Client mod initialized");
        try {
            config = new Config();
            config.load();
        } catch (IOException e) {
            LOGGER.error("[DIntegrate] Failed to load config", e);
        }
        // стартуем наблюдатель за config/dintegrate.cfg
        startConfigWatcher();

        event.enqueueWork(() -> {
            // ✅ Регистрируем бинды и подписываем KeybindHandler на ивенты
            com.bogdan3000.dintegrate.client.KeybindHandler.init(config, donateProvider);
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(
                    com.bogdan3000.dintegrate.client.KeybindHandler.class
            );
        });
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterClientCommandsEvent event) {
        var dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("dpi")

                // /dpi token <value>
                .then(Commands.literal("token")
                        .then(Commands.argument("value", StringArgumentType.string())
                                .executes(ctx -> {
                                    String value = StringArgumentType.getString(ctx, "value");
                                    saveToConfig("token", value);
                                    reloadAndRestart();
                                    sendClientMessage("§aToken updated and connection restarted!");
                                    return 1;
                                })
                        )
                )

                // /dpi user <value>
                .then(Commands.literal("user")
                        .then(Commands.argument("value", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    int userId = IntegerArgumentType.getInteger(ctx, "value");
                                    saveToConfig("user_id", String.valueOf(userId));
                                    reloadAndRestart();
                                    sendClientMessage("§aUser ID updated and connection restarted!");
                                    return 1;
                                })
                        )
                )

                // /dpi start
                .then(Commands.literal("start")
                        .executes(ctx -> {
                            startConnection();
                            sendClientMessage("§aDonatePay connection started!");
                            return 1;
                        })
                )

                // /dpi stop
                .then(Commands.literal("stop")
                        .executes(ctx -> {
                            stopConnection();
                            sendClientMessage("§cDonatePay connection stopped.");
                            return 1;
                        })
                )

                // /dpi restart
                .then(Commands.literal("restart")
                        .executes(ctx -> {
                            restartConnection();
                            sendClientMessage("§bDonatePay connection restarted!");
                            return 1;
                        })
                )
                // /dpi info
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.player != null) {
                                mc.player.sendSystemMessage(Component.literal("§6[DIntegrate Info] §7———"));
                                mc.player.sendSystemMessage(Component.literal("§eModes:"));
                                mc.player.sendSystemMessage(Component.literal(" §aall §7– выполняет все команды по порядку"));
                                mc.player.sendSystemMessage(Component.literal(" §brandom §7– выбирает одну случайную команду"));
                                mc.player.sendSystemMessage(Component.literal(" §drandomN §7– выполняет N случайных команд"));
                                mc.player.sendSystemMessage(Component.literal(" §crandom_all §7– все команды, но в случайном порядке"));
                                mc.player.sendSystemMessage(Component.literal(""));
                                mc.player.sendSystemMessage(Component.literal("§eDelays:"));
                                mc.player.sendSystemMessage(Component.literal(" §adelay X §7– задержка X секунд"));
                                mc.player.sendSystemMessage(Component.literal(" §brandomdelay X-Y §7– случайная задержка между X и Y сек"));
                                mc.player.sendSystemMessage(Component.literal(" §drand X-Y §7– короткая форма randomdelay"));
                                mc.player.sendSystemMessage(Component.literal(" §estartdelay=X §7– задержка перед всей цепочкой"));
                                mc.player.sendSystemMessage(Component.literal(""));
                                mc.player.sendSystemMessage(Component.literal("§ePlaceholders:"));
                                mc.player.sendSystemMessage(Component.literal(" §a%name% §7– имя донатера"));
                                mc.player.sendSystemMessage(Component.literal(" §a%sum% §7– сумма доната"));
                                mc.player.sendSystemMessage(Component.literal(" §a%message% §7– сообщение донатера"));
                                mc.player.sendSystemMessage(Component.literal(""));
                                mc.player.sendSystemMessage(Component.literal("§7Используй §b/dpi test <name> <sum> <msg> §7для проверки."));
                            }
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

                                                    var rules = config.getRules();
                                                    if (!rules.containsKey(sum)) {
                                                        sendClientMessage("§c[DIntegrate] No donation rule found for this exact amount.");
                                                        return 0;
                                                    }

                                                    var rule = rules.get(sum);
                                                    sendClientMessage("§d[DIntegrate] Simulating donation (" + sum + " RUB, mode: " + rule.mode + ")");
                                                    new ActionHandler(config).execute(sum, name, message);
                                                    LOGGER.info("[DIntegrate] Simulated donation executed: {} sent {} ({})", name, sum, message);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                )
        );
    }

    private static void saveToConfig(String key, String value) {
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

    private static void reloadAndRestart() {
        try {
            config.load();
            restartConnection();
        } catch (IOException e) {
            LOGGER.error("[DIntegrate] Failed to reload config", e);
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

    private static void startConfigWatcher() {
        try {
            if (configWatcherThread != null && configWatcherThread.isAlive()) {
                return; // уже запущен
            }
            Path cfg = Paths.get("config", "dintegrate.cfg");
            ConfigWatcher watcher = new ConfigWatcher(cfg);
            configWatcherThread = new Thread(watcher, "DIntegrate-ConfigWatcher");
            configWatcherThread.setDaemon(true);
            configWatcherThread.start();
            LOGGER.info("[DIntegrate] Config watcher started for {}", cfg.toAbsolutePath());
        } catch (Exception e) {
            LOGGER.error("[DIntegrate] Failed to start config watcher", e);
        }
    }

    /**
     * Этот метод вызывается только из потока вотчера, чтобы аккуратно перезагрузить конфиг
     * и перезапустить подключение. Вынесен отдельно, чтобы не путать с ручными командами.
     */
    static void reloadAndRestartFromWatcher() {
        try {
            config.load();
            restartConnection();
            sendClientMessage("§b[DIntegrate] Конфиг обновлён, соединение перезапущено.");
        } catch (IOException e) {
            LOGGER.error("[DIntegrate] Failed to reload config from watcher", e);
            sendClientMessage("§c[DIntegrate] Ошибка перезагрузки конфига, см. лог.");
        }
    }

    private static void sendClientMessage(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null)
            mc.player.sendSystemMessage(Component.literal(text));
    }
}