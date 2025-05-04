package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.gui.DonateIntegrateGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.InputEvent;
import org.lwjgl.input.Keyboard;

public class KeyHandler {
    public static final KeyBinding KEY_OPEN_GUI = new KeyBinding("key.dintegrate.open_gui", Keyboard.KEY_P, "key.categories.dintegrate");

    @SubscribeEvent
    public void onKeyInput(InputEvent.KeyInputEvent event) {
        if (KEY_OPEN_GUI.isPressed()) {
            Minecraft.getMinecraft().displayGuiScreen(new DonateIntegrateGui());
        }
    }
}