package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.command.DPICommand;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.donatepay.DonatePayApiClient;
import com.bogdan3000.dintegrate.donatepay.DonatePayWebSocketHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Mod(modid = DonateIntegrate.MOD_ID, name = DonateIntegrate.NAME, version = "1.3.1")
public class DonateIntegrate {
    public static final String MOD_ID = "dintegrate";
    public static final String NAME = "DonateIntegrate";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static List<CommandToExecute> commands = new ArrayList<>();
    @Instance
    public static DonateIntegrate instance;

    private static DonatePayWebSocketHandler wsHandler;
    private static ExecutorService commandExecutor;

    public static class CommandToExecute {
        public final String command;
        public final String playerName;

        public CommandToExecute(String command, String playerName) {
            this.command = command;
            this.playerName = playerName;
        }
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("DonateIntegrate initializing");
        ConfigHandler.register(event.getSuggestedConfigurationFile());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Registering server tick handler");
        MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
        commandExecutor = Executors.newFixedThreadPool(2);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        LOGGER.info("Registering /dpi command");
        event.registerServerCommand(new DPICommand());
        startWebSocketHandler();
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        LOGGER.info("Shutting down DonateIntegrate");
        stopWebSocketHandler();
        if (commandExecutor != null) {
            commandExecutor.shutdown();
        }
    }

    public static void startWebSocketHandler() {
        stopWebSocketHandler();
        LOGGER.info("Starting WebSocket handler");
        wsHandler = new DonatePayWebSocketHandler(new DonatePayApiClient());
        new Thread(wsHandler::start, "WebSocketThread").start();
    }

    public static void stopWebSocketHandler() {
        if (wsHandler != null) {
            LOGGER.info("Stopping WebSocket handler");
            wsHandler.stop();
            wsHandler = null;
        }
    }

    public static class ServerTickHandler {
        private int tickCounter = 0;

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            tickCounter++;
            if (tickCounter % 6000 == 0) {
                if (wsHandler == null) {
                    LOGGER.info("WebSocket handler not running, restarting");
                    startWebSocketHandler();
                }
            }

            if (tickCounter % 100 == 0) {
                ConfigHandler.checkAndReloadConfig();
            }

            if (tickCounter % 10 == 0 && !commands.isEmpty()) {
                CommandToExecute cmd = commands.remove(0);
                commandExecutor.submit(() -> {
                    Minecraft mc = Minecraft.getMinecraft();
                    if (mc.player != null) {
                        mc.addScheduledTask(() -> {
                            if (cmd.command.startsWith("/")) {
                                mc.player.sendChatMessage(cmd.command);
                                LOGGER.debug("Sent command as {}: {}", cmd.playerName, cmd.command);
                            } else {
                                mc.player.sendChatMessage(cmd.command);
                                LOGGER.debug("Sent message as {}: {}", cmd.playerName, cmd.command);
                            }
                        });
                    }
                });
            }
        }
    }
}