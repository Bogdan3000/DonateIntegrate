package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.resources.I18n;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class DonateIntegrateGui extends GuiScreen {
    private static final int TAB_WIDTH = 60;
    private static final int TAB_HEIGHT = 40;
    private static final int CONTENT_MARGIN = 20;
    private static final int CONTENT_WIDTH = 400;
    private static final int CONTENT_HEIGHT = 300;
    private static final int TAB_COUNT = 4;
    private static final int ACTION_ITEM_HEIGHT = 30;
    private static final int ACTION_LIST_HEIGHT = 180;

    private int contentLeft, contentTop;
    private int currentTab = 0;
    private float fadeAnimation = 0.0f;
    private final List<TabButton> tabButtons = new ArrayList<>();
    private final List<String> donationHistory = new CopyOnWriteArrayList<>();

    private GuiTextField tokenField;
    private GuiTextField userIdField;
    private CustomButton saveSettingsButton;
    private CustomButton clearSettingsButton;
    private CustomButton addActionButton;
    private CustomButton editActionButton;
    private CustomButton deleteActionButton;
    private CustomButton reconnectButton;
    private CustomButton enableToggleButton;
    private int selectedActionIndex = -1;
    private float scrollOffset = 0.0f;
    private boolean isDraggingScrollbar = false;
    private int scrollbarHeight = 20;
    private int scrollbarTop;

    @Override
    public void initGui() {
        buttonList.clear();
        tabButtons.clear();
        Keyboard.enableRepeatEvents(true);
        fadeAnimation = 0.0f;
        selectedActionIndex = -1;
        scrollOffset = 0.0f;

        contentLeft = (width - CONTENT_WIDTH) / 2;
        contentTop = (height - CONTENT_HEIGHT) / 2;

        for (int i = 0; i < TAB_COUNT; i++) {
            TabButton tabButton = new TabButton(i, CONTENT_MARGIN, CONTENT_MARGIN + i * (TAB_HEIGHT + 10), TAB_WIDTH, TAB_HEIGHT, I18n.format("dintegrate.gui.tab." + getTabKey(i)));
            buttonList.add(tabButton);
            tabButtons.add(tabButton);
        }

        tokenField = new GuiTextField(100, fontRenderer, contentLeft + 20, contentTop + 60, CONTENT_WIDTH - 40, 20);
        tokenField.setMaxStringLength(100);
        tokenField.setText(ConfigHandler.getConfig().getDonpayToken());
        userIdField = new GuiTextField(101, fontRenderer, contentLeft + 20, contentTop + 110, CONTENT_WIDTH - 40, 20);
        userIdField.setMaxStringLength(20);
        userIdField.setText(ConfigHandler.getConfig().getUserId());
        saveSettingsButton = new CustomButton(102, contentLeft + 20, contentTop + CONTENT_HEIGHT - 60, 100, 24, I18n.format("dintegrate.gui.button.save"));
        clearSettingsButton = new CustomButton(103, contentLeft + CONTENT_WIDTH - 120, contentTop + CONTENT_HEIGHT - 60, 100, 24, I18n.format("dintegrate.gui.button.clear"));
        buttonList.add(saveSettingsButton);
        buttonList.add(clearSettingsButton);

        addActionButton = new CustomButton(104, contentLeft + 20, contentTop + CONTENT_HEIGHT - 60, 80, 24, I18n.format("dintegrate.gui.button.add"));
        editActionButton = new CustomButton(105, contentLeft + 110, contentTop + CONTENT_HEIGHT - 60, 80, 24, I18n.format("dintegrate.gui.button.edit"));
        editActionButton.enabled = false;
        deleteActionButton = new CustomButton(106, contentLeft + 200, contentTop + CONTENT_HEIGHT - 60, 80, 24, I18n.format("dintegrate.gui.button.delete"));
        deleteActionButton.enabled = false;
        buttonList.add(addActionButton);
        buttonList.add(editActionButton);
        buttonList.add(deleteActionButton);

        reconnectButton = new CustomButton(107, contentLeft + 20, contentTop + CONTENT_HEIGHT - 60, 100, 24, I18n.format("dintegrate.gui.button.reconnect"));
        enableToggleButton = new CustomButton(108, contentLeft + CONTENT_WIDTH - 120, contentTop + CONTENT_HEIGHT - 60, 100, 24,
                ConfigHandler.getConfig().isEnabled() ? I18n.format("dintegrate.gui.button.disable") : I18n.format("dintegrate.gui.button.enable"));
        buttonList.add(reconnectButton);
        buttonList.add(enableToggleButton);

        updateTabVisibility();
    }

    private String getTabKey(int index) {
        switch (index) {
            case 0: return "settings";
            case 1: return "actions";
            case 2: return "status";
            case 3: return "history";
            default: return "";
        }
    }

    private void updateTabVisibility() {
        tokenField.setVisible(currentTab == 0);
        userIdField.setVisible(currentTab == 0);
        saveSettingsButton.visible = currentTab == 0;
        clearSettingsButton.visible = currentTab == 0;

        addActionButton.visible = currentTab == 1;
        editActionButton.visible = currentTab == 1;
        deleteActionButton.visible = currentTab == 1;

        reconnectButton.visible = currentTab == 2;
        enableToggleButton.visible = currentTab == 2;
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button instanceof TabButton) {
            currentTab = button.id;
            selectedActionIndex = -1;
            editActionButton.enabled = false;
            deleteActionButton.enabled = false;
            scrollOffset = 0.0f;
            updateTabVisibility();
            return;
        }

        ModConfig config = ConfigHandler.getConfig();
        switch (button.id) {
            case 102:
                String token = tokenField.getText().trim();
                String userId = userIdField.getText().trim();
                if (token.isEmpty() || userId.isEmpty()) {
                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.empty_credentials"), false));
                    return;
                }
                config.setDonpayToken(token);
                config.setUserId(userId);
                ConfigHandler.save();
                DonateIntegrate.startDonationProvider();
                mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.settings_saved"), true));
                break;
            case 103:
                tokenField.setText("");
                userIdField.setText("");
                break;
            case 104:
                mc.displayGuiScreen(new ActionEditGui(this, null));
                break;
            case 105:
                if (selectedActionIndex >= 0 && selectedActionIndex < config.getActions().size()) {
                    Action action = config.getActions().get(selectedActionIndex);
                    mc.displayGuiScreen(new ActionEditGui(this, action));
                }
                break;
            case 106:
                if (selectedActionIndex >= 0 && selectedActionIndex < config.getActions().size()) {
                    config.getActions().remove(selectedActionIndex);
                    ConfigHandler.save();
                    selectedActionIndex = -1;
                    editActionButton.enabled = false;
                    deleteActionButton.enabled = false;
                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.action_deleted"), true));
                }
                break;
            case 107:
                DonateIntegrate.startDonationProvider();
                mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.reconnection_initiated"), true));
                break;
            case 108:
                config.setEnabled(!config.isEnabled());
                ConfigHandler.save();
                enableToggleButton.displayString = config.isEnabled() ? I18n.format("dintegrate.gui.button.disable") : I18n.format("dintegrate.gui.button.enable");
                if (config.isEnabled()) {
                    DonateIntegrate.startDonationProvider();
                } else {
                    DonateIntegrate.stopDonationProvider();
                }
                mc.displayGuiScreen(new MessageGui(this, I18n.format(config.isEnabled() ? "dintegrate.gui.message.enabled" : "dintegrate.gui.message.disabled"), true));
                break;
        }
    }

    // ... (Other methods remain unchanged, except for drawScreen)

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, fadeAnimation);
        GuiRenderUtils.drawOverlay(width, height);

        GuiRenderUtils.drawRoundedRect(contentLeft, contentTop, CONTENT_WIDTH, CONTENT_HEIGHT, 8, 0xFF263238);
        fontRenderer.drawString(I18n.format("dintegrate.gui.tab." + getTabKey(currentTab)), contentLeft + 20, contentTop + 20, GuiRenderUtils.getTextColor());

        switch (currentTab) {
            case 0:
                drawSettingsTab(mouseX, mouseY, partialTicks);
                break;
            case 1:
                drawActionsTab(mouseX, mouseY, partialTicks);
                break;
            case 2:
                drawStatusTab(mouseX, mouseY, partialTicks);
                break;
            case 3:
                drawHistoryTab(mouseX, mouseY, partialTicks);
                break;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawSettingsTab(int mouseX, int mouseY, float partialTicks) {
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.token"), contentLeft + 20, contentTop + 50, GuiRenderUtils.getTextColor());
        tokenField.drawTextBox();
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.user_id"), contentLeft + 20, contentTop + 100, GuiRenderUtils.getTextColor());
        userIdField.drawTextBox();
    }

    private void drawActionsTab(int mouseX, int mouseY, float partialTicks) {
        List<Action> actions = ConfigHandler.getConfig().getActions();
        int listTop = contentTop + 60;
        int listLeft = contentLeft + 20;
        int listWidth = CONTENT_WIDTH - 40;
        int maxItems = ACTION_LIST_HEIGHT / ACTION_ITEM_HEIGHT;

        // Draw list background
        GuiRenderUtils.drawRoundedRect(listLeft, listTop, listWidth, ACTION_LIST_HEIGHT, 4, 0xFF37474F);

        // Enable scissor test for clipping
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int scaleFactor = new ScaledResolution(mc).getScaleFactor();
        GL11.glScissor((listLeft * scaleFactor), (height - listTop - ACTION_LIST_HEIGHT) * scaleFactor,
                (listWidth * scaleFactor), (ACTION_LIST_HEIGHT * scaleFactor));

        // Draw action items
        for (int i = 0; i < actions.size(); i++) {
            int y = listTop + i * ACTION_ITEM_HEIGHT - (int) scrollOffset;
            if (y + ACTION_ITEM_HEIGHT < listTop || y > listTop + ACTION_LIST_HEIGHT) continue;

            Action action = actions.get(i);
            int color = i == selectedActionIndex ? GuiRenderUtils.mixColors(0xFF37474F, 0xFF0288D1, 0.5f) : 0xFF37474F;
            GuiRenderUtils.drawRoundedRect(listLeft + 2, y + 2, listWidth - 4, ACTION_ITEM_HEIGHT - 4, 4, color);

            String text = String.format("Sum: %.2f, Commands: %d, %s, Priority: %d",
                    action.getSum(), action.getCommands().size(),
                    action.isEnabled() ? "Enabled" : "Disabled", action.getPriority());
            fontRenderer.drawString(text, listLeft + 8, y + 10, GuiRenderUtils.getTextColor());
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Draw scrollbar
        if (actions.size() > maxItems) {
            int maxScroll = (actions.size() - maxItems) * ACTION_ITEM_HEIGHT;
            float scrollRatio = scrollOffset / (maxScroll > 0 ? maxScroll : 1);
            scrollbarHeight = Math.max(20, ACTION_LIST_HEIGHT * maxItems / actions.size());
            scrollbarTop = listTop + (int) ((ACTION_LIST_HEIGHT - scrollbarHeight) * scrollRatio);
            int scrollbarLeft = contentLeft + CONTENT_WIDTH - 30;
            GuiRenderUtils.drawRoundedRect(scrollbarLeft, scrollbarTop, 10, scrollbarHeight, 4, 0xFF546E7A);
        }
    }

    private void drawStatusTab(int mouseX, int mouseY, float partialTicks) {
        ModConfig config = ConfigHandler.getConfig();
        int y = contentTop + 60;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.enabled") + (config.isEnabled() ? I18n.format("dintegrate.gui.value.yes") : I18n.format("dintegrate.gui.value.no")), contentLeft + 20, y,
                config.isEnabled() ? 0xFF4CAF50 : GuiRenderUtils.getErrorColor());
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.connected") + (DonateIntegrate.getInstance().getDonationProvider().isConnected() ? I18n.format("dintegrate.gui.value.yes") : I18n.format("dintegrate.gui.value.no")),
                contentLeft + 20, y, DonateIntegrate.getInstance().getDonationProvider().isConnected() ? 0xFF4CAF50 : GuiRenderUtils.getErrorColor());
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.last_donation_id") + config.getLastDonate(), contentLeft + 20, y, GuiRenderUtils.getTextColor());
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.token") + (config.getDonpayToken().isEmpty() ? I18n.format("dintegrate.gui.value.not_set") : I18n.format("dintegrate.gui.value.set")), contentLeft + 20, y,
                config.getDonpayToken().isEmpty() ? GuiRenderUtils.getErrorColor() : 0xFF4CAF50);
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.user_id") + (config.getUserId().isEmpty() ? I18n.format("dintegrate.gui.value.not_set") : config.getUserId()), contentLeft + 20, y,
                config.getUserId().isEmpty() ? GuiRenderUtils.getErrorColor() : GuiRenderUtils.getTextColor());
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.actions_configured") + config.getActions().size(), contentLeft + 20, y, GuiRenderUtils.getTextColor());
    }

    private void drawHistoryTab(int mouseX, int mouseY, float partialTicks) {
        int y = contentTop + 60;
        for (int i = 0; i < donationHistory.size() && y < contentTop + CONTENT_HEIGHT - 40; i++) {
            fontRenderer.drawString(donationHistory.get(i), contentLeft + 20, y, GuiRenderUtils.getTextColor());
            y += 20;
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    public Minecraft getMinecraft() {
        return mc;
    }

    /**
     * Custom tab button with vertical layout and animations.
     */
    private static class TabButton extends GuiButton {
        private float hoverAnimation = 0.0f;
        private boolean wasHovered = false;

        public TabButton(int buttonId, int x, int y, int width, int height, String buttonText) {
            super(buttonId, x, y, width, height, buttonText);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;

            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            if (hovered && !wasHovered) hoverAnimation = 0.0f;
            if (!hovered && wasHovered) hoverAnimation = 1.0f;
            hoverAnimation = hovered ? Math.min(1.0f, hoverAnimation + partialTicks * 0.2f) :
                    Math.max(0.0f, hoverAnimation - partialTicks * 0.2f);
            wasHovered = hovered;

            GlStateManager.pushMatrix();
            GuiRenderUtils.drawTabButton(x, y, width, height, hovered, id == ((DonateIntegrateGui) mc.currentScreen).currentTab, hoverAnimation);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y +

                    (height - 8) / 2, GuiRenderUtils.getTextColor());
            GlStateManager.popMatrix();
        }
    }

    /**
     * Custom button with hover and animation effects.
     */
    private static class CustomButton extends GuiButton {
        private float hoverAnimation = 0.0f;
        private boolean wasHovered = false;

        public CustomButton(int buttonId, int x, int y, int width, int height, String buttonText) {
            super(buttonId, x, y, width, height, buttonText);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;

            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            if (hovered && !wasHovered) hoverAnimation = 0.0f;
            if (!hovered && wasHovered) hoverAnimation = 1.0f;
            hoverAnimation = hovered ? Math.min(1.0f, hoverAnimation + partialTicks * 0.2f) :
                    Math.max(0.0f, hoverAnimation - partialTicks * 0.2f);
            wasHovered = hovered;

            GlStateManager.pushMatrix();
            GuiRenderUtils.drawButton(x, y, width, height, hovered, enabled, hoverAnimation);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, GuiRenderUtils.getTextColor());
            GlStateManager.popMatrix();
        }
    }
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        tokenField.mouseClicked(mouseX, mouseY, mouseButton);
        userIdField.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        tokenField.textboxKeyTyped(typedChar, keyCode);
        userIdField.textboxKeyTyped(typedChar, keyCode);
    }
    @Override
    public void updateScreen() {
        super.updateScreen();
        tokenField.updateCursorCounter();
        userIdField.updateCursorCounter();
        if (fadeAnimation < 1.0f) {
            fadeAnimation = Math.min(1.0f, fadeAnimation + 0.05f);
        }
    }
}