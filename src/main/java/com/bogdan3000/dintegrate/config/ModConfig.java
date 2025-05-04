package com.bogdan3000.dintegrate.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ModConfig {
    private boolean enabled = true;
    private String donpayToken = "";
    private String userId = "";
    private int lastDonate = 0;
    private List<Action> actions = new ArrayList<>();

    public ModConfig() {
        actions.add(new Action(
                10,
                true,
                Arrays.asList(
                        "/give @s diamond 1",
                        "/say Thanks {username} for the 10 donation: {message}"
                ),
                Action.ExecutionMode.ALL
        ));
        actions.add(new Action(
                20,
                true,
                Arrays.asList(
                        "/give @s emerald 5",
                        "/effect @s speed 30 1",
                        "/say Epic donation from {username}: {message}"
                ),
                Action.ExecutionMode.RANDOM_ONE
        ));
        actions.add(new Action(
                50,
                true,
                Arrays.asList(
                        "/give @s diamond_block 1",
                        "/effect @s strength 60 2",
                        "/say Legendary donation from {username}: {message}"
                ),
                Action.ExecutionMode.ALL
        ));
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
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }
}