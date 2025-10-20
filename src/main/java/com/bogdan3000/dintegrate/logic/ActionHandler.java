package com.bogdan3000.dintegrate.logic;

import com.bogdan3000.dintegrate.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;

import java.util.*;

public class ActionHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RANDOM = new Random();
    private final Config config;

    public ActionHandler(Config config) {
        this.config = config;
    }

    public void execute(double amount, String name, String message) {
        Config.DonationRule rule = config.getRules().get(amount);
        if (rule == null) {
            LOGGER.warn("[DIntegrate] No donation rule for {}", amount);
            return;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) {
            LOGGER.error("[DIntegrate] Player not available, cannot execute commands");
            return;
        }

        List<String> commandsToRun = pickCommands(rule);

        for (String rawCmd : commandsToRun) {
            String cmd = rawCmd
                    .replace("%name%", name)
                    .replace("%sum%", String.valueOf(amount))
                    .replace("%message%", message);

            try {
                if (cmd.startsWith("/")) {
                    player.connection.sendCommand(cmd.substring(1));
                    LOGGER.info("[DIntegrate] Sent command: {}", cmd);
                } else {
                    player.connection.sendChat(cmd);
                    LOGGER.info("[DIntegrate] Sent chat message: {}", cmd);
                }
            } catch (Exception e) {
                LOGGER.error("[DIntegrate] Error sending command '{}'", cmd, e);
            }
        }
    }

    private List<String> pickCommands(Config.DonationRule rule) {
        List<String> cmds = new ArrayList<>(rule.commands);
        if (cmds.isEmpty()) return Collections.emptyList();

        String mode = rule.mode.toLowerCase(Locale.ROOT);

        switch (mode) {
            case "all":
                return cmds;

            case "random":
                return List.of(cmds.get(RANDOM.nextInt(cmds.size())));

            case "random_all":
                Collections.shuffle(cmds, RANDOM);
                return cmds;

            default:
                if (mode.startsWith("random")) {
                    try {
                        int n = Integer.parseInt(mode.replace("random", ""));
                        n = Math.min(n, cmds.size());
                        Collections.shuffle(cmds, RANDOM);
                        return cmds.subList(0, n);
                    } catch (Exception e) {
                        LOGGER.error("[DIntegrate] Invalid mode '{}'", rule.mode);
                    }
                }
                LOGGER.warn("[DIntegrate] Unknown mode '{}', defaulting to 'all'", rule.mode);
                return cmds;
        }
    }
}