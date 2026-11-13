package com.notesdesk;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.JButton;
import javax.swing.plaf.basic.BasicComboBoxUI;

/** Strips the beveled native dropdown arrow down to a flat theme-colored triangle. */
class FlatComboBoxUI extends BasicComboBoxUI {
    @Override
    protected JButton createArrowButton() {
        JButton button = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g2 = (Graphics2D) graphics.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(Theme.PANEL_RAISED);
                g2.fillRect(0, 0, getWidth(), getHeight());
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.setColor(Theme.CYAN);
                int[] xs = {cx - 4, cx + 4, cx};
                int[] ys = {cy - 2, cy - 2, cy + 4};
                g2.fillPolygon(xs, ys, 3);
                g2.dispose();
            }
        };
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        return button;
    }
}
