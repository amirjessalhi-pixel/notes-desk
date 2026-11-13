package com.notesdesk;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.GeneralPath;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingConstants;

/** A rail navigation item whose selection bar eases in/out smoothly instead of snapping on/off. */
class RailButton extends JButton {
    private float active = 0f;
    private float activeTarget = 0f;
    private float hover = 0f;
    private float hoverTarget = 0f;
    private final Runnable tick = this::onTick;

    RailButton(String text) {
        super(text);
        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setHorizontalAlignment(SwingConstants.LEFT);
        setAlignmentX(LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(198, 42));
        setFont(Theme.monoBold(12));
        setForeground(Theme.MUTED);
        setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 10));
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
    }

    void setActive(boolean value) {
        activeTarget = value ? 1f : 0f;
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
        if (Math.abs(active - activeTarget) > 0.004f) {
            active += (activeTarget - active) * 0.22f;
            changed = true;
        } else if (active != activeTarget) {
            active = activeTarget;
            changed = true;
        }
        if (Math.abs(hover - hoverTarget) > 0.004f) {
            hover += (hoverTarget - hover) * 0.3f;
            changed = true;
        }
        if (changed) {
            setForeground(Theme.blend(Theme.MUTED, Color.WHITE, active));
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        if (active > 0.01f) {
            GeneralPath bar = new GeneralPath();
            bar.moveTo(0, 0);
            bar.lineTo(w, 0);
            bar.lineTo(w - 14, h);
            bar.lineTo(0, h);
            bar.closePath();
            g2.setColor(new Color(Theme.BLUE_DEEP.getRed(), Theme.BLUE_DEEP.getGreen(), Theme.BLUE_DEEP.getBlue(),
                    (int) (255 * active)));
            g2.fill(bar);
            g2.setColor(new Color(Theme.CYAN.getRed(), Theme.CYAN.getGreen(), Theme.CYAN.getBlue(), (int) (255 * active)));
            g2.fillRect(0, 0, 4, h);
        }
        if (hover > 0.01f && active < 0.5f) {
            g2.setColor(new Color(Theme.CYAN.getRed(), Theme.CYAN.getGreen(), Theme.CYAN.getBlue(), (int) (hover * 22)));
            g2.fillRect(0, 0, w, h);
        }
        g2.dispose();
        super.paintComponent(graphics);
    }
}
