package com.bogdan3000.dintegrate;

import com.bogdan3000.dintegrate.gui.DonateIntegrateGui;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class NetworkHandler {
    public static final SimpleNetworkWrapper INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(DonateIntegrate.MOD_ID);

    public static void init() {
        INSTANCE.registerMessage(OpenGuiMessage.Handler.class, OpenGuiMessage.class, 0, Side.CLIENT);
    }

    public static class OpenGuiMessage implements IMessage {
        public OpenGuiMessage() {}

        @Override
        public void fromBytes(ByteBuf buf) {
            // No data to read
        }

        @Override
        public void toBytes(ByteBuf buf) {
            // No data to write
        }

        public static class Handler implements IMessageHandler<OpenGuiMessage, IMessage> {
            @Override
            public IMessage onMessage(OpenGuiMessage message, MessageContext ctx) {
                if (ctx.side == Side.CLIENT) {
                    Minecraft.getMinecraft().addScheduledTask(() -> Minecraft.getMinecraft().displayGuiScreen(new DonateIntegrateGui()));
                }
                return null;
            }
        }
    }
}