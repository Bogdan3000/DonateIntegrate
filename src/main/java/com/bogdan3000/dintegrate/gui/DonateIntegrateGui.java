package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main GUI for DonateIntegrate with a full-screen, side-tabbed minimalist design.
 * Features smooth animations, robust error handling, and a professional aesthetic.
 */
public class DonateIntegrateGui extends GuiScreen {
    private static final int TAB_WIDTH = 60;
    private static final int TAB_HEIGHT = 40;
    private static final int CONTENT_MARGIN = 20;
    private static final int CONTENT_WIDTH = 400;
    private static final int CONTENT_HEIGHT = 300;
    private static final int TAB_COUNT = 4;

    private int contentLeft, contentTop;
    private int currentTab = 0;
    private float fadeAnimation = 0.0f; // For GUI fade-in effect
    private final List<TabButton> tabButtons = new ArrayList<>();
    private final List<String> donationHistory = new CopyOnWriteArrayList<>();

    // Settings Tab
    private GuiTextField tokenField;
    private GuiTextField userIdField;
    private CustomButton saveSettingsButton;
    private CustomButton clearSettingsButton;

    // Actions Tab
    private ActionList actionList;
    private CustomButton addActionButton;
    private CustomButton editActionButton;
    private CustomButton deleteActionButton;

    // Status Tab
    private CustomButton reconnectButton;
    private CustomButton enableToggleButton;

    // History Tab
    private DonationHistoryList historyList;

    @Override
    public void initGui() {
        buttonList.clear();
        tabButtons.clear();
        Keyboard.enableRepeatEvents(true);
        fadeAnimation = 0.0f;

        // Calculate content area
        contentLeft = (width - CONTENT_WIDTH) / 2;
        contentTop = (height - CONTENT_HEIGHT) / 2;

        // Initialize side tabs
        for (int i = 0; i < TAB_COUNT; i++) {
            TabButton tabButton = new TabButton(i, CONTENT_MARGIN, CONTENT_MARGIN + i * (TAB_HEIGHT + 10), TAB_WIDTH, TAB_HEIGHT, getTabName(i));
            buttonList.add(tabButton);
            tabButtons.add(tabButton);
        }

        // Settings Tab
        tokenField = new GuiTextField(100, fontRenderer, contentLeft + 20, contentTop + 60, CONTENT_WIDTH - 40, 20);
        tokenField.setMaxStringLength(100);
        tokenField.setText(ConfigHandler.getConfig().getDonpayToken());
        userIdField = new GuiTextField(101, fontRenderer, contentLeft + 20, contentTop + 110, CONTENT_WIDTH - 40, 20);
        userIdField.setMaxStringLength(20);
        userIdField.setText(ConfigHandler.getConfig().getUserId());
        saveSettingsButton = new CustomButton(102, contentLeft + 20, contentTop + CONTENT_HEIGHT - 60, 100, 24, "Save");
        clearSettingsButton = new CustomButton(103, contentLeft + CONTENT_WIDTH - 120, contentTop + CONTENT_HEIGHT - 60, 100, 24, "Clear");
        buttonList.add(saveSettingsButton);
        buttonList.add(clearSettingsButton);

        // Actions Tab
        actionList = new ActionList(this, contentLeft + 20, contentTop + 60, CONTENT_WIDTH - 40, CONTENT_HEIGHT - 120);
        addActionButton = new CustomButton(104, contentLeft + 20, contentTop + CONTENT_HEIGHT - 60, 80, 24, "Add");
        editActionButton = new CustomButton(105, contentLeft + 110, contentTop + CONTENT_HEIGHT - 60, 80, 24, "Edit");
        deleteActionButton = new CustomButton(106, contentLeft + 200, contentTop + CONTENT_HEIGHT - 60, 80, 24, "Delete");
        buttonList.add(addActionButton);
        buttonList.add(editActionButton);
        buttonList.add(deleteActionButton);

        // Status Tab
        reconnectButton = new CustomButton(107, contentLeft + 20, contentTop + CONTENT_HEIGHT - 60, 100, 24, "Reconnect");
        enableToggleButton = new CustomButton(108, contentLeft + CONTENT_WIDTH - 120, contentTop + CONTENT_HEIGHT - 60, 100, 24,
                ConfigHandler.getConfig().isEnabled() ? "Disable" : "Enable");
        buttonList.add(reconnectButton);
        buttonList.add(enableToggleButton);

        // History Tab
        historyList = new DonationHistoryList(this, contentLeft + 20, contentTop + 60, CONTENT_WIDTH - 40, CONTENT_HEIGHT - 80);
        updateHistoryList();

        updateTabVisibility();
    }

