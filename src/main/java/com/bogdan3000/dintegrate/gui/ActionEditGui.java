package com.bogdan3000.dintegrate.gui;

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

public class ActionEditGui extends GuiScreen {
    private static final int CONTENT_MARGIN = 12;
    private static final int FIELD_HEIGHT = 20;
    private static final int DEFAULT_FIELD_WIDTH = 200;
    private static final int BUTTON_WIDTH = 24;
    private static final int EXPAND_BUTTON_WIDTH = 20;
    private static final int BUTTON_SPACING = 10;
    private static final int COMMAND_ITEM_HEIGHT = 28;
    private static final int COMMAND_ITEM_SPACING = 8;
    private int windowLeft, windowTop, windowWidth, windowHeight;
    private float fadeAnimation = 0.0f;
    private float scrollOffset = 0.0f;
    private boolean isDraggingScrollbar = false;
    private final GuiScreen parent;
    private final Action editingAction;
    private GuiTextField sumField;
    private GuiTextField priorityField;
    protected List<CommandEntry> commandEntries = new ArrayList<>(); // Changed to protected
    private CustomButton executionModeButton;
    private CustomButton enabledButton;
    private CustomButton saveButton;
    private CustomButton cancelButton;

    public static class CommandEntry { // Made public static
        public GuiTextField commandField;
        public CustomButton addButton;
        public CustomButton removeButton;
        public CustomButton expandButton;

        public CommandEntry(GuiTextField commandField, CustomButton addButton, CustomButton removeButton, CustomButton expandButton) {
            this.commandField = commandField;
            this.addButton = addButton;
            this.removeButton = removeButton;
            this.expandButton = expandButton;
        }
    }

    // Public static CustomButton class
    public static class CustomButton extends GuiButton {
        private float hoverAnimation = 0.0f;
        private boolean wasHovered = false;

        public CustomButton(int buttonId, int x, int y, int width, int height, String buttonText) {
            super(buttonId, x, y, width, height, buttonText);
        }

        @Override
        public void drawButton(Minecraft mc, int mouseX, int mouseY, float partialTicks) {
            if (!visible) return;

            boolean hovered = mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
            hoverAnimation = hovered ? Math.min(1.0f, hoverAnimation + partialTicks * 0.3f) :
                    Math.max(0.0f, hoverAnimation - partialTicks * 0.3f);
            wasHovered = hovered;

            GuiRenderUtils.drawButton(x, y, width, height, hovered, enabled, hoverAnimation);
            GuiRenderUtils.drawRoundedRect(x, y, width, height, 2, hovered ? 0xFF0288D1 : 0xFF90A4AE);
            drawCenteredString(mc.fontRenderer, displayString, x + width / 2, y + (height - 8) / 2, enabled ? 0xFFFFFFFF : 0xFF666666);
        }

        // Getter for displayString
        public String getDisplayString() {
            return displayString;
        }
    }

    public ActionEditGui(GuiScreen parent, Action editingAction) {
        this.parent = parent;
        this.editingAction = editingAction;
    }

