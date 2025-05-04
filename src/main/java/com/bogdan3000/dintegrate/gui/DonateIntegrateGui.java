package com.bogdan3000.dintegrate.gui;

import com.bogdan3000.dintegrate.DonateIntegrate;
import com.bogdan3000.dintegrate.config.ConfigHandler;
import com.bogdan3000.dintegrate.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Main GUI for the DonateIntegrate mod, providing tabs for settings, actions, status, and donation history.
 */
public class DonateIntegrateGui extends GuiScreen {
    private static final int TAB_COUNT = 4;
    private static final int TAB_WIDTH = 60;
    private static final int TAB_HEIGHT = 20;
    private static final int GUI_WIDTH = 256;
    private static final int GUI_HEIGHT = 180;
    private int guiLeft, guiTop;
    private int currentTab = 0;

    // Settings Tab
    private GuiTextField tokenField;
    private GuiTextField userIdField;
    private GuiButton saveSettingsButton;
    private GuiButton clearSettingsButton;

    // Actions Tab
    private ActionList actionList;
    private GuiButton addActionButton;
    private GuiButton editActionButton;
    private GuiButton deleteActionButton;

    // Status Tab
    private GuiButton reconnectButton;
    private GuiButton enableToggleButton;

    // History Tab
    private DonationHistoryList historyList;
    private final List<String> donationHistory = new CopyOnWriteArrayList<>();