    private String getTabName(int index) {
        switch (index) {
            case 0: return "Settings";
            case 1: return "Actions";
            case 2: return "Status";
            case 3: return "History";
            default: return "";
        }
    }

    private void updateTabVisibility() {
        tokenField.setVisible(currentTab == 0);
        userIdField.setVisible(currentTab == 0);
        saveSettingsButton.visible = currentTab == 0;
        clearSettingsButton.visible = currentTab == 0;

        actionList.setVisible(currentTab == 1);
        addActionButton.visible = currentTab == 1;
        editActionButton.visible = currentTab == 1;
        deleteActionButton.visible = currentTab == 1;

        reconnectButton.visible = currentTab == 2;
        enableToggleButton.visible = currentTab == 2;

        historyList.setVisible(currentTab == 3);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button instanceof TabButton) {
            currentTab = button.id;
            updateTabVisibility();
            return;
        }

        ModConfig config = ConfigHandler.getConfig();
        switch (button.id) {
            case 102: // Save Settings
                String token = tokenField.getText().trim();
                String userId = userIdField.getText().trim();
                if (token.isEmpty() || userId.isEmpty()) {
                    mc.displayGuiScreen(new MessageGui(this, "Token and User ID cannot be empty!", false));
                    return;
                }
                config.setDonpayToken(token);
                config.setUserId(userId);
                ConfigHandler.save();
                DonateIntegrate.startDonationProvider();
                mc.displayGuiScreen(new MessageGui(this, "Settings saved successfully!", true));
                break;
            case 103: // Clear Settings
                tokenField.setText("");
                userIdField.setText("");
                break;
            case 104: // Add Action
                mc.displayGuiScreen(new ActionEditGui(this, null));
                break;
            case 105: // Edit Action
                com.bogdan3000.dintegrate.config.Action selectedAction = actionList.getSelectedAction();
                if (selectedAction == null) {
                    mc.displayGuiScreen(new MessageGui(this, "Please select an action to edit!", false));
                } else {
                    mc.displayGuiScreen(new ActionEditGui(this, selectedAction));
                }
                break;
            case 106: // Delete Action
                selectedAction = actionList.getSelectedAction();
                if (selectedAction == null) {
                    mc.displayGuiScreen(new MessageGui(this, "Please select an action to delete!", false));
                } else {
                    config.getActions().remove(selectedAction);
                    ConfigHandler.save();
                    actionList.refreshList();
                    mc.displayGuiScreen(new MessageGui(this, "Action deleted successfully!", true));
                }
                break;
            case 107: // Reconnect
                DonateIntegrate.startDonationProvider();
                mc.displayGuiScreen(new MessageGui(this, "Reconnection initiated!", true));
                break;
            case 108: // Toggle Enable
                config.setEnabled(!config.isEnabled());
                ConfigHandler.save();
                enableToggleButton.displayString = config.isEnabled() ? "Disable" : "Enable";
                if (config.isEnabled()) {
                    DonateIntegrate.startDonationProvider();
                } else {
                    DonateIntegrate.stopDonationProvider();
                }
                mc.displayGuiScreen(new MessageGui(this, "DonateIntegrate " + (config.isEnabled() ? "enabled" : "disabled") + "!", true));
                break;
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (currentTab == 0) {
            tokenField.mouseClicked(mouseX, mouseY, mouseButton);
            userIdField.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (currentTab == 1) {
            actionList.mouseClicked(mouseX, mouseY, mouseButton);
        } else if (currentTab == 3) {
            historyList.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            mc.displayGuiScreen(null);
            return;
        }
        if (currentTab == 0) {
            tokenField.textboxKeyTyped(typedChar, keyCode);
            userIdField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (fadeAnimation < 1.0f) {
            fadeAnimation = Math.min(1.0f, fadeAnimation + 0.05f);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Draw full-screen overlay with fade-in
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, fadeAnimation);
        GuiRenderUtils.drawOverlay(width, height);

        // Draw content area
        GuiRenderUtils.drawRoundedRect(contentLeft, contentTop, CONTENT_WIDTH, CONTENT_HEIGHT, 8, 0xFF263238);
        fontRenderer.drawString(getTabName(currentTab), contentLeft + 20, contentTop + 20, GuiRenderUtils.getTextColor());

        // Draw tab-specific content
        switch (currentTab) {
            case 0: // Settings
                drawSettingsTab(mouseX, mouseY, partialTicks);
                break;
            case 1: // Actions
                drawActionsTab(mouseX, mouseY, partialTicks);
                break;
            case 2: // Status
                drawStatusTab(mouseX, mouseY, partialTicks);
                break;
            case 3: // History
                drawHistoryTab(mouseX, mouseY, partialTicks);
                break;
        }

        // Draw all buttons (including tabs)
        super.drawScreen(mouseX, mouseY, partialTicks);
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawSettingsTab(int mouseX, int mouseY, float partialTicks) {
        fontRenderer.drawString("DonatePay Token:", contentLeft + 20, contentTop + 50, GuiRenderUtils.getTextColor());
        tokenField.drawTextBox();
        fontRenderer.drawString("User ID:", contentLeft + 20, contentTop + 100, GuiRenderUtils.getTextColor());
        userIdField.drawTextBox();
    }

    private void drawActionsTab(int mouseX, int mouseY, float partialTicks) {
        actionList.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawStatusTab(int mouseX, int mouseY, float partialTicks) {
        ModConfig config = ConfigHandler.getConfig();
        int y = contentTop + 60;
        fontRenderer.drawString("Enabled: " + (config.isEnabled() ? "Yes" : "No"), contentLeft + 20, y,
                config.isEnabled() ? 0xFF4CAF50 : GuiRenderUtils.getErrorColor());
        y += 20;
        fontRenderer.drawString("Connected: " + (DonateIntegrate.getInstance().getDonationProvider().isConnected() ? "Yes" : "No"),
                contentLeft + 20, y, DonateIntegrate.getInstance().getDonationProvider().isConnected() ? 0xFF4CAF50 : GuiRenderUtils.getErrorColor());
        y += 20;
        fontRenderer.drawString("Last Donation ID: " + config.getLastDonate(), contentLeft + 20, y, GuiRenderUtils.getTextColor());
        y += 20;
        fontRenderer.drawString("Token: " + (config.getDonpayToken().isEmpty() ? "Not Set" : "Set"), contentLeft + 20, y,
                config.getDonpayToken().isEmpty() ? GuiRenderUtils.getErrorColor() : 0xFF4CAF50);
        y += 20;
        fontRenderer.drawString("User ID: " + (config.getUserId().isEmpty() ? "Not Set" : config.getUserId()), contentLeft + 20, y,
                config.getUserId().isEmpty() ? GuiRenderUtils.getErrorColor() : GuiRenderUtils.getTextColor());
        y += 20;
        fontRenderer.drawString("Actions Configured: " + config.getActions().size(), contentLeft + 20, y, GuiRenderUtils.getTextColor());
    }

    private void drawHistoryTab(int mouseX, int mouseY, float partialTicks) {
        historyList.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    public void addDonationToHistory(String donationInfo) {
        if (donationInfo == null) return;
        donationHistory.add(0, donationInfo);
        if (donationHistory.size() > 100) {
            donationHistory.remove(donationHistory.size() - 1);
        }
        updateHistoryList();
    }

    private void updateHistoryList() {
        historyList.setEntries(donationHistory);
    }

    public Minecraft getMinecraft() {
        return mc;
    }

    public void refreshActionList() {
        actionList.refreshList();
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
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, GuiRenderUtils.getTextColor());
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
}