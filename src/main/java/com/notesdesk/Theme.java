package com.notesdesk;

import java.awt.Color;
import java.awt.Font;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;

/** Shared palette + type styles. Black/light-blue scheme (Persona 3 Reload uses blue, not P5's red). */
final class Theme {
    private Theme() { }

    static final Color BG = new Color(4, 9, 17);
    static final Color PANEL = new Color(9, 21, 35);
    static final Color PANEL_RAISED = new Color(13, 28, 46);
    static final Color EDGE = new Color(24, 58, 92);
    static final Color INK = new Color(236, 246, 252);
    static final Color MUTED = new Color(122, 155, 186);
    static final Color BLUE = new Color(41, 120, 214);
    static final Color BLUE_DEEP = new Color(16, 46, 87);
    static final Color CYAN = new Color(97, 223, 235);
    static final Color HILITE = new Color(226, 248, 255);
    static final Color HIGH = new Color(247, 99, 121);
    static final Color NORMAL = new Color(245, 196, 81);
    static final Color LOW = new Color(97, 223, 235);

    static Font display(int size) {
        return new Font("Segoe UI", Font.BOLD | Font.ITALIC, size);
    }

    static Font heading(int size) {
        return new Font("Segoe UI", Font.BOLD, size);
    }

    static Font label(int size) {
        return new Font("Segoe UI", Font.PLAIN, size);
    }

    static Font mono(int size) {
        return new Font("Consolas", Font.PLAIN, size);
    }

    static Font monoBold(int size) {
        return new Font("Consolas", Font.BOLD, size);
    }

    /**
     * Overrides the Swing defaults every stock dialog/combo-box/file-chooser inherits from, so
     * confirm dialogs, the sync-location file picker, and anything else we didn't hand-paint still
     * land in the same dark palette instead of popping up native gray-and-white.
     */
    static void installLookAndFeel() {
        put("Panel.background", PANEL);
        put("OptionPane.background", PANEL);
        put("OptionPane.messageForegroundColor", INK);
        put("OptionPane.messageFont", label(13));
        put("OptionPane.buttonFont", monoBold(11));
        put("Button.background", PANEL_RAISED);
        put("Button.foreground", INK);
        put("Button.select", BLUE_DEEP);
        put("Button.focus", new Color(0, 0, 0, 0));
        put("ComboBox.background", PANEL_RAISED);
        put("ComboBox.foreground", INK);
        put("ComboBox.selectionBackground", BLUE_DEEP);
        put("ComboBox.selectionForeground", Color.WHITE);
        put("ComboBox.buttonBackground", PANEL_RAISED);
        put("List.background", PANEL_RAISED);
        put("List.foreground", INK);
        put("List.selectionBackground", BLUE_DEEP);
        put("List.selectionForeground", Color.WHITE);
        put("ScrollBar.thumb", BLUE);
        put("ScrollBar.track", PANEL_RAISED);
        put("ToolTip.background", PANEL_RAISED);
        put("ToolTip.foreground", INK);
        put("ToolTip.font", mono(11));
        put("TextField.background", PANEL_RAISED);
        put("TextField.foreground", INK);
        put("TextField.caretForeground", CYAN);
        put("TextArea.background", PANEL_RAISED);
        put("TextArea.foreground", INK);
        put("TextArea.caretForeground", CYAN);
        put("Label.foreground", INK);
        put("Label.font", label(12));
        put("SplitPane.background", BG);
        put("SplitPaneDivider.draggingColor", BLUE_DEEP);
        put("Viewport.background", PANEL_RAISED);
        put("FileChooser.background", PANEL);
        put("Tree.background", PANEL_RAISED);
        put("Tree.foreground", INK);
        put("Table.background", PANEL_RAISED);
        put("Table.foreground", INK);
        put("Table.selectionBackground", BLUE_DEEP);
        put("Table.selectionForeground", Color.WHITE);
        put("TableHeader.background", PANEL);
        put("TableHeader.foreground", INK);
        put("CheckBox.background", PANEL);
        put("CheckBox.foreground", INK);
        put("RadioButton.background", PANEL);
        put("RadioButton.foreground", INK);
        put("PopupMenu.background", PANEL_RAISED);
        UIManager.put("PopupMenu.border", BorderFactory.createLineBorder(EDGE));
        put("MenuItem.background", PANEL_RAISED);
        put("MenuItem.foreground", INK);
        put("MenuItem.selectionBackground", BLUE_DEEP);
        put("MenuItem.selectionForeground", Color.WHITE);
        put("MenuItem.font", mono(12));
        put("Separator.foreground", EDGE);
        put("Separator.background", PANEL_RAISED);
        UIManager.put("Button.border", new EmptyBorder(new Insets(6, 12, 6, 12)));
    }

    private static void put(String key, Color color) {
        UIManager.put(key, new ColorUIResource(color));
    }

    private static void put(String key, Font font) {
        UIManager.put(key, new FontUIResource(font));
    }

    /** Recolors a split pane's divider to a thin theme-colored bar instead of the native gray bevel. */
    static void styleSplitDivider(javax.swing.JSplitPane splitPane) {
        // The Metal divider paints light "grip" bumps that ignore setBackground, so paint a flat bar instead.
        splitPane.setUI(new javax.swing.plaf.basic.BasicSplitPaneUI() {
            @Override
            public javax.swing.plaf.basic.BasicSplitPaneDivider createDefaultDivider() {
                return new javax.swing.plaf.basic.BasicSplitPaneDivider(this) {
                    @Override
                    public void paint(java.awt.Graphics graphics) {
                        graphics.setColor(EDGE);
                        graphics.fillRect(0, 0, getWidth(), getHeight());
                    }
                };
            }
        });
        splitPane.setBorder(null);
    }

    /** Linear interpolation between two colors — shared by every hover/flash/glow animation. */
    static Color blend(Color from, Color to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return new Color(
                (int) (from.getRed() + (to.getRed() - from.getRed()) * t),
                (int) (from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                (int) (from.getBlue() + (to.getBlue() - from.getBlue()) * t));
    }

    /**
     * Slow-drifting bubbles rising through the frame, callback to Persona 3 Reload's actual UI theme
     * (its designers built it around water and rising bubbles, not the diagonal cuts alone). Purely
     * procedural — every bubble is just a circle computed from its index and the caller's phase, so
     * there's nothing to license or ship as an asset.
     */
    static void paintBubbles(java.awt.Graphics2D g2, int w, int h, float phase, int count, Color color) {
        for (int i = 0; i < count; i++) {
            float seed = i * 37.13f;
            float speed = 0.4f + (i % 5) * 0.17f;
            float span = h + 60f;
            float y = h - ((phase * speed * span + seed * 19f) % span);
            float lane = (i * 0.6180339f) % 1f;
            float x = w * lane + (float) (Math.sin(phase * 1.6 + seed) * 16);
            float radius = 2.5f + (i % 4) * 1.4f;
            float twinkle = (float) (0.5 + 0.5 * Math.sin(phase * 2.3 + seed));
            int alpha = (int) (14 + twinkle * 22);
            g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(4, alpha)));
            g2.fillOval((int) x, (int) y, (int) (radius * 2), (int) (radius * 2));
        }
    }
}