    @Override
    public void initGui() {
        buttonList.clear();
        commandEntries.clear();
        Keyboard.enableRepeatEvents(true);
        fadeAnimation = 0.0f;
        scrollOffset = 0.0f;

        // Calculate window dimensions with margins from screen edges
        windowWidth = width - 2 * CONTENT_MARGIN;
        windowHeight = height - 2 * CONTENT_MARGIN;
        windowLeft = CONTENT_MARGIN;
        windowTop = CONTENT_MARGIN;

        // Adjust for internal content margin
        int contentLeft = windowLeft + CONTENT_MARGIN;
        int contentTop = windowTop + 40 + CONTENT_MARGIN; // Account for title and internal margin
        int contentWidth = windowWidth - 2 * CONTENT_MARGIN;
        int contentHeight = windowHeight - 40 - CONTENT_MARGIN; // Subtract space for title and internal margin

        // Left column (30% width)
        int leftWidth = (int) (contentWidth * 0.3);
        int leftX = contentLeft;
        int fieldY = contentTop;

        // Sum and priority fields
        sumField = new GuiTextField(0, fontRenderer, leftX, fieldY, DEFAULT_FIELD_WIDTH, FIELD_HEIGHT);
        sumField.setMaxStringLength(10);
        sumField.setText(editingAction != null ? String.format("%.2f", editingAction.getSum()) : "1.00");

        priorityField = new GuiTextField(1, fontRenderer, leftX, fieldY + 40 + CONTENT_MARGIN, DEFAULT_FIELD_WIDTH, FIELD_HEIGHT);
        priorityField.setMaxStringLength(5);
        priorityField.setText(editingAction != null ? String.valueOf(editingAction.getPriority()) : "1");

        // Buttons
        int buttonY = fieldY + 80 + CONTENT_MARGIN;
        executionModeButton = new CustomButton(2, leftX, buttonY, DEFAULT_FIELD_WIDTH, 24,
                I18n.format("dintegrate.gui.label.mode") + ": " + (editingAction != null ? editingAction.getExecutionMode().name() : "ALL"));
        enabledButton = new CustomButton(3, leftX, buttonY + 34, DEFAULT_FIELD_WIDTH, 24,
                editingAction != null ? (editingAction.isEnabled() ? I18n.format("dintegrate.gui.value.enabled") : I18n.format("dintegrate.gui.value.disabled")) : I18n.format("dintegrate.gui.value.enabled"));
        saveButton = new CustomButton(4, leftX, buttonY + 68, DEFAULT_FIELD_WIDTH, 24, I18n.format("dintegrate.gui.button.save"));
        cancelButton = new CustomButton(5, leftX, buttonY + 102, DEFAULT_FIELD_WIDTH, 24, I18n.format("dintegrate.gui.button.cancel"));

        buttonList.add(executionModeButton);
        buttonList.add(enabledButton);
        buttonList.add(saveButton);
        buttonList.add(cancelButton);

        // Right column (70% width)
        int rightWidth = contentWidth - leftWidth - CONTENT_MARGIN;
        int rightX = leftX + leftWidth + CONTENT_MARGIN;
        List<String> initialCommands = editingAction != null ? editingAction.getCommands() : new ArrayList<>();
        if (initialCommands.isEmpty()) initialCommands.add("");
        for (int i = 0; i < initialCommands.size(); i++) {
            addCommandEntry(i, initialCommands.get(i), rightX, rightWidth);
        }
        updateRemoveButtons();
    }

    private void addCommandEntry(int index, String commandText, int rightX, int rightWidth) {
        int commandFieldWidth = rightWidth - EXPAND_BUTTON_WIDTH - 2 * BUTTON_WIDTH - 3 * BUTTON_SPACING;
        int y = windowTop + 60 + CONTENT_MARGIN + index * (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING);
        GuiTextField commandField = new GuiTextField(100 + index, fontRenderer, rightX + EXPAND_BUTTON_WIDTH + BUTTON_SPACING, y + (COMMAND_ITEM_HEIGHT - FIELD_HEIGHT) / 2,
                commandFieldWidth, FIELD_HEIGHT);
        commandField.setMaxStringLength(200);
        commandField.setText(commandText);
        CustomButton addButton = new CustomButton(200 + index, rightX + commandFieldWidth + EXPAND_BUTTON_WIDTH + 2 * BUTTON_SPACING, y + (COMMAND_ITEM_HEIGHT - BUTTON_WIDTH) / 2, BUTTON_WIDTH, BUTTON_WIDTH, "+");
        CustomButton removeButton = new CustomButton(300 + index, rightX + commandFieldWidth + EXPAND_BUTTON_WIDTH + 3 * BUTTON_SPACING + BUTTON_WIDTH, y + (COMMAND_ITEM_HEIGHT - BUTTON_WIDTH) / 2, BUTTON_WIDTH, BUTTON_WIDTH, "−");
        CustomButton expandButton = new CustomButton(400 + index, rightX, y + (COMMAND_ITEM_HEIGHT - EXPAND_BUTTON_WIDTH) / 2, EXPAND_BUTTON_WIDTH, EXPAND_BUTTON_WIDTH, ">>");
        commandEntries.add(index, new CommandEntry(commandField, addButton, removeButton, expandButton));
        buttonList.add(addButton);
        buttonList.add(removeButton);
        buttonList.add(expandButton);
    }

