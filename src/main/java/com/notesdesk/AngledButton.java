package com.notesdesk;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;

import javax.swing.BorderFactory;
import javax.swing.JButton;

/**
 * A slant-cut button matching the main menu's selection-bar shape. Hover and press states ease in
 * smoothly (via the shared {@link Pulse} ticker) instead of snapping, and a click plays a brief
 * white flash sweep — the same kind of feedback the main menu's selection bar gets.
 */
class AngledButton extends JButton {
    private final boolean solid;
    private float hover = 0f;
    private float hoverTarget = 0f;
    private float clickFlash = 0f;
    private final Runnable tick = this::onTick;

    private AngledButton(String text, boolean solid) {
        super(text);
        this.solid = solid;
        setFont(Theme.monoBold(11));
        setForeground(solid ? Color.WHITE : Theme.CYAN);
        setContentAreaFilled(false);
        setFocusPainted(false);
        setBorderPainted(false);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 14));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hoverTarget = 1f;
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hoverTarget = 0f;
            }
        });
        addActionListener(event -> clickFlash = 1f);
    }

    static AngledButton ghost(String text) {
        return new AngledButton(text, false);
    }

    static AngledButton solid(String text) {
        return new AngledButton(text, true);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Pulse.add(tick);
    }

    @Override
    public void removeNotify() {
        Pulse.remove(tick);
        super.removeNotify();
    }

    private void onTick() {
        boolean changed = false;
        if (Math.abs(hover - hoverTarget) > 0.004f) {
            hover += (hoverTarget - hover) * 0.28f;
            changed = true;
        } else if (hover != hoverTarget) {
            hover = hoverTarget;
        }
        if (clickFlash > 0f) {
            clickFlash = Math.max(0f, clickFlash - 0.10f);
            changed = true;
        }
        if (changed) {
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        boolean pressed = getModel().isArmed() && getModel().isPressed();
        int inset = pressed ? 2 : 0;
        int w = getWidth();
        int h = getHeight() - inset * 2;
        int y0 = inset;
        int cut = Math.max(6, h / 3);
        GeneralPath shape = new GeneralPath();
        shape.moveTo(cut, y0);
        shape.lineTo(w, y0);
        shape.lineTo(w - cut, y0 + h);
        shape.lineTo(0, y0 + h);
        shape.closePath();

        if (solid) {
            Color base = pressed ? Theme.BLUE_DEEP : Theme.blend(Theme.BLUE, Theme.CYAN, hover * 0.7f);
            g2.setColor(base);
            g2.fill(shape);
        } else {
            if (hover > 0.01f) {
                g2.setColor(new Color(Theme.CYAN.getRed(), Theme.CYAN.getGreen(), Theme.CYAN.getBlue(),
                        (int) (hover * (pressed ? 70 : 38))));
                g2.fill(shape);
            }
            g2.setColor(Theme.CYAN);
            g2.setStroke(new BasicStroke(1.4f + hover * 0.8f));
            g2.draw(shape);
        }
        if (clickFlash > 0f) {
            g2.setColor(new Color(255, 255, 255, (int) (clickFlash * 110)));
            g2.fill(shape);
        }
        g2.dispose();
        super.paintComponent(graphics);
    }
}
