package com.bogdan3000.dintegrate.config;

public class Action {
    private int sum;
    private String message;
    private String command;

    public Action() {
    }

    public Action(int sum, String message, String command) {
        this.sum = sum;
        this.message = message;
        this.command = command;
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

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }
}