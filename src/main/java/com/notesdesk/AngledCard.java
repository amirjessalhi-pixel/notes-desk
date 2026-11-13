package com.notesdesk;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

/** A panel with a Persona-style angled top-left corner and a thin accent stripe, used for every card in the app. */
class AngledCard extends JPanel {
    private final Color edge;
    private final int cut;
    private float glowPhase = (float) (Math.random() * 10);
    private final Runnable tick;

    AngledCard(LayoutManager layout) {
        this(layout, Theme.PANEL, Theme.EDGE, 22);
    }

    AngledCard(LayoutManager layout, Color fill, Color edge, int cut) {
        super(layout);
        this.edge = edge;
        this.cut = cut;
        this.tick = () -> {
            glowPhase += 0.02f;
            repaint();
        };
        setOpaque(false);
        setBackground(fill);
        setBorder(BorderFactory.createEmptyBorder(cut + 10, 16, 14, 16));
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

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        GeneralPath body = new GeneralPath();
        body.moveTo(cut, 0);
        body.lineTo(w, 0);
        body.lineTo(w, h);
        body.lineTo(0, h);
        body.lineTo(0, cut);
        body.closePath();
        g2.setColor(getBackground());
        g2.fill(body);

        java.awt.Shape oldClip = g2.getClip();
        g2.clip(body);
        Theme.paintBubbles(g2, w, h, glowPhase * 0.5f, 5, Theme.CYAN);
        g2.setClip(oldClip);

        float glow = (float) (0.5 + 0.5 * Math.sin(glowPhase));
        Color breathing = new Color(
                (int) (edge.getRed() + (Theme.CYAN.getRed() - edge.getRed()) * glow * 0.6f),
                (int) (edge.getGreen() + (Theme.CYAN.getGreen() - edge.getGreen()) * glow * 0.6f),
                (int) (edge.getBlue() + (Theme.CYAN.getBlue() - edge.getBlue()) * glow * 0.6f));
        g2.setColor(breathing);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(0, cut, cut, 0);
        g2.setStroke(new BasicStroke(3f));
        g2.drawLine(cut, 0, Math.min(w, cut + 140), 0);
        g2.drawLine(0, cut, 0, Math.min(h, cut + 100));
        g2.dispose();
        super.paintComponent(graphics);
    }
}
