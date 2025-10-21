package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.DonateIntegrate;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class DonateIntegrateScreen extends Screen {

    private final List<Button> tabButtons = new ArrayList<>();
    private TabBase currentTab;
    private final Config config;

    public DonateIntegrateScreen() {
        super(Component.literal("DonateIntegrate"));
        this.config = DonateIntegrate.getConfig();
    }

    @Override
    protected void init() {
        tabButtons.clear();
        int x = width / 2 - 170;
        int y = 15;

        // Кнопки вкладок
        Button btnInfo = Button.builder(Component.literal("Info"), b -> switchTab(new TabInfo()))
                .bounds(x, y, 80, 20).build();
        Button btnConfig = Button.builder(Component.literal("Config"), b -> switchTab(new TabConfig(config)))
                .bounds(x + 90, y, 80, 20).build();
        Button btnCommands = Button.builder(Component.literal("Commands"), b -> switchTab(new TabCommands()))
                .bounds(x + 180, y, 80, 20).build();
        Button btnMisc = Button.builder(Component.literal("Misc"), b -> switchTab(new TabMisc()))
                .bounds(x + 270, y, 80, 20).build();

        addRenderableWidget(btnInfo);
        addRenderableWidget(btnConfig);
        addRenderableWidget(btnCommands);
        addRenderableWidget(btnMisc);

        tabButtons.add(btnInfo);
        tabButtons.add(btnConfig);
        tabButtons.add(btnCommands);
        tabButtons.add(btnMisc);

        // По умолчанию открываем Config
        switchTab(new TabConfig(config));
    }

    private void switchTab(TabBase tab) {
        clearWidgets();
        // вернуть кнопки табов
        tabButtons.forEach(this::addRenderableWidget);

        this.currentTab = tab;
        currentTab.init(minecraft, width, height);
        currentTab.getWidgets().forEach(this::addRenderableWidget);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        renderBackground(gfx);
        if (currentTab != null) {
            currentTab.render(gfx, mouseX, mouseY, partialTicks);
        }
        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}