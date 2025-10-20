package com.bogdan3000.dintegrate.logic;

import com.bogdan3000.dintegrate.Config;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ActionHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Random RANDOM = new Random();
    private final Config config;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ActionHandler(Config config) {
        this.config = config;
    }

    public void execute(double amount, String name, String message) {
        Config.DonationRule rule = config.getRules().get(amount);
        if (rule == null) {
            LOGGER.warn("[DIntegrate] No donation rule for {}", amount);
            return;
        }

        List<String> commandsToRun = pickCommands(rule);
        double startDelay = rule.startDelay;

        executor.submit(() -> {
            try {
                if (startDelay > 0) {
                    LOGGER.info("[DIntegrate] Starting delay: {} seconds", startDelay);
                    Thread.sleep((long) (startDelay * 1000));
                }
                runCommands(commandsToRun, name, message, amount);
            } catch (Exception e) {
                LOGGER.error("[DIntegrate] Error executing donation actions", e);
            }
        });
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
                Collections.shuffle(cmds);
                return cmds;
            default:
                if (mode.startsWith("random")) {
                    try {
                        int n = Integer.parseInt(mode.replace("random", ""));
                        n = Math.min(n, cmds.size());
                        Collections.shuffle(cmds);
                        return cmds.subList(0, n);
                    } catch (NumberFormatException ignored) {}
                }
        }

        LOGGER.warn("[DIntegrate] Unknown mode '{}', defaulting to 'all'", rule.mode);
        return cmds;
    }

    private void runCommands(List<String> commands, String name, String message, double amount) {
        for (String rawCmd : commands) {
            String cmd = rawCmd
                    .replace("{name}", name)
                    .replace("{sum}", String.valueOf(amount))
                    .replace("{message}", message)
                    .trim();

            try {
                // === Обработка задержек ===
                if (cmd.toLowerCase(Locale.ROOT).startsWith("delay")) {
                    double seconds = parseDelay(cmd, 1.0);
                    LOGGER.info("[DIntegrate] Delay {} seconds", seconds);
                    Thread.sleep((long) (seconds * 1000));
                    continue;
                }
                if (cmd.toLowerCase(Locale.ROOT).startsWith("randomdelay") ||
                        cmd.toLowerCase(Locale.ROOT).startsWith("rand")) {
                    double seconds = parseRandomDelay(cmd);
                    LOGGER.info("[DIntegrate] Random delay {} seconds", seconds);
                    Thread.sleep((long) (seconds * 1000));
                    continue;
                }
                if (cmd.matches("^\\d+(\\.\\d+)?$")) {
                    double seconds = Double.parseDouble(cmd);
                    LOGGER.info("[DIntegrate] Numeric delay {} seconds", seconds);
                    Thread.sleep((long) (seconds * 1000));
                    continue;
                }

                // === Выполнение как реальной команды ===
                Minecraft.getInstance().execute(() -> {
                    LocalPlayer player = Minecraft.getInstance().player;
                    if (player == null || player.connection == null) {
                        LOGGER.warn("[DIntegrate] Player not ready for command: {}", cmd);
                        return;
                    }

                    try {
                        String command = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                        player.connection.sendCommand(command);
                        LOGGER.info("[DIntegrate] Executed command: {}", cmd);
                    } catch (Exception e) {
                        LOGGER.error("[DIntegrate] Error executing command '{}'", cmd, e);
                    }
                });

            } catch (Exception e) {
                LOGGER.error("[DIntegrate] Error executing '{}'", cmd, e);
            }
        }
    }

    private double parseDelay(String cmd, double def) {
        String[] parts = cmd.split("\\s+");
        if (parts.length > 1) {
            try {
                return Double.parseDouble(parts[1]);
            } catch (Exception ignored) {}
        }
        return def;
    }

    private double parseRandomDelay(String cmd) {
        String[] parts = cmd.split("\\s+");
        if (parts.length > 1 && parts[1].contains("-")) {
            try {
                String[] range = parts[1].split("-");
                double min = Double.parseDouble(range[0]);
                double max = Double.parseDouble(range[1]);
                return min + RANDOM.nextDouble() * (max - min);
            } catch (Exception ignored) {}
        }
        return 1.0;
    }
}