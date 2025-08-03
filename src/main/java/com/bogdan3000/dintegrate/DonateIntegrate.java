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

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Mod(modid = DonateIntegrate.MOD_ID, name = DonateIntegrate.NAME, version = "2.0.6", clientSideOnly = true)
@SideOnly(Side.CLIENT)
public class DonateIntegrate {
    public static final String MOD_ID = "dintegrate";
    public static final String NAME = "DonateIntegrate";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private static final ConcurrentLinkedQueue<CommandToExecute> commands = new ConcurrentLinkedQueue<>();
    private static final long COMMAND_COOLDOWN_MS = 1; // 0.01 second between commands

    private static DonationProvider donationProvider;
    private static ExecutorService commandExecutor;
    private static long lastCommandTime = 0;
    private static DonateIntegrate instance;
    private static volatile boolean isConnectedToServer = false; // Переименован для ясности

    public static class CommandToExecute {
        public final String command;
        public final String playerName;
        public final int priority;

        public CommandToExecute(String command, String playerName, int priority) {
            if (command == null || command.trim().isEmpty()) {
                throw new IllegalArgumentException("Command cannot be empty");
            }
            if (playerName == null) {
                throw new IllegalArgumentException("Player name cannot be null");
            }
            this.command = command;
            this.playerName = playerName;
            this.priority = priority;
        }
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("Pre-initializing DonateIntegrate on side: {}", event.getSide());
        instance = this;
        ConfigHandler.register(event.getSuggestedConfigurationFile());
        ClientRegistry.registerKeyBinding(KeyHandler.KEY_OPEN_GUI);
    }

    public static DonateIntegrate getInstance() {
        return instance;
    }

    public DonationProvider getDonationProvider() {
        return donationProvider;
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Initializing DonateIntegrate on side: {}", event.getSide());
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
        MinecraftForge.EVENT_BUS.register(new ClientEventHandler());
        commandExecutor = Executors.newFixedThreadPool(2);
        initializeDonationProvider();
        NetworkHandler.init();
        // Регистрируем клиентскую команду /dpi
        ClientCommandHandler.instance.registerCommand(new DPICommand());
    }

    private void initializeDonationProvider() {
        donationProvider = new DonatePayProvider();
        donationProvider.onDonation(event -> {
            try {
                LOGGER.info("Donation: {} donated {}, message: {}, ID: {}",
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
                            LOGGER.info("Processed donation #{}: added {} commands", event.id(), commandsToExecute.size());
                        });
            } catch (Exception e) {
                LOGGER.error("Error processing donation #{}: {}", event.id(), e.getMessage(), e);
            }
        });
    }

    public static void startDonationProvider() {
        try {
            if (ConfigHandler.getConfig().isEnabled()) {
                stopDonationProvider();
                donationProvider.connect();
                LOGGER.info("Attempting to start donation provider");
            } else {
                LOGGER.warn("Donation processing is disabled");
            }
        } catch (Exception e) {
            LOGGER.error("Error starting donation provider: {}", e.getMessage(), e);
        }
    }

    public static void stopDonationProvider() {
        try {
            donationProvider.disconnect();
            LOGGER.info("Donation provider stopped");
        } catch (Exception e) {
            LOGGER.error("Error stopping donation provider: {}", e.getMessage(), e);
        }
    }

    public static void addCommand(CommandToExecute command) {
        LOGGER.debug("Attempting to add command: {}, isConnectedToServer={}", command.command, isConnectedToServer);
        try {
            if (isConnectedToServer) {
                commands.add(command);
                LOGGER.debug("Added command to queue: {}", command.command);
            } else {
                LOGGER.warn("Command not added: not connected to server: {}", command.command);
            }
        } catch (Exception e) {
            LOGGER.error("Error adding command: {}", command.command, e);
        }
    }

    @SideOnly(Side.CLIENT)
    public static class ClientEventHandler {
        private int tickCounter = 0;

        @SubscribeEvent
        public void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase != TickEvent.Phase.END || !isConnectedToServer) {
                return;
            }

            tickCounter++;
            if (tickCounter % 6000 == 0) {
                try {
                    if (!ConfigHandler.getConfig().isEnabled()) {
                        LOGGER.info("Donation provider disabled");
                        stopDonationProvider();
                    } else if (!donationProvider.isConnected()) {
                        LOGGER.info("Reconnecting donation provider");
                        startDonationProvider();
                    }
                } catch (Exception e) {
                    LOGGER.error("Error checking connection", e);
                }
            }

            if (tickCounter % 100 == 0) {
                try {
                    ConfigHandler.checkAndReloadConfig();
                } catch (Exception e) {
                    LOGGER.error("Error reloading configuration", e);
                }
            }

            if (tickCounter % 2 == 0 && !commands.isEmpty()) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastCommandTime < COMMAND_COOLDOWN_MS) {
                    return;
                }

                CommandToExecute cmd = commands.poll();
                if (cmd != null) {
                    lastCommandTime = currentTime;
                    if (commandExecutor != null && !commandExecutor.isShutdown()) {
                        commandExecutor.submit(() -> {
                            try {
                                Minecraft mc = Minecraft.getMinecraft();
                                if (mc.player != null && isConnectedToServer) {
                                    mc.addScheduledTask(() -> {
                                        mc.player.sendChatMessage(cmd.command);
                                        LOGGER.debug("Sent command from {}: {}", cmd.playerName, cmd.command);
                                    });
                                } else {
                                    LOGGER.warn("Command execution skipped: player unavailable or not connected: {}", cmd.command);
                                }
                            } catch (Exception e) {
                                LOGGER.error("Error executing command '{}': {}", cmd.command, e.getMessage(), e);
                            }
                        });
                    } else {
                        LOGGER.warn("Command execution skipped: commandExecutor is shutdown: {}", cmd.command);
                    }
                }
            }
        }

        @SubscribeEvent
        public void onClientConnectedToServer(FMLNetworkEvent.ClientConnectedToServerEvent event) {
            LOGGER.info("Client connected to server: isSinglePlayer={}, isLocal={}",
                    Minecraft.getMinecraft().isSingleplayer(), event.isLocal());
            isConnectedToServer = true;
            startDonationProvider();
            LOGGER.debug("isConnectedToServer set to true");
        }

        @SubscribeEvent
        public void onClientDisconnectionFromServer(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            LOGGER.info("Client disconnected from server");
            isConnectedToServer = false;
            stopDonationProvider();
            commands.clear();
            LOGGER.debug("isConnectedToServer set to false, command queue cleared");
        }
    }
}