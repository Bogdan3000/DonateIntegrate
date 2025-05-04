package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.command.DPICommand;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.donation.DonationProvider;
import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import com.bogdan3000.dintegrate.gui.DonateIntegrateGui;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
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
    private static final long COMMAND_COOLDOWN_MS = 1; // 0.01 second between commands

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
        LOGGER.info("Initializing DonateIntegrate");
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
        LOGGER.info("Registering server tick handler and key handler");
        MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
        MinecraftForge.EVENT_BUS.register(new KeyHandler());
        commandExecutor = Executors.newFixedThreadPool(2);
        initializeDonationProvider();
        NetworkHandler.init();
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        LOGGER.info("Registering /dpi command");
        event.registerServerCommand(new DPICommand());
        startDonationProvider();
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        LOGGER.info("Stopping DonateIntegrate");
        stopDonationProvider();
        if (commandExecutor != null) {
            commandExecutor.shutdown();
            try {
                if (!commandExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    commandExecutor.shutdownNow();
                    LOGGER.warn("Forced shutdown of command pool");
                }
            } catch (InterruptedException e) {
                LOGGER.error("Error stopping command pool: {}", e.getMessage());
                commandExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
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
                            if (Minecraft.getMinecraft().currentScreen instanceof DonateIntegrateGui) {
                                String donationInfo = String.format("ID: %d, User: %s, Amount: %.2f, Message: %s",
                                        event.id(), event.username(), event.amount(), event.message());
                                ((DonateIntegrateGui) Minecraft.getMinecraft().currentScreen).addDonationToHistory(donationInfo);
                            }
                        });
            } catch (Exception e) {
                LOGGER.error("Error processing donation #{}: {}", event.id(), e.getMessage());
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
            LOGGER.error("Error starting donation provider: {}", e.getMessage());
        }
    }

    public static void stopDonationProvider() {
        try {
            donationProvider.disconnect();
            LOGGER.info("Donation provider stopped");
        } catch (Exception e) {
            LOGGER.error("Error stopping donation provider: {}", e.getMessage());
        }
    }

    public static void addCommand(CommandToExecute command) {
        try {
            commands.add(command);
            LOGGER.debug("Added command to queue: {}", command.command);
        } catch (Exception e) {
            LOGGER.error("Error adding command: {}", e.getMessage());
        }
    }

    public static class ServerTickHandler {
        private int tickCounter = 0;

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

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
                    LOGGER.error("Error checking connection: {}", e.getMessage());
                }
            }

            if (tickCounter % 100 == 0) {
                try {
                    ConfigHandler.checkAndReloadConfig();
                } catch (Exception e) {
                    LOGGER.error("Error reloading configuration: {}", e.getMessage());
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
                    commandExecutor.submit(() -> {
                        try {
                            Minecraft mc = Minecraft.getMinecraft();
                            if (mc.player != null) {
                                mc.addScheduledTask(() -> {
                                    mc.player.sendChatMessage(cmd.command);
                                    LOGGER.debug("Executed command from {}: {}", cmd.playerName, cmd.command);
                                });
                            } else {
                                LOGGER.warn("Player unavailable for command: {}", cmd.command);
                            }
                        } catch (Exception e) {
                            LOGGER.error("Error executing command '{}': {}", cmd.command, e.getMessage());
                        }
                    });
                }
            }
        }
    }
}