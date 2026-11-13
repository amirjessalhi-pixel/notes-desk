package com.notesdesk;

import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

import javax.swing.BorderFactory;
import javax.swing.JTextField;

/** A search/text field that shows a muted placeholder until focused or typed into. */
class PlaceholderField extends JTextField {
    private final String placeholder;
    private boolean showingPlaceholder;

    PlaceholderField(String placeholder) {
        this.placeholder = placeholder;
        setFont(Theme.mono(12));
        setForeground(Theme.MUTED);
        setBackground(Theme.PANEL_RAISED);
        setCaretColor(Theme.CYAN);
        setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BLUE),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));
        setText(placeholder);
        showingPlaceholder = true;
        addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent event) {
                if (showingPlaceholder) {
                    setText("");
                    setForeground(Theme.INK);
                    showingPlaceholder = false;
                }
            }

            @Override
            public void focusLost(FocusEvent event) {
                if (getText().trim().isEmpty()) {
                    showingPlaceholder = true;
                    setForeground(Theme.MUTED);
                    setText(placeholder);
                }
            }
        });
    }

    /** The typed query, or empty string while the placeholder is showing. */
    String query() {
        return showingPlaceholder ? "" : getText().trim();
    }

    /** Programmatically sets real content (not the placeholder) — empty resets to the placeholder state. */
    void setValue(String value) {
        if (value == null || value.isEmpty()) {
            showingPlaceholder = true;
            setForeground(Theme.MUTED);
            setText(placeholder);
        } else {
            showingPlaceholder = false;
            setForeground(Theme.INK);
            setText(value);
        }
    }
}
