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
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

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
    private static final int ACTION_ITEM_HEIGHT = 30;
    private static final int ACTION_LIST_HEIGHT = 180;

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
    private CustomButton addActionButton;
    private CustomButton editActionButton;
    private CustomButton deleteActionButton;
    private int selectedActionIndex = -1;
    private float scrollOffset = 0.0f;
    private boolean isDraggingScrollbar = false;
    private int scrollbarHeight = 20;
    private int scrollbarTop;

    // Status Tab
    private CustomButton reconnectButton;
    private CustomButton enableToggleButton;

    @Override
    public void initGui() {
        buttonList.clear();
        tabButtons.clear();
        Keyboard.enableRepeatEvents(true);
        fadeAnimation = 0.0f;
        selectedActionIndex = -1;
        scrollOffset = 0.0f;

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
        addActionButton = new CustomButton(104, contentLeft + 20, contentTop + CONTENT_HEIGHT - 60, 80, 24, "Add");
        editActionButton = new CustomButton(105, contentLeft + 110, contentTop + CONTENT_HEIGHT - 60, 80, 24, "Edit");
        editActionButton.enabled = false; // Disabled until an action is selected
        deleteActionButton = new CustomButton(106, contentLeft + 200, contentTop + CONTENT_HEIGHT - 60, 80, 24, "Delete");
        deleteActionButton.enabled = false; // Disabled until an action is selected
        buttonList.add(addActionButton);
        buttonList.add(editActionButton);
        buttonList.add(deleteActionButton);

        // Status Tab
        reconnectButton = new CustomButton(107, contentLeft + 20, contentTop + CONTENT_HEIGHT - 60, 100, 24, "Reconnect");
        enableToggleButton = new CustomButton(108, contentLeft + CONTENT_WIDTH - 120, contentTop + CONTENT_HEIGHT - 60, 100, 24,
                ConfigHandler.getConfig().isEnabled() ? "Disable" : "Enable");
        buttonList.add(reconnectButton);
        buttonList.add(enableToggleButton);

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
                if (selectedActionIndex >= 0 && selectedActionIndex < config.getActions().size()) {
                    Action action = config.getActions().get(selectedActionIndex);
                    mc.displayGuiScreen(new ActionEditGui(this, action));
                }
                break;
            case 106: // Delete Action
                if (selectedActionIndex >= 0 && selectedActionIndex < config.getActions().size()) {
                    config.getActions().remove(selectedActionIndex);
                    ConfigHandler.save();
                    selectedActionIndex = -1;
                    editActionButton.enabled = false;
                    deleteActionButton.enabled = false;
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
            handleActionListClick(mouseX, mouseY, mouseButton);
            handleScrollbarClick(mouseX, mouseY, mouseButton);
        }
    }

    private void handleActionListClick(int mouseX, int mouseY, int mouseButton) {
        if (mouseButton != 0) return; // Only handle left clicks
        int listTop = contentTop + 60;
        int listBottom = listTop + ACTION_LIST_HEIGHT;
        int listLeft = contentLeft + 20;
        int listRight = contentLeft + CONTENT_WIDTH - 40;

        if (mouseX >= listLeft && mouseX <= listRight && mouseY >= listTop && mouseY <= listBottom) {
            List<Action> actions = ConfigHandler.getConfig().getActions();
            int index = (int) ((mouseY - listTop + scrollOffset) / ACTION_ITEM_HEIGHT);
            if (index >= 0 && index < actions.size()) {
                selectedActionIndex = index;
                editActionButton.enabled = true;
                deleteActionButton.enabled = true;
            } else {
                selectedActionIndex = -1;
                editActionButton.enabled = false;
                deleteActionButton.enabled = false;
            }
        }
    }

    private void handleScrollbarClick(int mouseX, int mouseY, int mouseButton) {
        int scrollbarLeft = contentLeft + CONTENT_WIDTH - 30;
        int scrollbarRight = scrollbarLeft + 10;
        if (mouseX >= scrollbarLeft && mouseX <= scrollbarRight && mouseY >= scrollbarTop && mouseY <= scrollbarTop + scrollbarHeight) {
            isDraggingScrollbar = true;
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        isDraggingScrollbar = false;
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        if (currentTab != 1) return;

        int mouseWheel = Mouse.getEventDWheel();
        if (mouseWheel != 0) {
            List<Action> actions = ConfigHandler.getConfig().getActions();
            int maxItems = ACTION_LIST_HEIGHT / ACTION_ITEM_HEIGHT;
            int maxScroll = Math.max(0, actions.size() - maxItems) * ACTION_ITEM_HEIGHT;
            scrollOffset -= mouseWheel > 0 ? 30 : -30;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        }

        if (isDraggingScrollbar) {
            int mouseY = Mouse.getY();
            int screenHeight = mc.displayHeight;
            int guiHeight = height;
            float mouseYGui = (float) (screenHeight - mouseY) * guiHeight / screenHeight;
            List<Action> actions = ConfigHandler.getConfig().getActions();
            int maxItems = ACTION_LIST_HEIGHT / ACTION_ITEM_HEIGHT;
            int maxScroll = Math.max(0, actions.size() - maxItems) * ACTION_ITEM_HEIGHT;
            float scrollAreaHeight = ACTION_LIST_HEIGHT - scrollbarHeight;
            float scrollRatio = (mouseYGui - contentTop - 60) / scrollAreaHeight;
            scrollOffset = scrollRatio * maxScroll;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
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
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, fadeAnimation);
        GuiRenderUtils.drawOverlay(width, height);

        GuiRenderUtils.drawRoundedRect(contentLeft, contentTop, CONTENT_WIDTH, CONTENT_HEIGHT, 8, 0xFF263238);
        fontRenderer.drawString(getTabName(currentTab), contentLeft + 20, contentTop + 20, GuiRenderUtils.getTextColor());

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
        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void drawSettingsTab(int mouseX, int mouseY, float partialTicks) {
        fontRenderer.drawString("DonatePay Token:", contentLeft + 20, contentTop + 50, GuiRenderUtils.getTextColor());
        tokenField.drawTextBox();
        fontRenderer.drawString("User ID:", contentLeft + 20, contentTop + 100, GuiRenderUtils.getTextColor());
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
}