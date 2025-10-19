package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.command.DPICommand;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.donation.DonationProvider;
import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.*;

/**
 * Полностью асинхронная версия DonateIntegrate с независимыми задержками.
 */
@Mod(modid = DonateIntegrate.MOD_ID, name = DonateIntegrate.NAME, version = "2.0.7", clientSideOnly = true)
@SideOnly(Side.CLIENT)
public class DonateIntegrate {
    public static final String MOD_ID = "dintegrate";
    public static final String NAME = "DonateIntegrate";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static final long COMMAND_COOLDOWN_MS = 150;

    private static DonationProvider donationProvider;
    private static DonateIntegrate instance;
    private static volatile boolean isConnectedToServer = false;

    // Очередь донатов
    private static final BlockingQueue<DonationProvider.DonationEvent> incomingDonations = new LinkedBlockingQueue<>();

    // Планировщик, который управляет задержками между командами
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(8);

    // Активные потоки донатов
    private static final Map<Integer, Boolean> activeDonations = new ConcurrentHashMap<>();

    public static class CommandToExecute {
        public final String command;
        public final String playerName;
        public final int priority;

        public CommandToExecute(String command, String playerName, int priority) {
            this.command = command.trim();
            this.playerName = playerName;
            this.priority = priority;
        }
    }

    public static DonateIntegrate getInstance() {
        return instance;
    }

    public DonationProvider getDonationProvider() {
        return donationProvider;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        instance = this;
        ConfigHandler.register(event.getSuggestedConfigurationFile());
        ClientRegistry.registerKeyBinding(KeyHandler.KEY_OPEN_GUI);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
        initializeDonationProvider();
        NetworkHandler.init();
        ClientCommandHandler.instance.registerCommand(new DPICommand());
    }

    // ===== Инициализация провайдера =====
    private void initializeDonationProvider() {
        donationProvider = new DonatePayProvider();
        donationProvider.onDonation(event -> {
            LOGGER.info("🎁 Новый донат от {}: {} RUB | msg: {} | id: {}",
                    event.username(), event.amount(), event.message(), event.id());
            incomingDonations.offer(event);
        });

        // Запускаем обработчик очереди донатов
        new Thread(DonateIntegrate::processDonationsQueue, "DonationQueueProcessor").start();
    }

    // ===== Обработка донатов =====
    private static void processDonationsQueue() {
        while (true) {
            try {
                DonationProvider.DonationEvent event = incomingDonations.take();
                processDonation(event);
            } catch (InterruptedException e) {
                LOGGER.warn("Donation queue processor interrupted");
                break;
            } catch (Exception e) {
                LOGGER.error("Ошибка при обработке доната: {}", e.getMessage(), e);
            }
        }
    }

    private static void processDonation(DonationProvider.DonationEvent event) {
        int id = event.id();
        if (activeDonations.containsKey(id)) {
            LOGGER.warn("Донат #{} уже обрабатывается, пропуск", id);
            return;
        }
        activeDonations.put(id, true);

        new Thread(() -> {
            try {
                ConfigHandler.getConfig().getActions().stream()
                        .filter(a -> Math.abs(a.getSum() - event.amount()) < 0.001 && a.isEnabled())
                        .findFirst()
                        .ifPresent(action -> {
                            List<String> cmds = action.getExecutionMode() ==
                                    com.bogdan3000.dintegrate.config.Action.ExecutionMode.ALL
                                    ? new ArrayList<>(action.getCommands())
                                    : Collections.singletonList(
                                    action.getCommands().get(new Random().nextInt(action.getCommands().size()))
                            );
                            executeDonationCommands(id, event, cmds);
                        });
            } catch (Exception e) {
                LOGGER.error("Ошибка при выполнении доната #{}: {}", id, e.getMessage(), e);
            } finally {
                activeDonations.remove(id);
                LOGGER.info("✅ Донат #{} завершён", id);
            }
        }, "DonationExec-" + id).start();
    }

