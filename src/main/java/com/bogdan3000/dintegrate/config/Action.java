package com.bogdan3000.dintegrate.config;

import net.minecraft.client.Minecraft;
import java.util.ArrayList;
import java.util.List;

public class Action {
    private float sum;
    private boolean enabled;
    private int priority;
    private List<String> commands;
    private ExecutionMode executionMode;

    public enum ExecutionMode {
        ALL,
        RANDOM_ONE
    }

    @SuppressWarnings("unused")
    public Action() {
        this.sum = 0.0f;
        this.enabled = true;
        this.priority = 1;
        this.commands = new ArrayList<>();
        this.executionMode = ExecutionMode.ALL;
    }

    public Action(float sum, boolean enabled, int priority, List<String> commands, ExecutionMode executionMode) {
        if (sum <= 0) throw new IllegalArgumentException("Сумма должна быть положительной");
        if (priority < 0) throw new IllegalArgumentException("Приоритет не может быть отрицательным");
        this.sum = sum;
        this.enabled = enabled;
        this.priority = priority;
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.ALL;
    }

    public float getSum() { return sum; }

    @SuppressWarnings("unused")
    public void setSum(float sum) {
        if (sum <= 0) throw new IllegalArgumentException("Сумма должна быть положительной");
        this.sum = sum;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public int getPriority() { return priority; }

    @SuppressWarnings("unused")
    public void setPriority(int priority) {
        if (priority < 0) throw new IllegalArgumentException("Приоритет не может быть отрицательным");
        this.priority = priority;
    }

    public List<String> getCommands() { return commands; }

    @SuppressWarnings("unused")
    public void setCommands(List<String> commands) {
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
    }

    public ExecutionMode getExecutionMode() { return executionMode; }

    @SuppressWarnings("unused")
    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.ALL;
    }

    // ✅ Добавлен метод выполнения команд с поддержкой /delay
    public void execute() {
        if (commands == null || commands.isEmpty()) return;

        new Thread(() -> {
            for (String command : commands) {
                if (command == null || command.trim().isEmpty()) continue;

                if (command.toLowerCase().startsWith("/delay")) {
                    try {
                        String[] parts = command.split(" ");
                        if (parts.length > 1) {
                            int seconds = Integer.parseInt(parts[1]);
                            System.out.println("[DonateIntegrate] Delay " + seconds + " seconds...");
                            Thread.sleep(seconds * 1000L);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    continue;
                }

                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (Minecraft.getMinecraft().player != null) {
                        Minecraft.getMinecraft().player.sendChatMessage(command);
                        System.out.println("[DonateIntegrate] Executed: " + command);
                    }
                });
            }
        }, "DonateIntegrate-ActionThread").start();
    }
}