package com.bogdan3000.dintegrate.client.gui;

import com.bogdan3000.dintegrate.DonateIntegrate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public class TabCommands extends TabBase {

    public TabCommands() {
        super("Commands");
    }

    @Override
    public void init(Minecraft mc, int width, int height) {
        super.init(mc, width, height);
        // Здесь позже добавим список правил/команд с кнопкой "Test"
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        graphics.fill(0, 0, width, height, 0xAA000000);
        graphics.drawCenteredString(Minecraft.getInstance().font, "Commands Tab — список триггеров", centerX(), 45, 0xFFD580);

        var cfg = DonateIntegrate.getConfig();
        if (cfg != null) {
            int y = 65;
            for (var e : cfg.getRules().entrySet()) {
                double amount = e.getKey();
                String mode = e.getValue().mode;
                int count = e.getValue().commands != null ? e.getValue().commands.size() : 0;
                graphics.drawCenteredString(Minecraft.getInstance().font,
                        "Rule: " + amount + "₽  | mode=" + mode + " | commands=" + count,
                        centerX(), y, 0xFFFFFF);
                y += 12;
                if (y > height - 20) break; // простая защита от переполнения экрана
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }
}