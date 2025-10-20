package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class DonateIntegrateScreen extends Screen {

    private final Config config;
    private final DonatePayProvider provider;
    private final boolean connected;

    private TabBase activeTab;
    private TabConfig tabConfig;
    private TabCommands tabCommands;
    private TabMisc tabMisc;
    private TabInfo tabInfo;

    public DonateIntegrateScreen(Config config, DonatePayProvider provider, boolean connected) {
        super(Component.literal("DonateIntegrate Manager"));
        this.config = config;
        this.provider = provider;
        this.connected = connected;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int topY = 20;

        int buttonWidth = 100;
        int spacing = 5;
        int startX = centerX - (buttonWidth * 2 + spacing * 3) / 2;

        // Главное меню
        addRenderableWidget(Button.builder(Component.literal("Config"), b -> switchTab(tabConfig))
                .bounds(startX, topY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Commands"), b -> switchTab(tabCommands))
                .bounds(startX + buttonWidth + spacing, topY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Misc"), b -> switchTab(tabMisc))
                .bounds(startX + (buttonWidth + spacing) * 2, topY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Info"), b -> switchTab(tabInfo))
                .bounds(startX + (buttonWidth + spacing) * 3, topY, buttonWidth, 20).build());

        // Инициализация вкладок (один раз)
        tabConfig = new TabConfig(this, config, provider);
        tabCommands = new TabCommands(this, config);
        tabMisc = new TabMisc(this);
        tabInfo = new TabInfo(this);

        // Первая вкладка по умолчанию
        if (activeTab == null) {
            activeTab = tabConfig;
            activeTab.init(minecraft, this.width, this.height);
            activeTab.addWidgets();
        }
    }

    private void switchTab(TabBase tab) {
        if (tab == null || tab == activeTab) return;

        clearWidgets();
        init(); // пересоздаёт верхнее меню
        activeTab = tab;
        activeTab.init(minecraft, this.width, this.height);
        activeTab.addWidgets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTicks);

        graphics.drawCenteredString(this.font, "DonateIntegrate Manager", this.width / 2, 5, 0xFFFFFF);

        if (activeTab != null)
            activeTab.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public Minecraft getMinecraft() {
        return this.minecraft;
    }

    public void addChildWidget(Button button) {
        addRenderableWidget(button);
    }
}