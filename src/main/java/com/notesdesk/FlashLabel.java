package com.notesdesk;

import javax.swing.JLabel;

/** A JLabel that can briefly flash white before easing back to its set color — same confirm-feedback
 * language as the button click-flash and list-row selection-flash, applied to status text. */
class FlashLabel extends JLabel {
    private java.awt.Color base = Theme.INK;
    private float flashAmount = 0f;
    private final Runnable tick = () -> {
        if (flashAmount > 0f) {
            flashAmount = Math.max(0f, flashAmount - 0.07f);
            super.setForeground(Theme.blend(base, java.awt.Color.WHITE, flashAmount));
        }
    };

    FlashLabel(String text) {
        super(text);
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
    public void setForeground(java.awt.Color color) {
        base = color;
        super.setForeground(color);
    }

    /** Call after changing the text/color to a "success" state to draw the eye to it. */
    void flash() {
        flashAmount = 1f;
    }
}
