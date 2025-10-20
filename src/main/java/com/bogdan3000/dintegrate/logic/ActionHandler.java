package com.bogdan3000.dintegrate.logic;

import com.bogdan3000.dintegrate.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

import java.util.Map;

public class ActionHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final MinecraftServer server;
    private final Config config;

    public ActionHandler(MinecraftServer server, Config config) {
        this.server = server;
        this.config = config;
    }

    public void execute(double sum, String name, String message) {
        Map<Double, String> rules = config.getRules();
        String safeName = name != null ? name : "Unknown";
        String safeMsg = message != null ? message : "";

        rules.entrySet().stream()
                .sorted(Map.Entry.<Double, String>comparingByKey().reversed())
                .filter(e -> sum >= e.getKey())
                .findFirst()
                .ifPresent(entry -> {
                    String cmd = entry.getValue()
                            .replace("{name}", safeName)
                            .replace("{sum}", String.format("%.2f", sum))
                            .replace("{msg}", safeMsg);

                    LOGGER.info("[DIntegrate] Executing command: {}", cmd);
                    try {
                        server.execute(() ->
                                server.getCommands().performPrefixedCommand(
                                        server.createCommandSourceStack().withPermission(4),
                                        cmd
                                )
                        );
                    } catch (Exception e) {
                        LOGGER.error("[DIntegrate] Failed to execute command: {}", cmd, e);
                    }
                });
    }
}