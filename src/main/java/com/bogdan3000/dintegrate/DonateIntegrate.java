package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.command.DPICommand;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.donation.DonationProvider;
import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Mod(modid = DonateIntegrate.MOD_ID, name = DonateIntegrate.NAME, version = "2.0.3")
public class DonateIntegrate {
    public static final String MOD_ID = "dintegrate";
    public static final String NAME = "DonateIntegrate";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static final ConcurrentLinkedQueue<CommandToExecute> commands = new ConcurrentLinkedQueue<>();
    private static final long COMMAND_COOLDOWN_MS = 1; // 0.01 секунда между командами

    private static DonationProvider donationProvider;
    private static ExecutorService commandExecutor;
    private static long lastCommandTime = 0;
    private static DonateIntegrate instance;

    public static class CommandToExecute {
        public final String command;
        public final String playerName;
        public final int priority;

        public CommandToExecute(String command, String playerName, int priority) {
            if (command == null || command.trim().isEmpty()) {
                throw new IllegalArgumentException("Команда не может быть пустой");
            }
            if (playerName == null) {
                throw new IllegalArgumentException("Имя игрока не может быть null");
            }
            this.command = command;
            this.playerName = playerName;
            this.priority = priority;
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Инициализация DonateIntegrate");
        instance = this;
        ConfigHandler.register(event.getSuggestedConfigurationFile());
    }

    public static DonateIntegrate getInstance() {
        return instance;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Регистрация обработчика тиков сервера");
        MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
        commandExecutor = Executors.newFixedThreadPool(2);
        initializeDonationProvider();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        LOGGER.info("Регистрация команды /dpi");
        event.registerServerCommand(new DPICommand());
        startDonationProvider();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        LOGGER.info("Остановка DonateIntegrate");
        stopDonationProvider();
        if (commandExecutor != null) {
            commandExecutor.shutdown();
            try {
                if (!commandExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    commandExecutor.shutdownNow();
                    LOGGER.warn("Принудительная остановка пула команд");
                }
            } catch (InterruptedException e) {
                LOGGER.error("Ошибка при остановке пула команд: {}", e.getMessage());
                commandExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void initializeDonationProvider() {
        donationProvider = new DonatePayProvider();
        donationProvider.onDonation(event -> {
            try {
                LOGGER.info("Донат: {} пожертвовал {}, сообщение: {}, ID: {}",
                        event.username(), event.amount(), event.message(), event.id());
                ConfigHandler.getConfig().getActions().stream()
                        .filter(action -> Math.abs(action.getSum() - event.amount()) < 0.001 && action.isEnabled())
                        .findFirst()
                        .ifPresent(action -> {
                            List<String> commandsToExecute = action.getExecutionMode() == com.bogdan3000.dintegrate.config.Action.ExecutionMode.ALL
                                    ? action.getCommands()
                                    : java.util.Collections.singletonList(
                                    action.getCommands().get(new java.util.Random().nextInt(action.getCommands().size())));
                            for (String cmd : commandsToExecute) {
                                String command = cmd.replace("{username}", event.username())
                                        .replace("{message}", event.message())
                                        .replace("{amount}", String.valueOf(event.amount()));
                                addCommand(new CommandToExecute(command, event.username(), action.getPriority()));
                            }
                            ConfigHandler.getConfig().setLastDonate(event.id());
                            ConfigHandler.save();
                            LOGGER.info("Обработан донат #{}: добавлено {} команд", event.id(), commandsToExecute.size());
                        });
            } catch (Exception e) {
                LOGGER.error("Ошибка обработки доната #{}: {}", event.id(), e.getMessage());
            }
        });
    }

    public static void startDonationProvider() {
        try {
            if (ConfigHandler.getConfig().isEnabled()) {
                stopDonationProvider(); // Закрываем старое соединение перед новым
                donationProvider.connect();
                LOGGER.info("Попытка запуска провайдера донатов");
            } else {
                LOGGER.warn("Обработка донатов отключена");
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка запуска провайдера донатов: {}", e.getMessage());
        }
    }

    public static void stopDonationProvider() {
        try {
            donationProvider.disconnect();
            LOGGER.info("Провайдер донатов остановлен");
        } catch (Exception e) {
            LOGGER.error("Ошибка остановки провайдера донатов: {}", e.getMessage());
        }
    }

    public static void addCommand(CommandToExecute command) {
        try {
            commands.add(command);
            LOGGER.debug("Добавлена команда в очередь: {}", command.command);
        } catch (Exception e) {
            LOGGER.error("Ошибка добавления команды: {}", e.getMessage());
        }
    }

    public static class ServerTickHandler {
        private int tickCounter = 0;

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            tickCounter++;
            if (tickCounter % 6000 == 0) { // Каждые 5 минут
                try {
                    if (!ConfigHandler.getConfig().isEnabled()) {
                        LOGGER.info("Провайдер донатов отключен");
                        stopDonationProvider();
                    } else if (!donationProvider.isConnected()) {
                        LOGGER.info("Переподключение провайдера донатов");
                        startDonationProvider();
                    }
                } catch (Exception e) {
                    LOGGER.error("Ошибка проверки подключения: {}", e.getMessage());
                }
            }

            if (tickCounter % 100 == 0) { // Каждые 5 секунд
                try {
                    ConfigHandler.checkAndReloadConfig();
                } catch (Exception e) {
                    LOGGER.error("Ошибка перезагрузки конфигурации: {}", e.getMessage());
                }
            }

            if (tickCounter % 2 == 0 && !commands.isEmpty()) { // Каждые 0.5 секунды
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastCommandTime < COMMAND_COOLDOWN_MS) {
                    return;
                }

                CommandToExecute cmd = commands.poll();
                if (cmd != null) {
                    lastCommandTime = currentTime;
                    commandExecutor.submit(() -> {
                        try {
                            Minecraft mc = Minecraft.getMinecraft();
                            if (mc.player != null) {
                                mc.addScheduledTask(() -> {
                                    mc.player.sendChatMessage(cmd.command);
                                    LOGGER.debug("Выполнена команда от {}: {}", cmd.playerName, cmd.command);
                                });
                            } else {
                                LOGGER.warn("Игрок недоступен для команды: {}", cmd.command);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Ошибка выполнения команды '{}': {}", cmd.command, e.getMessage());
                        }
                    });
                }
            }
        }
    }
}