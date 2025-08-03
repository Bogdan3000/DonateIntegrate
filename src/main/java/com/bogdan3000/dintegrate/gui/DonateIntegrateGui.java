package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.config.Action;
import com.bogdan3000.dintegrate.config.ConfigHandler;
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

public class DonateIntegrateGui extends GuiScreen {
    private static final int TAB_WIDTH = 100;
    private static final int TAB_HEIGHT = 30;
    private static final int TAB_SPACING = 4;
    private static final int CONTENT_MARGIN = 10;
    private static final int ACTION_ITEM_HEIGHT = 50;
    private int windowLeft, windowTop, windowWidth, windowHeight;
    private int currentTab = 1; // Default to Actions tab
    private float fadeAnimation = 0.0f;
    private final List<TabButton> tabButtons = new ArrayList<>();
    private ObfuscatedTextField tokenField;
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

    @Override
    public void initGui() {
        buttonList.clear();
        tabButtons.clear();
        Keyboard.enableRepeatEvents(true);
        fadeAnimation = 0.0f;
        selectedActionIndex = -1;
        scrollOffset = 0.0f;

        // Calculate window dimensions
        ScaledResolution scaled = new ScaledResolution(mc);
        windowWidth = (int) (width * 0.75);
        windowHeight = height - 40;
        windowLeft = TAB_WIDTH + CONTENT_MARGIN;
        windowTop = 20;

        // Initialize tabs (vertical with spacing)
        String[] tabNames = {"settings", "actions", "status", "history"};
        for (int i = 0; i < tabNames.length; i++) {
            TabButton tabButton = new TabButton(i, CONTENT_MARGIN, 20 + i * (TAB_HEIGHT + TAB_SPACING), TAB_WIDTH, TAB_HEIGHT, I18n.format("dintegrate.gui.tab." + tabNames[i]));
            buttonList.add(tabButton);
            tabButtons.add(tabButton);
        }

        // Settings tab fields
        tokenField = new ObfuscatedTextField(100, fontRenderer, windowLeft + CONTENT_MARGIN, windowTop + 50, windowWidth - 2 * CONTENT_MARGIN, 20);
        tokenField.setMaxStringLength(100);
        tokenField.setText(ConfigHandler.getConfig().getDonpayToken());
        userIdField = new GuiTextField(101, fontRenderer, windowLeft + CONTENT_MARGIN, windowTop + 100, windowWidth - 2 * CONTENT_MARGIN, 20);
        userIdField.setMaxStringLength(20);
        userIdField.setText(ConfigHandler.getConfig().getUserId());
        saveSettingsButton = new CustomButton(102, windowLeft + CONTENT_MARGIN, windowTop + windowHeight - 40, 100, 24, I18n.format("dintegrate.gui.button.save"));
        clearSettingsButton = new CustomButton(103, windowLeft + windowWidth - CONTENT_MARGIN - 100, windowTop + windowHeight - 40, 100, 24, I18n.format("dintegrate.gui.button.clear"));
        buttonList.add(saveSettingsButton);
        buttonList.add(clearSettingsButton);

        // Actions tab buttons
        addActionButton = new CustomButton(104, windowLeft + CONTENT_MARGIN, windowTop + windowHeight - 40, 80, 24, I18n.format("dintegrate.gui.button.add"));
        editActionButton = new CustomButton(105, windowLeft + CONTENT_MARGIN + 90, windowTop + windowHeight - 40, 80, 24, I18n.format("dintegrate.gui.button.edit"));
        editActionButton.enabled = false;
        deleteActionButton = new CustomButton(106, windowLeft + CONTENT_MARGIN + 180, windowTop + windowHeight - 40, 80, 24, I18n.format("dintegrate.gui.button.delete"));
        deleteActionButton.enabled = false;
        buttonList.add(addActionButton);
        buttonList.add(editActionButton);
        buttonList.add(deleteActionButton);

        // Status tab buttons
        reconnectButton = new CustomButton(107, windowLeft + CONTENT_MARGIN, windowTop + windowHeight - 40, 100, 24, I18n.format("dintegrate.gui.button.reconnect"));
        enableToggleButton = new CustomButton(108, windowLeft + windowWidth - CONTENT_MARGIN - 100, windowTop + windowHeight - 40, 100, 24,
                ConfigHandler.getConfig().isEnabled() ? I18n.format("dintegrate.gui.button.disable") : I18n.format("dintegrate.gui.button.enable"));
        buttonList.add(reconnectButton);
        buttonList.add(enableToggleButton);

        updateTabVisibility();
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

        switch (button.id) {
            case 102: // Save settings
                String token = tokenField.getText().trim();
                String userId = userIdField.getText().trim();
                if (token.isEmpty() || userId.isEmpty()) {
                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.empty_credentials"), false));
                    return;
                }
                ConfigHandler.getConfig().setDonpayToken(token);
                ConfigHandler.getConfig().setUserId(userId);
                ConfigHandler.save();
                DonateIntegrate.startDonationProvider();
                mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.settings_saved"), true));
                break;
            case 103: // Clear settings
                tokenField.setText("");
                userIdField.setText("");
                break;
            case 104: // Add action
                mc.displayGuiScreen(new ActionEditGui(this, null));
                break;
            case 105: // Edit action
                if (selectedActionIndex >= 0 && selectedActionIndex < ConfigHandler.getConfig().getActions().size()) {
                    Action action = ConfigHandler.getConfig().getActions().get(selectedActionIndex);
                    mc.displayGuiScreen(new ActionEditGui(this, action));
                }
                break;
            case 106: // Delete action
                if (selectedActionIndex >= 0 && selectedActionIndex < ConfigHandler.getConfig().getActions().size()) {
                    ConfigHandler.getConfig().getActions().remove(selectedActionIndex);
                    ConfigHandler.save();
                    selectedActionIndex = -1;
                    editActionButton.enabled = false;
                    deleteActionButton.enabled = false;
                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.action_deleted"), true));
                }
                break;
            case 107: // Reconnect
                DonateIntegrate.startDonationProvider();
                mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.reconnection_initiated"), true));
                break;
            case 108: // Enable/Disable
                ConfigHandler.getConfig().setEnabled(!ConfigHandler.getConfig().isEnabled());
                ConfigHandler.save();
                enableToggleButton.displayString = ConfigHandler.getConfig().isEnabled() ? I18n.format("dintegrate.gui.button.disable") : I18n.format("dintegrate.gui.button.enable");
                if (ConfigHandler.getConfig().isEnabled()) {
                    DonateIntegrate.startDonationProvider();
                } else {
                    DonateIntegrate.stopDonationProvider();
                }
                mc.displayGuiScreen(new MessageGui(this, I18n.format(ConfigHandler.getConfig().isEnabled() ? "dintegrate.gui.message.enabled" : "dintegrate.gui.message.disabled"), true));
                break;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, fadeAnimation);
        GuiRenderUtils.drawOverlay(width, height);
        GuiRenderUtils.drawRoundedRect(windowLeft, windowTop, windowWidth, windowHeight, 8, 0xFF263238);

        // Draw content area
        switch (currentTab) {
            case 0: // Settings
                drawSettingsTab();
                break;
            case 1: // Actions
                drawActionsTab(mouseX, mouseY, partialTicks);
                break;
            case 2: // Status
                drawStatusTab();
                break;
            case 3: // History
                drawHistoryTab();
                break;
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawSettingsTab() {
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.token"), windowLeft + CONTENT_MARGIN, windowTop + 40, GuiRenderUtils.getTextColor());
        tokenField.drawTextBox();
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.user_id"), windowLeft + CONTENT_MARGIN, windowTop + 90, GuiRenderUtils.getTextColor());
        userIdField.drawTextBox();
    }

    private void drawActionsTab(int mouseX, int mouseY, float partialTicks) {
        List<Action> actions = ConfigHandler.getConfig().getActions();
        int listTop = windowTop + CONTENT_MARGIN;
        int listLeft = windowLeft + CONTENT_MARGIN;
        int listWidth = windowWidth - 2 * CONTENT_MARGIN;
        int listHeight = windowHeight - 60;

        // Подсчёт полной высоты
        int totalHeight = 0;
        for (Action a : actions) {
            totalHeight += 28 + a.getCommands().size() * 22;
        }

        GuiRenderUtils.drawRoundedRect(listLeft, listTop, listWidth, listHeight, 4, 0xFF37474F);

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int scaleFactor = new ScaledResolution(mc).getScaleFactor();
        GL11.glScissor(listLeft * scaleFactor, (height - listTop - listHeight) * scaleFactor, listWidth * scaleFactor, listHeight * scaleFactor);

        int yPos = listTop;
        for (int i = 0; i < actions.size(); i++) {
            Action action = actions.get(i);
            int commandCount = action.getCommands().size();
            int itemHeight = 28 + commandCount * 22;
            int y = yPos - (int) scrollOffset;

            if (y + itemHeight < listTop || y > listTop + listHeight) {
                yPos += itemHeight;
                continue;
            }

            boolean hovered = mouseX >= listLeft && mouseX <= listLeft + listWidth && mouseY >= y && mouseY <= y + itemHeight;
            boolean selected = i == selectedActionIndex;
            int baseColor = 0xFF37474F;
            int backgroundColor = selected ? 0xFF0288D1 : (hovered ? 0xFF546E7A : baseColor);
            int commandBackgroundColor = selected ? 0xFF0288D1 : (hovered ? 0xFF78909C : 0xFF455A64);

            GuiRenderUtils.drawRoundedRect(listLeft + 1, y + 1, listWidth - 2, itemHeight - 2, 4, 0xFF263238);
            GuiRenderUtils.drawRoundedRect(listLeft + 2, y + 2, listWidth - 4, itemHeight - 4, 4, backgroundColor);

            String text = String.format("%s: %.2f, %s: %d, %s, %s: %d",
                    I18n.format("dintegrate.gui.label.sum"), action.getSum(),
                    I18n.format("dintegrate.gui.label.commands"), commandCount,
                    action.isEnabled() ? I18n.format("dintegrate.gui.value.enabled") : I18n.format("dintegrate.gui.value.disabled"),
                    I18n.format("dintegrate.gui.label.priority"), action.getPriority());
            fontRenderer.drawString(text, listLeft + 8, y + 8, GuiRenderUtils.getTextColor());

            int commandY = y + 28;
            for (String command : action.getCommands()) {
                String cmd = command.length() > 50 ? command.substring(0, 47) + "..." : command;
                GuiRenderUtils.drawRoundedRect(listLeft + 8, commandY, listWidth - 16, 20, 2, commandBackgroundColor);
                GuiRenderUtils.drawRoundedRect(listLeft + 8, commandY, listWidth - 16, 20, 2, 0xFF78909C);
                fontRenderer.drawString(cmd, listLeft + 12, commandY + 4, GuiRenderUtils.getTextColor());
                commandY += 22;
            }

            yPos += itemHeight;
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Scrollbar
        if (totalHeight > listHeight) {
            int maxScroll = totalHeight - listHeight;
            float scrollRatio = scrollOffset / (float) maxScroll;
            int scrollbarHeight = Math.max(20, listHeight * listHeight / totalHeight);
            int scrollbarTop = listTop + (int) ((listHeight - scrollbarHeight) * scrollRatio);
            GuiRenderUtils.drawRoundedRect(windowLeft + windowWidth - CONTENT_MARGIN - 10, scrollbarTop, 10, scrollbarHeight, 4, 0xFF546E7A);
        }
    }

    private void drawStatusTab() {
        int y = windowTop + CONTENT_MARGIN;
        int x = windowLeft + CONTENT_MARGIN;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.enabled") + ": " +
                        (ConfigHandler.getConfig().isEnabled() ? I18n.format("dintegrate.gui.value.yes") : I18n.format("dintegrate.gui.value.no")),
                x, y, ConfigHandler.getConfig().isEnabled() ? 0xFF4CAF50 : GuiRenderUtils.getErrorColor());
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.connected") + ": " +
                        (DonateIntegrate.getInstance().getDonationProvider().isConnected() ? I18n.format("dintegrate.gui.value.yes") : I18n.format("dintegrate.gui.value.no")),
                x, y, DonateIntegrate.getInstance().getDonationProvider().isConnected() ? 0xFF4CAF50 : GuiRenderUtils.getErrorColor());
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.last_donation_id") + ": " + ConfigHandler.getConfig().getLastDonate(),
                x, y, GuiRenderUtils.getTextColor());
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.token") + ": " +
                        (ConfigHandler.getConfig().getDonpayToken().isEmpty() ? I18n.format("dintegrate.gui.value.not_set") : I18n.format("dintegrate.gui.value.set")),
                x, y, ConfigHandler.getConfig().getDonpayToken().isEmpty() ? GuiRenderUtils.getErrorColor() : 0xFF4CAF50);
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.user_id") + ": " +
                        (ConfigHandler.getConfig().getUserId().isEmpty() ? I18n.format("dintegrate.gui.value.not_set") : ConfigHandler.getConfig().getUserId()),
                x, y, ConfigHandler.getConfig().getUserId().isEmpty() ? GuiRenderUtils.getErrorColor() : GuiRenderUtils.getTextColor());
        y += 20;
        fontRenderer.drawString(I18n.format("dintegrate.gui.label.actions_configured") + ": " + ConfigHandler.getConfig().getActions().size(),
                x, y, GuiRenderUtils.getTextColor());
    }

    private void drawHistoryTab() {
        fontRenderer.drawString("History not implemented yet", windowLeft + CONTENT_MARGIN, windowTop + CONTENT_MARGIN, GuiRenderUtils.getTextColor());
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        tokenField.mouseClicked(mouseX, mouseY, mouseButton);
        userIdField.mouseClicked(mouseX, mouseY, mouseButton);

        if (currentTab == 1 && mouseButton == 0) {
            int listTop = windowTop + CONTENT_MARGIN;
            int listLeft = windowLeft + CONTENT_MARGIN;
            int listWidth = windowWidth - 2 * CONTENT_MARGIN;
            int listHeight = windowHeight - 60;
            if (mouseX >= listLeft && mouseX <= listLeft + listWidth &&
                    mouseY >= listTop && mouseY <= listTop + listHeight) {
                List<Action> actions = ConfigHandler.getConfig().getActions();
                int relativeY = mouseY - listTop + (int) scrollOffset;
                int currentY = 0;
                selectedActionIndex = -1;
                editActionButton.enabled = false;
                deleteActionButton.enabled = false;

                for (int i = 0; i < actions.size(); i++) {
                    int actionHeight = 28 + actions.get(i).getCommands().size() * 22;
                    if (relativeY >= currentY && relativeY < currentY + actionHeight) {
                        selectedActionIndex = i;
                        editActionButton.enabled = true;
                        deleteActionButton.enabled = true;
                        break;
                    }
                    currentY += actionHeight;
                }
            } else {
                selectedActionIndex = -1;
                editActionButton.enabled = false;
                deleteActionButton.enabled = false;
            }
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        tokenField.textboxKeyTyped(typedChar, keyCode);
        userIdField.textboxKeyTyped(typedChar, keyCode);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        if (currentTab == 1 && ConfigHandler.getConfig().getActions().size() > (windowHeight - 60) / ACTION_ITEM_HEIGHT) {
            int listTop = windowTop + CONTENT_MARGIN;
            int listLeft = windowLeft + CONTENT_MARGIN;
            int listWidth = windowWidth - 2 * CONTENT_MARGIN;
            int listHeight = windowHeight - 60;
            int maxItems = listHeight / ACTION_ITEM_HEIGHT;

            int wheel = Mouse.getEventDWheel();
            if (wheel != 0 && mouseX >= listLeft && mouseX <= listLeft + listWidth &&
                    mouseY >= listTop && mouseY <= listTop + listHeight) {
                int maxScroll = (ConfigHandler.getConfig().getActions().size() - maxItems) * ACTION_ITEM_HEIGHT;
                scrollOffset -= wheel > 0 ? 20 : -20;
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            }

            if (Mouse.isButtonDown(0)) {
                int scrollbarLeft = windowLeft + windowWidth - CONTENT_MARGIN - 10;
                int scrollbarTop = listTop + (int) ((listHeight - Math.max(20, listHeight * maxItems / ConfigHandler.getConfig().getActions().size())) * (scrollOffset / ((ConfigHandler.getConfig().getActions().size() - maxItems) * ACTION_ITEM_HEIGHT)));
                if (!isDraggingScrollbar && mouseX >= scrollbarLeft && mouseX <= scrollbarLeft + 10 &&
                        mouseY >= scrollbarTop && mouseY <= scrollbarTop + Math.max(20, listHeight * maxItems / ConfigHandler.getConfig().getActions().size())) {
                    isDraggingScrollbar = true;
                }
            } else {
                isDraggingScrollbar = false;
            }

            if (isDraggingScrollbar) {
                int maxScroll = (ConfigHandler.getConfig().getActions().size() - maxItems) * ACTION_ITEM_HEIGHT;
                float scrollRatio = (mouseY - listTop - Math.max(20, listHeight * maxItems / ConfigHandler.getConfig().getActions().size()) / 2.0f) / (listHeight - Math.max(20, listHeight * maxItems / ConfigHandler.getConfig().getActions().size()));
                scrollOffset = scrollRatio * maxScroll;
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            }
        }
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

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

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
            hoverAnimation = hovered ? Math.min(1.0f, hoverAnimation + partialTicks * 0.2f) :
                    Math.max(0.0f, hoverAnimation - partialTicks * 0.2f);
            wasHovered = hovered;

            GuiRenderUtils.drawTabButton(x, y, width, height, hovered, id == ((DonateIntegrateGui) mc.currentScreen).currentTab, hoverAnimation);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, GuiRenderUtils.getTextColor());
        }
    }

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
            hoverAnimation = hovered ? Math.min(1.0f, hoverAnimation + partialTicks * 0.2f) :
                    Math.max(0.0f, hoverAnimation - partialTicks * 0.2f);
            wasHovered = hovered;

            GuiRenderUtils.drawButton(x, y, width, height, hovered, enabled, hoverAnimation);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, GuiRenderUtils.getTextColor());
        }
    }
}