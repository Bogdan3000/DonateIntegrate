package com.bogdan3000.dintegrate.logic;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.Config.DonationRule;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class ActionHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Config config;

    public ActionHandler(Config config) {
        this.config = config;
    }

    public void execute(double amount, String donorName, String donorMessage) {
        DonationRule rule = null;
        for (var entry : config.getRules().entrySet()) {
            if (Math.abs(entry.getKey() - amount) < 0.0001) {
                rule = entry.getValue();
                break;
            }
        }

        if (rule == null) {
            LOGGER.warn("[DIntegrate] No rule found for amount {} ({} rules in config)", amount, config.getRules().size());
            return;
        }

        final DonationRule ruleFinal = rule; // 👈 добавляем это
        List<String> plan = buildPlan(ruleFinal);
        if (plan.isEmpty()) {
            LOGGER.warn("[DIntegrate] Rule {} has empty command list", amount);
            return;
        }

        final String name = donorName != null ? donorName : "Player";
        final String msg = donorMessage != null ? donorMessage : "";
        final String sum = formatSum(amount);

        Thread worker = new Thread(() -> {
            LOGGER.info("[DIntegrate] Executing rule for amount {} ({} commands, mode={})", amount, plan.size(), ruleFinal.mode);
            for (String raw : plan) {
                String cmd = raw.replace("{name}", name)
                        .replace("{message}", msg)
                        .replace("{sum}", sum)
                        .trim();

                if (cmd.isEmpty()) continue;

                // задержки
                if (handleDelay(cmd)) continue;

                // логируем и выполняем
                LOGGER.info("[DIntegrate] Executing: {}", cmd);
                runOnMainThread(cmd);
            }
        }, "DIntegrate-ActionWorker");
        worker.setDaemon(true);
        worker.start();
    }

    private List<String> buildPlan(DonationRule rule) {
        List<String> base = new ArrayList<>(rule.commands);
        String mode = rule.mode != null ? rule.mode.toLowerCase(Locale.ROOT) : "all";

        return switch (mode) {
            case "random" -> Collections.singletonList(base.get(ThreadLocalRandom.current().nextInt(base.size())));
            case "random_all" -> {
                Collections.shuffle(base);
                yield base;
            }
            default -> {
                if (mode.startsWith("random")) {
                    int n = parseInt(mode.replaceAll("\\D", ""), 1);
                    Collections.shuffle(base);
                    yield base.subList(0, Math.min(n, base.size()));
                } else yield base;
            }
        };
    }

    private int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    private boolean handleDelay(String line) {
        try {
            if (line.startsWith("delay ")) {
                double sec = Double.parseDouble(line.substring(6).trim());
                LOGGER.info("[DIntegrate] Waiting {} seconds", sec);
                Thread.sleep((long) (sec * 1000));
                return true;
            }
            if (line.startsWith("randomdelay ")) {
                String[] parts = line.substring(12).trim().split("-");
                double a = Double.parseDouble(parts[0]);
                double b = Double.parseDouble(parts[1]);
                double sec = a + ThreadLocalRandom.current().nextDouble() * (b - a);
                LOGGER.info("[DIntegrate] Waiting {:.2f} seconds (randomdelay)", sec);
                Thread.sleep((long) (sec * 1000));
                return true;
            }
        } catch (Exception e) {
            LOGGER.warn("[DIntegrate] Delay parse error: {}", e.toString());
        }
        return false;
    }

    private void runOnMainThread(String line) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            LocalPlayer player = mc.player;
            if (player == null || player.connection == null) {
                LOGGER.warn("[DIntegrate] No player connection available for: {}", line);
                return;
            }

            try {
                if (line.startsWith("/")) {
                    String command = line.substring(1);
                    player.connection.sendCommand(command);
                } else if (line.startsWith("§")) {
                    // внутренняя информация, просто показать сообщение
                    player.sendSystemMessage(net.minecraft.network.chat.Component.literal(line));
                } else {
                    player.connection.sendChat(line);
                }
            } catch (Exception e) {
                LOGGER.error("[DIntegrate] Failed to execute command '{}'", line, e);
            }
        });
    }

    private String formatSum(double a) {
        String s = Double.toString(a);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }
}