    private void updateRemoveButtons() {
        for (CommandEntry entry : commandEntries) {
            entry.removeButton.enabled = commandEntries.size() > 1;
        }
    }

    private void updateButtonPositions() {
        int contentLeft = windowLeft + CONTENT_MARGIN;
        int contentTop = windowTop + 40 + CONTENT_MARGIN;
        int contentWidth = windowWidth - 2 * CONTENT_MARGIN;
        int leftWidth = (int) (contentWidth * 0.3);
        int rightWidth = contentWidth - leftWidth - CONTENT_MARGIN;
        int rightX = contentLeft + leftWidth + CONTENT_MARGIN;
        for (int i = 0; i < commandEntries.size(); i++) {
            CommandEntry entry = commandEntries.get(i);
            int y = contentTop + i * (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING);
            int commandFieldWidth = rightWidth - EXPAND_BUTTON_WIDTH - 2 * BUTTON_WIDTH - 3 * BUTTON_SPACING;
            entry.commandField.y = y + (COMMAND_ITEM_HEIGHT - FIELD_HEIGHT) / 2;
            entry.commandField.x = rightX + EXPAND_BUTTON_WIDTH + BUTTON_SPACING;
            entry.commandField.width = commandFieldWidth;
            entry.addButton.id = 200 + i;
            entry.addButton.y = y + (COMMAND_ITEM_HEIGHT - BUTTON_WIDTH) / 2;
            entry.addButton.x = rightX + commandFieldWidth + EXPAND_BUTTON_WIDTH + 2 * BUTTON_SPACING;
            entry.removeButton.id = 300 + i;
            entry.removeButton.y = y + (COMMAND_ITEM_HEIGHT - BUTTON_WIDTH) / 2;
            entry.removeButton.x = rightX + commandFieldWidth + EXPAND_BUTTON_WIDTH + 3 * BUTTON_SPACING + BUTTON_WIDTH;
            entry.expandButton.id = 400 + i;
            entry.expandButton.y = y + (COMMAND_ITEM_HEIGHT - EXPAND_BUTTON_WIDTH) / 2;
            entry.expandButton.x = rightX;
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= 200 && button.id < 300) { // Add command
            int index = button.id - 200;
            int contentLeft = windowLeft + CONTENT_MARGIN;
            int contentTop = windowTop + 40 + CONTENT_MARGIN;
            int contentWidth = windowWidth - 2 * CONTENT_MARGIN;
            int leftWidth = (int) (contentWidth * 0.3);
            int rightWidth = contentWidth - leftWidth - CONTENT_MARGIN;
            int rightX = contentLeft + leftWidth + CONTENT_MARGIN;
            addCommandEntry(index + 1, "", rightX, rightWidth);
            updateButtonPositions();
            updateRemoveButtons();
        } else if (button.id >= 300 && button.id < 400) { // Remove command
            int index = button.id - 300;
            if (commandEntries.size() > 1) {
                CommandEntry entry = commandEntries.remove(index);
                buttonList.remove(entry.addButton);
                buttonList.remove(entry.removeButton);
                buttonList.remove(entry.expandButton);
                updateButtonPositions();
                updateRemoveButtons();
            }
        } else if (button.id >= 400 && button.id < 500) { // Expand command
            int index = button.id - 400;
            CommandEntry entry = commandEntries.get(index);
            mc.displayGuiScreen(new CommandEditGui(this, index, entry.commandField.getText()));
        } else if (button.id == 2) { // Toggle execution mode
            Action.ExecutionMode currentMode = executionModeButton.getDisplayString().contains("ALL") ? Action.ExecutionMode.ALL : Action.ExecutionMode.RANDOM_ONE;
            Action.ExecutionMode newMode = currentMode == Action.ExecutionMode.ALL ? Action.ExecutionMode.RANDOM_ONE : Action.ExecutionMode.ALL;
            executionModeButton.displayString = I18n.format("dintegrate.gui.label.mode") + ": " + newMode.name();
        } else if (button.id == 3) { // Toggle enabled
            enabledButton.displayString = enabledButton.getDisplayString().contains(I18n.format("dintegrate.gui.value.enabled")) ?
                    I18n.format("dintegrate.gui.value.disabled") : I18n.format("dintegrate.gui.value.enabled");
        } else if (button.id == 4) { // Save
            try {
                float sum = Float.parseFloat(sumField.getText().trim());
                int priority = Integer.parseInt(priorityField.getText().trim());
                List<String> commands = new ArrayList<>();
                for (CommandEntry entry : commandEntries) {
                    String cmd = entry.commandField.getText().trim();
                    if (!cmd.isEmpty()) {
                        if (!cmd.startsWith("/")) {
                            mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.invalid_command"), false));
                            return;
                        }
                        commands.add(cmd);
                    }
                }
                Action.ExecutionMode mode = executionModeButton.getDisplayString().contains("ALL") ? Action.ExecutionMode.ALL : Action.ExecutionMode.RANDOM_ONE;
                boolean enabled = enabledButton.getDisplayString().contains(I18n.format("dintegrate.gui.value.enabled"));

                if (sum <= 0) {
                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.positive_sum"), false));
                    return;
                }
                if (priority < 0) {
                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.negative_priority"), false));
                    return;
                }
                if (commands.isEmpty()) {
                    mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.no_commands"), false));
                    return;
                }

                Action newAction = new Action(sum, enabled, priority, commands, mode);
                if (editingAction != null) {
                    int index = ConfigHandler.getConfig().getActions().indexOf(editingAction);
                    if (index >= 0) ConfigHandler.getConfig().getActions().set(index, newAction);
                } else {
                    ConfigHandler.getConfig().getActions().add(newAction);
                }
                ConfigHandler.save();
                mc.displayGuiScreen(new MessageGui(parent, I18n.format(editingAction != null ? "dintegrate.gui.message.action_updated" : "dintegrate.gui.message.action_added"), true));
            } catch (NumberFormatException e) {
                mc.displayGuiScreen(new MessageGui(this, I18n.format("dintegrate.gui.message.invalid_number"), false));
            }
        } else if (button.id == 5) { // Cancel
            mc.displayGuiScreen(parent);
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        GlStateManager.enableBlend();
        GlStateManager.color(1.0f, 1.0f, 1.0f, fadeAnimation * 0.8f);
        GuiRenderUtils.drawOverlay(width, height);
        GlStateManager.color(1.0f, 1.0f, 1.0f, fadeAnimation);
        GuiRenderUtils.drawRoundedRect(windowLeft, windowTop, windowWidth, windowHeight, 8, 0xFF263238);

        // Draw title and "Commands" label
        drawCenteredString(fontRenderer, I18n.format(editingAction != null ? "dintegrate.gui.title.edit_action" : "dintegrate.gui.title.add_action"),
                windowLeft + windowWidth / 2, windowTop + CONTENT_MARGIN, 0xFFFFFFFF);
        drawCenteredString(fontRenderer, I18n.format("dintegrate.gui.label.commands"),
                windowLeft + windowWidth / 2, windowTop + 40, 0xFFFFFFFF);

        // Left column
        int contentLeft = windowLeft + CONTENT_MARGIN;
        int contentTop = windowTop + 40 + CONTENT_MARGIN;
        int contentWidth = windowWidth - 2 * CONTENT_MARGIN;
        int leftWidth = (int) (contentWidth * 0.3);
        int leftX = contentLeft;
        int fieldY = contentTop;

        fontRenderer.drawStringWithShadow(I18n.format("dintegrate.gui.label.sum"), leftX, fieldY - 12, 0xFFFFFFFF);
        GuiRenderUtils.drawRoundedRect(leftX, fieldY, DEFAULT_FIELD_WIDTH, FIELD_HEIGHT, 2, 0xFF37474F);
        GuiRenderUtils.drawRoundedRect(leftX, fieldY, DEFAULT_FIELD_WIDTH, FIELD_HEIGHT, 2, sumField.isFocused() ? 0xFF0288D1 : 0xFF90A4AE);
        sumField.drawTextBox();

        fontRenderer.drawStringWithShadow(I18n.format("dintegrate.gui.label.priority"), leftX, fieldY + 28 + CONTENT_MARGIN, 0xFFFFFFFF);
        GuiRenderUtils.drawRoundedRect(leftX, fieldY + 40 + CONTENT_MARGIN, DEFAULT_FIELD_WIDTH, FIELD_HEIGHT, 2, 0xFF37474F);
        GuiRenderUtils.drawRoundedRect(leftX, fieldY + 40 + CONTENT_MARGIN, DEFAULT_FIELD_WIDTH, FIELD_HEIGHT, 2, priorityField.isFocused() ? 0xFF0288D1 : 0xFF90A4AE);
        priorityField.drawTextBox();

        // Right column
        int rightWidth = contentWidth - leftWidth - CONTENT_MARGIN;
        int rightX = leftX + leftWidth + CONTENT_MARGIN;
        int rightHeight = windowHeight - (contentTop - windowTop) - CONTENT_MARGIN;
        GuiRenderUtils.drawRoundedRect(rightX, contentTop, rightWidth, rightHeight, 4, 0xFF37474F);

        // Draw command list
        int listTop = contentTop;
        int listHeight = rightHeight;

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        int scaleFactor = new ScaledResolution(mc).getScaleFactor();
        GL11.glScissor(rightX * scaleFactor, (height - listTop - listHeight) * scaleFactor, rightWidth * scaleFactor, listHeight * scaleFactor);

        for (int i = 0; i < commandEntries.size(); i++) {
            CommandEntry entry = commandEntries.get(i);
            int y = listTop + i * (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING) - (int) scrollOffset;
            if (y + COMMAND_ITEM_HEIGHT >= listTop && y <= listTop + listHeight) {
                int commandFieldWidth = rightWidth - EXPAND_BUTTON_WIDTH - 2 * BUTTON_WIDTH - 3 * BUTTON_SPACING;
                entry.commandField.y = y + (COMMAND_ITEM_HEIGHT - FIELD_HEIGHT) / 2;
                entry.commandField.x = rightX + EXPAND_BUTTON_WIDTH + BUTTON_SPACING;
                entry.commandField.width = commandFieldWidth;
                GuiRenderUtils.drawRoundedRect(rightX, y, rightWidth, COMMAND_ITEM_HEIGHT, 2, 0xFF455A64);
                GuiRenderUtils.drawRoundedRect(entry.commandField.x, y + (COMMAND_ITEM_HEIGHT - FIELD_HEIGHT) / 2, commandFieldWidth, FIELD_HEIGHT, 2,
                        entry.commandField.isFocused() ? 0xFF0288D1 : 0xFF90A4AE);
                entry.commandField.drawTextBox();
                entry.expandButton.y = y + (COMMAND_ITEM_HEIGHT - EXPAND_BUTTON_WIDTH) / 2;
                entry.expandButton.x = rightX;
                entry.addButton.y = y + (COMMAND_ITEM_HEIGHT - BUTTON_WIDTH) / 2;
                entry.addButton.x = rightX + commandFieldWidth + EXPAND_BUTTON_WIDTH + 2 * BUTTON_SPACING;
                entry.removeButton.y = y + (COMMAND_ITEM_HEIGHT - BUTTON_WIDTH) / 2;
                entry.removeButton.x = rightX + commandFieldWidth + EXPAND_BUTTON_WIDTH + 3 * BUTTON_SPACING + BUTTON_WIDTH;
            }
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Draw scrollbar
        if (commandEntries.size() > listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING)) {
            int maxItems = listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING);
            int maxScroll = (commandEntries.size() - maxItems) * (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING);
            float scrollRatio = scrollOffset / (maxScroll > 0 ? maxScroll : 1);
            int scrollbarHeight = Math.max(20, listHeight * maxItems / commandEntries.size());
            int scrollbarTop = listTop + (int) ((listHeight - scrollbarHeight) * scrollRatio);
            GuiRenderUtils.drawRoundedRect(rightX + rightWidth - 10, scrollbarTop, 10, scrollbarHeight, 4, 0xFF546E7A);
        }

        // Draw buttons with hover effects
        for (GuiButton button : buttonList) {
            if (button instanceof CustomButton) {
                CustomButton customButton = (CustomButton) button;
                boolean hovered = mouseX >= customButton.x && mouseY >= customButton.y &&
                        mouseX < customButton.x + customButton.width && mouseY < customButton.y + customButton.height;
                GlStateManager.pushMatrix();
                if (hovered) {
                    GlStateManager.translate(customButton.x + customButton.width / 2f, customButton.y + customButton.height / 2f, 0);
                    GlStateManager.scale(1.05f, 1.05f, 1.0f);
                    GlStateManager.translate(-customButton.x - customButton.width / 2f, -customButton.y - customButton.height / 2f, 0);
                }
                customButton.drawButton(mc, mouseX, mouseY, partialTicks);
                GlStateManager.popMatrix();
            }
        }

        GlStateManager.color(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int mouseX = Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - Mouse.getEventY() * height / mc.displayHeight - 1;

        int contentLeft = windowLeft + CONTENT_MARGIN;
        int contentTop = windowTop + 40 + CONTENT_MARGIN;
        int contentWidth = windowWidth - 2 * CONTENT_MARGIN;
        int leftWidth = (int) (contentWidth * 0.3);
        int rightWidth = contentWidth - leftWidth - CONTENT_MARGIN;
        int rightX = contentLeft + leftWidth + CONTENT_MARGIN;
        int listTop = contentTop;
        int listHeight = windowHeight - (contentTop - windowTop) - CONTENT_MARGIN;

        if (commandEntries.size() > listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING)) {
            int wheel = Mouse.getEventDWheel();
            if (wheel != 0 && mouseX >= rightX && mouseX <= rightX + rightWidth &&
                    mouseY >= listTop && mouseY <= listTop + listHeight) {
                int maxItems = listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING);
                int maxScroll = (commandEntries.size() - maxItems) * (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING);
                scrollOffset -= wheel > 0 ? 20 : -20;
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            }

            if (Mouse.isButtonDown(0)) {
                int scrollbarLeft = rightX + rightWidth - 10;
                int scrollbarTop = listTop + (int) ((listHeight - Math.max(20, listHeight * (listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING)) / commandEntries.size())) * (scrollOffset / ((commandEntries.size() - (listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING))) * (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING))));
                if (!isDraggingScrollbar && mouseX >= scrollbarLeft && mouseX <= scrollbarLeft + 10 &&
                        mouseY >= scrollbarTop && mouseY <= scrollbarTop + Math.max(20, listHeight * (listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING)) / commandEntries.size())) {
                    isDraggingScrollbar = true;
                }
            } else {
                isDraggingScrollbar = false;
            }

            if (isDraggingScrollbar) {
                int maxScroll = (commandEntries.size() - (listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING))) * (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING);
                float scrollRatio = (mouseY - listTop - Math.max(20, listHeight * (listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING)) / commandEntries.size()) / 2.0f) / (listHeight - Math.max(20, listHeight * (listHeight / (COMMAND_ITEM_HEIGHT + COMMAND_ITEM_SPACING)) / commandEntries.size()));
                scrollOffset = scrollRatio * maxScroll;
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        sumField.mouseClicked(mouseX, mouseY, mouseButton);
        priorityField.mouseClicked(mouseX, mouseY, mouseButton);
        for (CommandEntry entry : commandEntries) {
            entry.commandField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        super.keyTyped(typedChar, keyCode);
        sumField.textboxKeyTyped(typedChar, keyCode);
        priorityField.textboxKeyTyped(typedChar, keyCode);
        for (CommandEntry entry : commandEntries) {
            entry.commandField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        sumField.updateCursorCounter();
        priorityField.updateCursorCounter();
        for (CommandEntry entry : commandEntries) {
            entry.commandField.updateCursorCounter();
        }
        if (fadeAnimation < 1.0f) {
            fadeAnimation = Math.min(1.0f, fadeAnimation + 0.05f);
        }
    }

    @Override
    public void onGuiClosed() {
        Keyboard.enableRepeatEvents(false);
    }

    // Public getter for parent
    public GuiScreen getParent() {
        return parent;
    }

    // Public getters for private fields
    public GuiTextField getSumField() {
        return sumField;
    }

    public GuiTextField getPriorityField() {
        return priorityField;
    }

    public CustomButton getExecutionModeButton() {
        return executionModeButton;
    }

    public CustomButton getEnabledButton() {
        return enabledButton;
    }

    public Action getEditingAction() {
        return editingAction;
    }
}