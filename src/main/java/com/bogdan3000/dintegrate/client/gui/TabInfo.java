package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.DonateIntegrate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TabInfo extends TabBase {

    public TabInfo() {
        super("Info");
    }

    @Override
    public void init(Minecraft mc, int width, int height) {
        super.init(mc, width, height);
        // Тут можно добавить кнопки или текстовые поля, если понадобится
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(0, 0, width, height, 0xAA000000);
        graphics.drawCenteredString(Minecraft.getInstance().font, "Info Tab — справка по модулю", centerX(), 45, 0x99FF99);

        var provider = DonateIntegrate.getDonateProvider();
        String status = (provider != null && provider.isConnected()) ? "Connected" : "Disconnected";
        graphics.drawCenteredString(Minecraft.getInstance().font, "Connection: " + status, centerX(), 65, 0xFFFFFF);

        var cfg = DonateIntegrate.getConfig();
        int rules = cfg != null ? cfg.getRules().size() : 0;
        graphics.drawCenteredString(Minecraft.getInstance().font, "Rules loaded: " + rules, centerX(), 80, 0xFFFFFF);

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}