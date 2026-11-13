package com.notesdesk;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** The Config-screen equivalent: sync location, manual backup, and where things stand — all in one place. */
class OptionsDialog extends JDialog {
    OptionsDialog(JFrame owner, java.nio.file.Path saveFile, Runnable chooseSyncLocation, Runnable backupNow) {
        super(owner, "Options", true);
        setUndecorated(true);
        getRootPane().setBorder(BorderFactory.createLineBorder(Theme.CYAN, 2));

        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(Theme.PANEL);
        root.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

        JLabel heading = new JLabel("OPTIONS  //  CONFIG");
        heading.setFont(Theme.display(20));
        heading.setForeground(Theme.CYAN);
        root.add(heading, BorderLayout.NORTH);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new javax.swing.BoxLayout(body, javax.swing.BoxLayout.Y_AXIS));

        body.add(row("SAVE LOCATION", saveFile.toString()));
        body.add(javax.swing.Box.createVerticalStrut(12));
        body.add(actionRow("Point at a shared folder (Google Drive, Dropbox, ...) so a second computer sees the same notes.",
                "[ SYNC LOCATION ]", () -> { dispose(); chooseSyncLocation.run(); }));
        body.add(javax.swing.Box.createVerticalStrut(10));
        body.add(actionRow("Write a timestamped copy of the current save file as a manual safety net.",
                "[ BACKUP NOW ]", () -> { dispose(); backupNow.run(); }));
        body.add(javax.swing.Box.createVerticalStrut(16));
        body.add(row("SET UP ON ANOTHER COMPUTER", "Install Notes Desk there too, then use Sync Location on both machines to point at the"
                + " same shared folder. They don't need the same cloud provider, just the same folder.",
                90));
        body.add(javax.swing.Box.createVerticalStrut(6));
        body.add(row("OFFLINE", "Fully local — no network calls, no account, no telemetry. Sync only happens if your"
                + " chosen folder is itself cloud-synced; everything else works with no connection at all."));
        body.add(javax.swing.Box.createVerticalStrut(6));
        body.add(row("SHORTCUTS", "Ctrl+K search everywhere  ·  Ctrl+N new entry  ·  Ctrl+F find in list"
                + "  ·  Del delete  ·  Ctrl+Z undo delete"));

        root.add(body, BorderLayout.CENTER);

        JButton close = AngledButton.ghost("[ CLOSE ]");
        close.addActionListener(event -> dispose());
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        footer.setOpaque(false);
        footer.add(close);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        setSize(580, 500);
        setLocationRelativeTo(owner);
    }

    private JPanel row(String label, String value) {
        return row(label, value, 60);
    }

    private JPanel row(String label, String value, int height) {
        JPanel panel = new JPanel(new BorderLayout(4, 2));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(2000, height));
        JLabel labelLine = new JLabel(label);
        labelLine.setFont(Theme.monoBold(11));
        labelLine.setForeground(Theme.CYAN);
        javax.swing.JTextArea valueLine = new javax.swing.JTextArea(value);
        valueLine.setLineWrap(true);
        valueLine.setWrapStyleWord(true);
        valueLine.setEditable(false);
        valueLine.setOpaque(false);
        valueLine.setFont(Theme.mono(11));
        valueLine.setForeground(Theme.MUTED);
        panel.add(labelLine, BorderLayout.NORTH);
        panel.add(valueLine, BorderLayout.CENTER);
        return panel;
    }

    private JPanel actionRow(String description, String buttonText, Runnable action) {
        JPanel panel = new JPanel(new BorderLayout(10, 4));
        panel.setOpaque(false);
        panel.setAlignmentX(LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(2000, 60));
        javax.swing.JTextArea desc = new javax.swing.JTextArea(description);
        desc.setLineWrap(true);
        desc.setWrapStyleWord(true);
        desc.setEditable(false);
        desc.setOpaque(false);
        desc.setFont(Theme.mono(11));
        desc.setForeground(Theme.MUTED);
        panel.add(desc, BorderLayout.CENTER);
        JButton button = AngledButton.solid(buttonText);
        button.addActionListener(event -> action.run());
        panel.add(button, BorderLayout.EAST);
        return panel;
    }
}
