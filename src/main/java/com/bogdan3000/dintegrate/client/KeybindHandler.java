package com.bogdan3000.dintegrate.client;

import com.bogdan3000.dintegrate.Config;
import com.bogdan3000.dintegrate.client.gui.DonateIntegrateScreen;
import com.bogdan3000.dintegrate.donation.DonatePayProvider;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KeybindHandler {

    public static final KeyMapping OPEN_GUI = new KeyMapping(
            "key.dintegrate.open_gui", // можно поменять текст
            GLFW.GLFW_KEY_P,           // клавиша по умолчанию
            "key.categories.misc"      // категория в настройках управления
    );

    private static Config config;
    private static DonatePayProvider provider;

    private static boolean wasPressed = false;

    public static void init(Config cfg, DonatePayProvider prov) {
        config = cfg;
        provider = prov;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        if (OPEN_GUI.isDown()) {
            if (!wasPressed) {
                wasPressed = true;
                if (mc.player != null && mc.screen == null && config != null) {
                    mc.setScreen(new DonateIntegrateScreen(config, provider, provider != null && provider.isConnected()));
                }
            }
        } else {
            wasPressed = false;
        }
    }

    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class Register {
        @SubscribeEvent
        public static void registerKeyMappings(RegisterKeyMappingsEvent e) {
            e.register(OPEN_GUI);
        }
    }
}