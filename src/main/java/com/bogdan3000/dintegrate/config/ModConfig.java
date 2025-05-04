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
                10.0f,
                true,
                1,
                Arrays.asList(
                        "/give @s diamond 1",
                        "/say Спасибо {username} за донат {amount}: {message}"
                ),
                Action.ExecutionMode.ALL
        ));
        actions.add(new Action(
                20.0f,
                true,
                2,
                Arrays.asList(
                        "/give @s emerald 5",
                        "/effect @s speed 30 1",
                        "/say Эпичный донат от {username}: {message}"
                ),
                Action.ExecutionMode.RANDOM_ONE
        ));
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getDonpayToken() { return donpayToken; }
    public void setDonpayToken(String donpayToken) {
        this.donpayToken = donpayToken != null ? donpayToken : "";
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) {
        this.userId = userId != null ? userId : "";
    }

    public int getLastDonate() { return lastDonate; }
    public void setLastDonate(int lastDonate) {
        this.lastDonate = Math.max(0, lastDonate);
    }

    public List<Action> getActions() { return actions; }
    public void setActions(List<Action> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }
}