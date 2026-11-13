package com.notesdesk;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.Timer;

/**
 * Persona 3 Reload style title screen: black/light-blue diagonal field, a crooked right-aligned
 * command list with a glowing selection bar, water-blue accent bands.
 */
class MainMenuScreen extends JPanel {
    private final String[] labels = {"ENTER THE DESK", "FIELD NOTES", "OPTIONS", "EXIT"};
    private final double[] tilt;
    private int selected = 0;
    private float pulse = 0f;
    private float flash = 0f;
    private final Timer pulseTimer;
    private final List<Rectangle> hitboxes = new ArrayList<>();
    private final Runnable[] actions;
    private final long dayNumber;

    MainMenuScreen(Runnable enterDesk, Runnable openFieldNotes, Runnable openOptions, Runnable exit, long dayNumber) {
        this.actions = new Runnable[] {enterDesk, openFieldNotes, openOptions, exit};
        this.dayNumber = dayNumber;
        tilt = new double[labels.length];
        for (int i = 0; i < labels.length; i++) {
            tilt[i] = ((i % 2 == 0) ? -1 : 1) * (1.2 + (i * 0.35));
        }
        setLayout(null);
        setBackground(Theme.BG);
        setFocusable(true);

        pulseTimer = new Timer(30, event -> {
            if (!isShowing()) {
                return;
            }
            pulse += 0.06f;
            flash = Math.max(0f, flash - 0.12f);
            repaint();
        });
        pulseTimer.start();

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_UP) {
                    selected = (selected - 1 + labels.length) % labels.length;
                    flash = 1f;
                    repaint();
                } else if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                    selected = (selected + 1) % labels.length;
                    flash = 1f;
                    repaint();
                } else if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    activate(selected);
                }
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                for (int i = 0; i < hitboxes.size(); i++) {
                    if (hitboxes.get(i).contains(event.getPoint()) && selected != i) {
                        selected = i;
                        flash = 1f;
                        repaint();
                    }
                }
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                for (int i = 0; i < hitboxes.size(); i++) {
                    if (hitboxes.get(i).contains(event.getPoint())) {
                        activate(i);
                    }
                }
            }
        });
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent event) {
                requestFocusInWindow();
            }
        });
    }

    private void activate(int index) {
        actions[index].run();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();

        g2.setColor(Theme.BG);
        g2.fillRect(0, 0, w, h);

        GeneralPath big = new GeneralPath();
        big.moveTo(w * 0.36f, 0);
        big.lineTo(w, 0);
        big.lineTo(w, h);
        big.lineTo(w * 0.14f, h);
        big.closePath();
        g2.setColor(Theme.PANEL);
        g2.fill(big);

        GeneralPath accent = new GeneralPath();
        accent.moveTo(w * 0.40f, 0);
        accent.lineTo(w * 0.46f, 0);
        accent.lineTo(w * 0.24f, h);
        accent.lineTo(w * 0.18f, h);
        accent.closePath();
        g2.setColor(Theme.BLUE);
        g2.fill(accent);

        GeneralPath accent2 = new GeneralPath();
        accent2.moveTo(w * 0.46f, 0);
        accent2.lineTo(w * 0.475f, 0);
        accent2.lineTo(w * 0.255f, h);
        accent2.lineTo(w * 0.24f, h);
        accent2.closePath();
        g2.setColor(Theme.CYAN);
        g2.fill(accent2);

        // thin water-ripple lines drifting across the panel, in place of a static block
        g2.setColor(new Color(97, 223, 235, 40));
        for (int i = 0; i < 5; i++) {
            float phase = (float) ((pulse * 18 + i * 70) % (w + 200) - 200);
            g2.drawLine((int) (w * 0.5f + phase), 0, (int) (w * 0.5f + phase - h * 0.4f), h);
        }
        Theme.paintBubbles(g2, w, h, pulse * 0.4f, 16, Theme.CYAN);

        drawMoon(g2, (int) (w * 0.14f), (int) (h * 0.18f), 46);
        g2.setColor(Theme.MUTED);
        g2.setFont(Theme.monoBold(13));
        String dayLabel = "DAY " + Math.max(1, dayNumber);
        int dayLabelX = (int) (w * 0.14f) - g2.getFontMetrics().stringWidth(dayLabel) / 2;
        g2.drawString(dayLabel, dayLabelX, (int) (h * 0.18f) - 46 - 16);

        g2.setColor(Theme.CYAN);
        g2.setFont(Theme.display(48));
        g2.drawString("NOTES", (int) (w * 0.07f), (int) (h * 0.30f));
        g2.setColor(Theme.MUTED);
        g2.setFont(Theme.label(14));
        g2.drawString("DIGITAL FORENSICS  //  CASE ARCHIVE TERMINAL", (int) (w * 0.075f), (int) (h * 0.30f) + 26);

        float blink = (float) (0.5 + 0.5 * Math.sin(pulse * 1.6));
        g2.setColor(new Color(236, 246, 252, (int) (110 + blink * 100)));
        g2.setFont(Theme.label(12));
        g2.drawString("PRESS ENTER  //  SELECT A COMMAND", (int) (w * 0.075f), (int) (h * 0.30f) + 54);

        paintMenu(g2, w, h);

        g2.setColor(Theme.MUTED);
        g2.setFont(Theme.label(10));
        g2.drawString("SAVE LOCATION  //  " + Storage.SAVE_FILE, 20, h - 34);
        g2.drawString("CTRL+K  //  QUICK SEARCH EVERYTHING", 20, h - 18);
        g2.dispose();
    }

    private void paintMenu(Graphics2D g2, int w, int h) {
        hitboxes.clear();
        int menuTop = (int) (h * 0.50f);
        int rowHeight = 62;
        for (int i = 0; i < labels.length; i++) {
            int y = menuTop + i * rowHeight;
            int rowX = (int) (w * 0.58f);
            int rowW = (int) (w * 0.36f);
            Rectangle box = new Rectangle(rowX, y - 34, rowW, 48);
            hitboxes.add(box);

            AffineTransform saved = g2.getTransform();
            double angle = Math.toRadians(i == selected ? tilt[i] * 0.4 : tilt[i]);
            g2.rotate(angle, box.x + box.width / 2.0, box.y + box.height / 2.0);

            g2.setFont(Theme.display(23));
            int textWidth = g2.getFontMetrics().stringWidth(labels[i]);

            if (i == selected) {
                float wipe = (float) (0.5 + 0.5 * Math.sin(pulse * 3));
                GeneralPath bar = new GeneralPath();
                bar.moveTo(box.x + 18, box.y);
                bar.lineTo(box.x + box.width, box.y);
                bar.lineTo(box.x + box.width - 18, box.y + box.height);
                bar.lineTo(box.x, box.y + box.height);
                bar.closePath();
                g2.setColor(Theme.BLUE_DEEP);
                g2.fill(bar);
                g2.setColor(new Color(97, 223, 235, (int) (90 + wipe * 100)));
                g2.setStroke(new java.awt.BasicStroke(2.5f));
                g2.draw(bar);
                g2.setColor(new Color(236, 246, 252, (int) (140 + wipe * 90)));
                g2.fillRect(box.x, box.y, 8, box.height);
                if (flash > 0f) {
                    g2.setColor(new Color(255, 255, 255, (int) (flash * 120)));
                    g2.fill(bar);
                }
                g2.setColor(Color.WHITE);
            } else {
                g2.setColor(Theme.MUTED);
            }
            int textX = rowX + rowW - textWidth - 22;
            g2.drawString(labels[i], textX, y);
            g2.setTransform(saved);
        }
    }

    private void drawMoon(Graphics2D g2, int cx, int cy, int radius) {
        Ellipse2D full = new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2);
        Ellipse2D bite = new Ellipse2D.Float(cx - radius + radius * 0.7f, cy - radius, radius * 2, radius * 2);
        Area moon = new Area(full);
        moon.subtract(new Area(bite));
        g2.setColor(new Color(236, 246, 252, 235));
        g2.fill(moon);
    }
}