    @Override
    public void initGui() {
        guiLeft = (width - GUI_WIDTH) / 2;
        guiTop = (height - GUI_HEIGHT) / 2;
        buttonList.clear();
        Keyboard.enableRepeatEvents(true);

        // Initialize Tabs
        for (int i = 0; i < TAB_COUNT; i++) {
            buttonList.add(new GuiButton(i, guiLeft + i * TAB_WIDTH, guiTop - TAB_HEIGHT, TAB_WIDTH, TAB_HEIGHT, getTabName(i)));
        }

        // Initialize Settings Tab
        tokenField = new GuiTextField(100, fontRenderer, guiLeft + 10, guiTop + 30, GUI_WIDTH - 20, 20);
        tokenField.setMaxStringLength(100);
        tokenField.setText(ConfigHandler.getConfig().getDonpayToken());
        userIdField = new GuiTextField(101, fontRenderer, guiLeft + 10, guiTop + 70, GUI_WIDTH - 20, 20);
        userIdField.setMaxStringLength(20);
        userIdField.setText(ConfigHandler.getConfig().getUserId());
        saveSettingsButton = new GuiButton(102, guiLeft + 10, guiTop + 110, 100, 20, "Save");
        clearSettingsButton = new GuiButton(103, guiLeft + GUI_WIDTH - 110, guiTop + 110, 100, 20, "Clear");
        buttonList.add(saveSettingsButton);
        buttonList.add(clearSettingsButton);

        // Initialize Actions Tab
        actionList = new ActionList(this, guiLeft + 10, guiTop + 30, GUI_WIDTH - 20, 100);
        addActionButton = new GuiButton(104, guiLeft + 10, guiTop + 140, 80, 20, "Add Action");
        editActionButton = new GuiButton(105, guiLeft + 98, guiTop + 140, 80, 20, "Edit Action");
        deleteActionButton = new GuiButton(106, guiLeft + 186, guiTop + 140, 80, 20, "Delete Action");
        buttonList.add(addActionButton);
        buttonList.add(editActionButton);
        buttonList.add(deleteActionButton);

        // Initialize Status Tab
        reconnectButton = new GuiButton(107, guiLeft + 10, guiTop + 140, 100, 20, "Reconnect");
        enableToggleButton = new GuiButton(108, guiLeft + GUI_WIDTH - 110, guiTop + 140, 100, 20,
                ConfigHandler.getConfig().isEnabled() ? "Disable" : "Enable");
        buttonList.add(reconnectButton);
        buttonList.add(enableToggleButton);

        // Initialize History Tab
        historyList = new DonationHistoryList(this, guiLeft + 10, guiTop + 30, GUI_WIDTH - 20, 120);
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

        actionList.visible = (currentTab == 1);
        addActionButton.visible = currentTab == 1;
        editActionButton.visible = currentTab == 1;
        deleteActionButton.visible = currentTab == 1;

        reconnectButton.visible = currentTab == 2;
        enableToggleButton.visible = currentTab == 2;

        historyList.visible = (currentTab == 3);
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id < TAB_COUNT) {
            currentTab = button.id;
            updateTabVisibility();
        } else {
            ModConfig config = ConfigHandler.getConfig();
            switch (button.id) {
                case 102: // Save Settings
                    config.setDonpayToken(tokenField.getText().trim());
                    config.setUserId(userIdField.getText().trim());
                    ConfigHandler.save();
                    DonateIntegrate.startDonationProvider();
                    mc.displayGuiScreen(new MessageGui(this, "Settings saved!", true));
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
                    if (selectedAction != null) {
                        mc.displayGuiScreen(new ActionEditGui(this, selectedAction));
                    } else {
                        mc.displayGuiScreen(new MessageGui(this, "Select an action to edit!", false));
                    }
                    break;
                case 106: // Delete Action
                    selectedAction = actionList.getSelectedAction();
                    if (selectedAction != null) {
                        config.getActions().remove(selectedAction);
                        ConfigHandler.save();
                        actionList.refreshList();
                        mc.displayGuiScreen(new MessageGui(this, "Action deleted!", true));
                    } else {
                        mc.displayGuiScreen(new MessageGui(this, "Select an action to delete!", false));
                    }
                    break;
                case 107: // Reconnect
                    DonateIntegrate.startDonationProvider();
                    mc.displayGuiScreen(new MessageGui(this, "Reconnection initiated!", true));
                    break;
                case 108: // Toggle Enable
                    config.setEnabled(!config.isEnabled());
                    enableToggleButton.displayString = config.isEnabled() ? "Disable" : "Enable";
                    ConfigHandler.save();
                    if (config.isEnabled()) {
                        DonateIntegrate.startDonationProvider();
                    } else {
                        DonateIntegrate.stopDonationProvider();
                    }
                    mc.displayGuiScreen(new MessageGui(this, "DonateIntegrate " + (config.isEnabled() ? "enabled" : "disabled") + "!", true));
                    break;
            }
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        // Draw full black background
        drawDefaultBackground(); // Удаляем, чтобы избежать текстуры земли
        drawRect(guiLeft, guiTop, guiLeft + GUI_WIDTH, guiTop + GUI_HEIGHT, 0xFF000000);

        // Draw Tabs with hover effect
        for (GuiButton button : buttonList) {
            if (button.id < TAB_COUNT) {
                boolean isHovered = mouseX >= button.x && mouseX < button.x + button.width &&
                        mouseY >= button.y && mouseY < button.y + button.height;
                drawRect(button.x, button.y, button.x + button.width, button.y + button.height,
                        isHovered ? 0xFFAAAAAA : 0xFF777777);
                button.drawButton(mc, mouseX, mouseY, partialTicks);
                if (button.id == currentTab) {
                    drawRect(button.x, button.y + button.height - 2, button.x + button.width, button.y + button.height, 0xFFFFFFFF);
                }
            }
        }

        // Draw Tab Content
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

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSettingsTab(int mouseX, int mouseY, float partialTicks) {
        drawString(fontRenderer, "DonatePay Token:", guiLeft + 10, guiTop + 20, 0xFFFFFF);
        tokenField.drawTextBox();
        drawString(fontRenderer, "User ID:", guiLeft + 10, guiTop + 60, 0xFFFFFF);
        userIdField.drawTextBox();
    }

    private void drawActionsTab(int mouseX, int mouseY, float partialTicks) {
        if (actionList.visible) {
            actionList.drawScreen(mouseX, mouseY, partialTicks);
            addActionButton.drawButton(mc, mouseX, mouseY, partialTicks);
            editActionButton.drawButton(mc, mouseX, mouseY, partialTicks);
            deleteActionButton.drawButton(mc, mouseX, mouseY, partialTicks);
        }
    }

    private void drawStatusTab(int mouseX, int mouseY, float partialTicks) {
        ModConfig config = ConfigHandler.getConfig();
        int y = guiTop + 20;
        drawString(fontRenderer, "Enabled: " + (config.isEnabled() ? TextFormatting.GREEN + "Yes" : TextFormatting.RED + "No"), guiLeft + 10, y, 0xFFFFFF);
        y += 15;
        drawString(fontRenderer, "Connected: " + (DonateIntegrate.getInstance().getDonationProvider().isConnected() ? TextFormatting.GREEN + "Yes" : TextFormatting.RED + "No"), guiLeft + 10, y, 0xFFFFFF);
        y += 15;
        drawString(fontRenderer, "Last Donation ID: " + config.getLastDonate(), guiLeft + 10, y, 0xFFFFFF);
        y += 15;
        drawString(fontRenderer, "Token: " + (config.getDonpayToken().isEmpty() ? TextFormatting.RED + "Not Set" : TextFormatting.GREEN + "Set"), guiLeft + 10, y, 0xFFFFFF);
        y += 15;
        drawString(fontRenderer, "User ID: " + (config.getUserId().isEmpty() ? TextFormatting.RED + "Not Set" : config.getUserId()), guiLeft + 10, y, 0xFFFFFF);
        y += 15;
        drawString(fontRenderer, "Actions Configured: " + config.getActions().size(), guiLeft + 10, y, 0xFFFFFF);
        reconnectButton.drawButton(mc, mouseX, mouseY, partialTicks);
        enableToggleButton.drawButton(mc, mouseX, mouseY, partialTicks);
    }

    private void drawHistoryTab(int mouseX, int mouseY, float partialTicks) {
        if (historyList.visible) {
            historyList.drawScreen(mouseX, mouseY, partialTicks);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    /**
     * Adds a donation entry to the history and updates the history list.
     *
     * @param donationInfo The donation information to add.
     */
    public void addDonationToHistory(String donationInfo) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            donationHistory.add(0, donationInfo);
            if (donationHistory.size() > 100) {
                donationHistory.remove(donationHistory.size() - 1);
            }
            updateHistoryList();
        });
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
}