package com.bogdan3000.dintegrate.config;

import java.util.ArrayList;
import java.util.List;

public class Action {
    private int sum;
    private boolean enabled;
    private List<String> commands;
    private ExecutionMode executionMode;

    public enum ExecutionMode {
        ALL,
        RANDOM_ONE
    }

    public Action() {
        this.commands = new ArrayList<>();
        this.executionMode = ExecutionMode.ALL;
        this.enabled = true;
    }

    public Action(int sum, boolean enabled, List<String> commands, ExecutionMode executionMode) {
        this.sum = sum;
        this.enabled = enabled;
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.ALL;
    }

    public int getSum() {
        return sum;
    }

    public void setSum(int sum) {
        this.sum = sum;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
    }

    public ExecutionMode getExecutionMode() {
        return executionMode;
    }

    public void setExecutionMode(ExecutionMode executionMode) {
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.ALL;
    }
}