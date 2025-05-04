package com.bogdan3000.dintegrate.config;

import java.util.ArrayList;
import java.util.List;

public class ModConfig {
    private boolean enabled = true;
    private String donpayToken = "";
    private String userId = "";
    private int lastDonate = 0;
    private List<Action> actions = new ArrayList<>();

    public ModConfig() {
        // Пример действия по умолчанию
        actions.add(new Action(10, "{username} donated 10!", "give {username} diamond 1"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDonpayToken() {
        return donpayToken;
    }

    public void setDonpayToken(String donpayToken) {
        this.donpayToken = donpayToken;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public int getLastDonate() {
        return lastDonate;
    }

    public void setLastDonate(int lastDonate) {
        this.lastDonate = lastDonate;
    }

    public List<Action> getActions() {
        return actions;
    }

    public void setActions(List<Action> actions) {
        this.actions = actions;
    }
}