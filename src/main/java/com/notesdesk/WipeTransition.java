package com.notesdesk;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;

import javax.swing.JComponent;
import javax.swing.Timer;

/** Sweeps angled color bands across the frame and swaps the visible card at the midpoint, Persona-style. */
class WipeTransition extends JComponent {
    // The big, rare main-menu <-> desk change: fully opaque, ends on a bright near-white flash.
    private static final Color[] BANDS_FULL = {Theme.BG, Theme.PANEL, Theme.BLUE_DEEP, Theme.BLUE, Theme.HILITE};
    // The frequent section switch: same shapes, but translucent and capped in the blue family, so it
    // reads as a quick tinted sweep instead of a strobe when it fires on every rail click.
    private static final Color[] BANDS_SOFT = {
            withAlpha(Theme.BG, 190), withAlpha(Theme.BLUE_DEEP, 175), withAlpha(Theme.BLUE, 140), withAlpha(Theme.CYAN, 90)
    };

    private final int durationMs;
    private final Color[] bands;
    private float progress = 0f;
    private boolean running = false;
    private Timer timer;
    private Runnable midpointAction;
    private boolean fired;

    WipeTransition() {
        this(620, false);
    }

    /** A shorter/faster, translucent instance for smaller-scale transitions (switching a section, not the whole screen). */
    WipeTransition(int durationMs, boolean soft) {
        this.durationMs = durationMs;
        this.bands = soft ? BANDS_SOFT : BANDS_FULL;
        setOpaque(false);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    boolean isRunning() {
        return running;
    }

    void play(Runnable atMidpoint) {
        this.midpointAction = atMidpoint;
        this.fired = false;
        this.progress = 0f;
        this.running = true;
        setVisible(true);
        long start = System.currentTimeMillis();
        timer = new Timer(12, event -> {
            float t = Math.min(1f, (System.currentTimeMillis() - start) / (float) durationMs);
            progress = t;
            if (!fired && t >= 0.5f) {
                fired = true;
                midpointAction.run();
            }
            repaint();
            if (t >= 1f) {
                timer.stop();
                running = false;
                setVisible(false);
            }
        });
        timer.start();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (!running && progress <= 0f) {
            return;
        }
        Graphics2D g2 = (Graphics2D) graphics.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int skew = Math.max(120, h / 3);
        for (int i = 0; i < bands.length; i++) {
            float stagger = i * 0.045f;
            float local = clamp((progress - stagger) / (1f - stagger), 0f, 1f);
            int travel = w + skew + 200;
            int x = (int) (-travel + local * travel * 2);
            GeneralPath path = new GeneralPath();
            path.moveTo(x, 0);
            path.lineTo(x + w + skew, 0);
            path.lineTo(x + w, h);
            path.lineTo(x - skew, h);
            path.closePath();
            g2.setColor(bands[i]);
            g2.fill(path);
        }
        g2.dispose();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
