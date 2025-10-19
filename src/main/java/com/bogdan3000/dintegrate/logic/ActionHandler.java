package com.bogdan3000.dintegrate.logic;

import com.bogdan3000.dintegrate.Config;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

public class ActionHandler {
    private final MinecraftServer server;

    public ActionHandler(MinecraftServer server) {
        this.server = server;
    }

    public void execute(double sum, String name, String message) {
        Config config = new Config();
        config.load();
        Map<Double, String> rules = config.getRules();

        for (Map.Entry<Double, String> entry : rules.entrySet()) {
            if (sum >= entry.getKey()) {
                String cmd = entry.getValue()
                        .replace("{name}", name)
                        .replace("{sum}", String.valueOf(sum))
                        .replace("{msg}", message);
                System.out.println("[DIntegrate] Executing command: " + cmd);
                server.execute(() ->
                        server.getCommands().performPrefixedCommand(
                                server.createCommandSourceStack().withPermission(4),
                                cmd
                        )
                );
                break;
            }
        }
    }
}