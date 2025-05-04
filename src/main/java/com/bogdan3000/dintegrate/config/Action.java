package com.bogdan3000.dintegrate.config;

import java.util.ArrayList;
import java.util.List;

public class Action {
    private int sum;
    private String message;
    private List<String> commands;
    private ExecutionMode executionMode;

    public enum ExecutionMode {
        SEQUENTIAL,
        RANDOM_ONE,
        RANDOM_MULTIPLE,
        ALL
    }

    public Action() {
        this.commands = new ArrayList<>();
        this.executionMode = ExecutionMode.SEQUENTIAL;
    }

    public Action(int sum, String message, List<String> commands, ExecutionMode executionMode) {
        this.sum = sum;
        this.message = message != null ? message : "";
        this.commands = commands != null ? new ArrayList<>(commands) : new ArrayList<>();
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.SEQUENTIAL;
    }

    public int getSum() {
        return sum;
    }

    public void setSum(int sum) {
        this.sum = sum;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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
        this.executionMode = executionMode != null ? executionMode : ExecutionMode.SEQUENTIAL;
    }
}