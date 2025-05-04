package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.command.DPICommand;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import net.minecraft.server.MinecraftServer;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Mod(modid = DonateIntegrate.MOD_ID, name = DonateIntegrate.NAME, version = "1.1.0")
public class DonateIntegrate {

    public static final String MOD_ID = "dintegrate";
    public static final String NAME = "DonateIntegrate";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);
    public static List<String> commands = new ArrayList<>();
    @Instance
    public static DonateIntegrate instance;

    // Reference to the WebSocket client
    private static WebSocketClientConn wsClient;

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.info("DonateIntegrate is starting");
        ConfigHandler.register(event.getSuggestedConfigurationFile());
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Initializing event bus for server tick handler");
        MinecraftForge.EVENT_BUS.register(new ServerTickHandler());
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        LOGGER.info("Registering /dpi command");
        event.registerServerCommand(new DPICommand());

        // Start the WebSocket client when server starts
        startWebSocketClient();
    }

    @EventHandler
    public void serverStopping(FMLServerStoppingEvent event) {
        LOGGER.info("Stopping WebSocket client");
        stopWebSocketClient();
    }

    public static void startWebSocketClient() {
        stopWebSocketClient(); // Ensure we stop any existing connection

        LOGGER.info("Starting WebSocket client");
        wsClient = new WebSocketClientConn();
        wsClient.start();
    }

    public static void stopWebSocketClient() {
        if (wsClient != null) {
            LOGGER.info("Stopping active WebSocket client");
            wsClient.stopClient();
            wsClient = null;
        }
    }

    public static class ServerTickHandler {
        private final AtomicInteger tickCounter = new AtomicInteger();

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;

            // Check if WebSocket client needs to be restarted every 5 minutes
            int ticks = tickCounter.getAndIncrement();
            if (ticks % 6000 == 0) { // ~5 minutes (20 ticks per second * 60 seconds * 5 minutes)
                if (wsClient == null || !wsClient.isAlive()) {
                    LOGGER.info("WebSocket client not running, restarting");
                    startWebSocketClient();
                }
            }

            // Execute commands every 40 ticks (~2 seconds)
            if (ticks % 40 == 0 && !commands.isEmpty()) {
                MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
                if (server != null) {
                    String command = commands.get(0);
                    server.getCommandManager().executeCommand(server, command);
                    commands.remove(0);
                    LOGGER.debug("Executed command: {}", command);
                }
            }
        }
    }
}

// JsonObject jsonResponse = new JsonParser().parse(responseStr).getAsJsonObject();
// JsonObject jsonMsg = new JsonParser().parse(message).getAsJsonObject();
// JsonObject jsonResponse = new JsonParser().parse(response.toString()).getAsJsonObject();