    // ===== Исполнение команд доната =====
    private static void executeDonationCommands(int donationId, DonationProvider.DonationEvent event, List<String> commands) {
        Iterator<String> iterator = commands.iterator();

        Runnable chain = new Runnable() {
            @Override
            public void run() {
                if (!iterator.hasNext()) return;

                String raw = iterator.next();
                if (raw == null || raw.trim().isEmpty()) {
                    scheduler.schedule(this, COMMAND_COOLDOWN_MS, TimeUnit.MILLISECONDS);
                    return;
                }

                String cmd = raw
                        .replace("{username}", event.username())
                        .replace("{message}", event.message())
                        .replace("{amount}", String.valueOf(event.amount()))
                        .trim();

                if (cmd.toLowerCase().startsWith("/delay")) {
                    try {
                        int seconds = Integer.parseInt(cmd.split(" ")[1]);
                        LOGGER.info("[Донат #{}] ⏱ Задержка {} сек", donationId, seconds);
                        Minecraft.getMinecraft().addScheduledTask(() -> {
                            if (Minecraft.getMinecraft().player != null);
                        });
                        scheduler.schedule(this, seconds, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        LOGGER.error("[Донат #{}] Ошибка в /delay: {}", donationId, e.getMessage());
                        scheduler.schedule(this, COMMAND_COOLDOWN_MS, TimeUnit.MILLISECONDS);
                    }
                    return;
                }

                Minecraft mc = Minecraft.getMinecraft();
                if (mc.player != null && isConnectedToServer) {
                    mc.addScheduledTask(() -> mc.player.sendChatMessage(cmd));
                    LOGGER.info("[Донат #{}] Выполнена команда: {}", donationId, cmd);
                }

                scheduler.schedule(this, COMMAND_COOLDOWN_MS, TimeUnit.MILLISECONDS);
            }
        };

        scheduler.schedule(chain, 0, TimeUnit.MILLISECONDS);
    }

    // ===== /dpi команда (эмулирует донат) =====
    public static void addCommand(CommandToExecute cmd) {
        DonationProvider.DonationEvent fake = new DonationProvider.DonationEvent(
                "Tester", 0, cmd.command, new Random().nextInt(99999)
        );
        processDonation(fake);
    }

    // ===== Управление провайдером =====
    public static void startDonationProvider() {
        try {
            if (ConfigHandler.getConfig().isEnabled()) {
                stopDonationProvider();
                donationProvider.connect();
                LOGGER.info("🔌 Donation provider запущен");
            }
        } catch (Exception e) {
            LOGGER.error("Ошибка запуска провайдера: {}", e.getMessage());
        }
    }

    public static void stopDonationProvider() {
        try {
            if (donationProvider != null) donationProvider.disconnect();
        } catch (Exception e) {
            LOGGER.error("Ошибка остановки провайдера: {}", e.getMessage());
        }
    }

    // ===== Forge Client Events =====
    @SideOnly(Side.CLIENT)
    public static class ClientEventHandler {
        private int ticks = 0;

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent e) {
            if (e.phase != TickEvent.Phase.END || !isConnectedToServer) return;
            ticks++;
            if (ticks % 6000 == 0) {
                try {
                    if (!ConfigHandler.getConfig().isEnabled()) stopDonationProvider();
                    else if (!donationProvider.isConnected()) startDonationProvider();
                } catch (Exception ex) {
                    LOGGER.error("Ошибка при проверке провайдера: {}", ex.getMessage());
                }
            }
        }

        @SubscribeEvent
        public void onClientConnectedToServer(FMLNetworkEvent.ClientConnectedToServerEvent e) {
            isConnectedToServer = true;
            startDonationProvider();
        }

        @SubscribeEvent
        public void onClientDisconnectionFromServer(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
            isConnectedToServer = false;
            stopDonationProvider();
            activeDonations.clear();
        }
    }
}