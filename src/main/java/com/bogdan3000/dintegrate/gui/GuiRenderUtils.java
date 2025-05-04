package com.bogdan3000.dintegrate.gui;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

/**
 * Utility class for rendering minimalist GUI elements with animations.
 * All methods are static and thread-safe for rendering purposes.
 */
public class GuiRenderUtils {
    // Color constants (ARGB format)
    private static final int COLOR_BACKGROUND = 0xD01C2526; // Semi-transparent dark gray
    private static final int COLOR_ACCENT = 0xFF4FC3F7; // Bright blue
    private static final int COLOR_HOVER = 0xFF81D4FA; // Lighter blue for hover
    private static final int COLOR_TEXT = 0xFFFFFFFF; // White text
    private static final int COLOR_ERROR = 0xFFFF5252; // Red for errors
    private static final int COLOR_SELECTED = 0xFF0288D1; // Darker blue for selected tab

    /**
     * Draws a rounded rectangle with specified color and corner radius.
     * @param x Left x-coordinate
     * @param y Top y-coordinate
     * @param width Rectangle width
     * @param height Rectangle height
     * @param radius Corner radius
     * @param color ARGB color
     */
    public static void drawRoundedRect(float x, float y, float width, float height, float radius, int color) {
        if (width <= 0 || height <= 0) return; // Prevent invalid dimensions
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        float alpha = ((color >> 24) & 0xFF) / 255.0F;
        float red = ((color >> 16) & 0xFF) / 255.0F;
        float green = ((color >> 8) & 0xFF) / 255.0F;
        float blue = (color & 0xFF) / 255.0F;

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);

        // Main body
        drawRect(buffer, x + radius, y + radius, x + width - radius, y + height - radius, red, green, blue, alpha);
        // Top and bottom strips
        drawRect(buffer, x + radius, y, x + width - radius, y + radius, red, green, blue, alpha);
        drawRect(buffer, x + radius, y + height - radius, x + width - radius, y + height, red, green, blue, alpha);
        // Left and right strips
        drawRect(buffer, x, y + radius, x + radius, y + height - radius, red, green, blue, alpha);
        drawRect(buffer, x + width - radius, y + radius, x + width, y + height - radius, red, green, blue, alpha);
        // Corners
        for (int i = 0; i < 4; i++) {
            float cx = i % 2 == 0 ? x + radius : x + width - radius;
            float cy = i < 2 ? y + radius : y + height - radius;
            drawCircleQuadrant(buffer, cx, cy, radius, i, red, green, blue, alpha);
        }

        tessellator.draw();
        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    /**
     * Helper to draw a rectangle in the buffer.
     */
    private static void drawRect(BufferBuilder buffer, float x1, float y1, float x2, float y2, float r, float g, float b, float a) {
        buffer.pos(x1, y2, 0).color(r, g, b, a).endVertex();
        buffer.pos(x2, y2, 0).color(r, g, b, a).endVertex();
        buffer.pos(x2, y1, 0).color(r, g, b, a).endVertex();
        buffer.pos(x1, y1, 0).color(r, g, b, a).endVertex();
    }

    /**
     * Draws a quadrant of a circle for rounded corners.
     */
    private static void drawCircleQuadrant(BufferBuilder buffer, float cx, float cy, float radius, int quadrant, float r, float g, float b, float a) {
        int segments = 16;
        float angleStep = (float) (Math.PI / 2 / segments);
        for (int i = 0; i < segments; i++) {
            float angle1 = quadrant * (float) (Math.PI / 2) + i * angleStep;
            float angle2 = angle1 + angleStep;
            float x1 = cx + radius * (float) Math.cos(angle1);
            float y1 = cy + radius * (float) Math.sin(angle1);
            float x2 = cx + radius * (float) Math.cos(angle2);
            float y2 = cy + radius * (float) Math.sin(angle2);
            buffer.pos(cx, cy, 0).color(r, g, b, a).endVertex();
            buffer.pos(x1, y1, 0).color(r, g, b, a).endVertex();
            buffer.pos(x2, y2, 0).color(r, g, b, a).endVertex();
        }
    }

    /**
     * Draws a button with hover and animation effects.
     * @param x Button x-coordinate
     * @param y Button y-coordinate
     * @param width Button width
     * @param height Button height
     * @param hovered Is the button hovered?
     * @param enabled Is the button enabled?
     * @param animationProgress Animation progress (0 to 1)
     */
    public static void drawButton(float x, float y, float width, float height, boolean hovered, boolean enabled, float animationProgress) {
        int baseColor = enabled ? COLOR_ACCENT : 0xFF666666;
        int color = hovered ? mixColors(baseColor, COLOR_HOVER, animationProgress) : baseColor;
        drawRoundedRect(x, y, width, height, 4, color);
    }

    /**
     * Draws a tab button with selection and hover effects.
     * @param x Tab x-coordinate
     * @param y Tab y-coordinate
     * @param width Tab width
     * @param height Tab height
     * @param hovered Is the tab hovered?
     * @param selected Is the tab selected?
     * @param animationProgress Animation progress (0 to 1)
     */
    public static void drawTabButton(float x, float y, float width, float height, boolean hovered, boolean selected, float animationProgress) {
        int baseColor = selected ? COLOR_SELECTED : 0xFF37474F;
        int color = hovered ? mixColors(baseColor, COLOR_HOVER, animationProgress) : baseColor;
        drawRoundedRect(x, y, width, height, 4, color);
        if (selected) {
            drawRoundedRect(x + width - 4, y, 4, height, 2, COLOR_ACCENT); // Selection indicator
        }
    }

    /**
     * Mixes two colors based on progress (0 to 1).
     * @param color1 First color (ARGB)
     * @param color2 Second color (ARGB)
     * @param progress Interpolation factor
     * @return Mixed color (ARGB)
     */
    public static int mixColors(int color1, int color2, float progress) {
        float p = Math.max(0, Math.min(1, progress));
        int a1 = (color1 >> 24) & 0xFF, r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int a2 = (color2 >> 24) & 0xFF, r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int a = (int) (a1 + (a2 - a1) * p);
        int r = (int) (r1 + (r2 - r1) * p);
        int g = (int) (g1 + (g2 - g1) * p);
        int b = (int) (b1 + (b2 - b1) * p);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Draws a semi-transparent full-screen overlay.
     * @param width Screen width
     * @param height Screen height
     */
    public static void drawOverlay(int width, int height) {
        drawRoundedRect(0, 0, width, height, 0, COLOR_BACKGROUND);
    }

    // Getter methods for colors
    public static int getTextColor() { return COLOR_TEXT; }
    public static int getErrorColor() { return COLOR_ERROR; }
    public static int getBackgroundColor() { return COLOR_BACKGROUND; }
}