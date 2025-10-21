package com.bogdan3000.dintegrate.client;

import com.bogdan3000.dintegrate.client.gui.DonateIntegrateScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeybindHandler {

    public static final KeyMapping OPEN_GUI_KEY = new KeyMapping(
            "key.dintegrate.open_gui", // ID
            GLFW.GLFW_KEY_F8,          // клавиша (можешь заменить)
            "key.categories.dintegrate" // категория в настройках управления
    );

    @SubscribeEvent
    public static void registerKeyBindings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_GUI_KEY);
    }

    // === Реакция на нажатие ===
    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ForgeKeyHandler {

        @SubscribeEvent
        public static void onKeyInput(InputEvent.Key event) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            if (OPEN_GUI_KEY.consumeClick()) {
                mc.setScreen(new DonateIntegrateScreen());
            }
        }
    }
}