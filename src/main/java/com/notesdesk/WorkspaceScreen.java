package com.notesdesk;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

/**
 * The desk: a rail on the left swaps between full-width sections (CardLayout), Persona-hub style,
 * instead of cramming notes/tasks/glossary into one screen.
 */
class WorkspaceScreen extends JPanel {
    static final String CARD_EVIDENCE = "EVIDENCE";
    static final String CARD_FIELD = "FIELD";
    static final String CARD_TASKS = "TASKS";

    private final java.awt.CardLayout sections = new java.awt.CardLayout();
    private final JPanel sectionHost = new JPanel(sections);
    private final JLayeredPane sectionLayers = new JLayeredPane();
    private final WipeTransition sectionWipe = new WipeTransition(380, true);
    private String activeCard = CARD_EVIDENCE;
    private final java.util.Map<String, RailButton> railButtons = new java.util.LinkedHashMap<>();

    WorkspaceScreen(NotebookPanel notes, NotebookPanel fieldNotes, TaskPanel tasks, Runnable backToMenu,
                     Runnable openSyncLocation, Runnable backupNow,
                     JLabel saveState, java.nio.file.Path saveFile) {
        super(new BorderLayout(18, 0));
        setBackground(Theme.BG);
        setBorder(BorderFactory.createEmptyBorder(22, 22, 18, 22));

        JLabel heading = new JLabel("THE DESK  //  CASE WORKSPACE");
        heading.setFont(Theme.display(27));
        heading.setForeground(Theme.CYAN);
        JLabel subtitle = new JLabel("LOGS  +  NOTES  +  TASKS");
        subtitle.setFont(Theme.label(12));
        subtitle.setForeground(Theme.MUTED);
        JPanel header = new GlintBar(new BorderLayout(0, 3));
        header.setBackground(new Color(4, 13, 25));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 2, 0, Theme.CYAN),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        header.add(heading, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.SOUTH);
        JButton backButton = AngledButton.ghost("[ <<  MAIN MENU ]");
        backButton.addActionListener(event -> backToMenu.run());
        JPanel menu = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 7, 0));
        menu.setOpaque(false);
        menu.add(backButton);
        header.add(menu, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        add(buildRail(openSyncLocation, backupNow, backToMenu), BorderLayout.WEST);

        sectionHost.setOpaque(false);
        sectionHost.add(notes, CARD_EVIDENCE);
        sectionHost.add(fieldNotes, CARD_FIELD);
        sectionHost.add(tasks, CARD_TASKS);
        sections.show(sectionHost, CARD_EVIDENCE);

        sectionLayers.setLayout(null);
        sectionLayers.add(sectionHost, JLayeredPane.DEFAULT_LAYER);
        sectionLayers.add(sectionWipe, JLayeredPane.PALETTE_LAYER);
        sectionLayers.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                refreshLayout();
            }
        });
        add(sectionLayers, BorderLayout.CENTER);

        JPanel footer = new GlintBar(new BorderLayout());
        footer.setBackground(Theme.PANEL_RAISED);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.EDGE),
                BorderFactory.createEmptyBorder(7, 12, 7, 12)));
        JLabel path = new JLabel("SAVE LOCATION  //  " + saveFile);
        path.setFont(Theme.mono(10));
        path.setForeground(Theme.MUTED);
        saveState.setFont(Theme.monoBold(10));
        saveState.setForeground(Theme.CYAN);
        footer.add(path, BorderLayout.WEST);
        footer.add(saveState, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private JPanel buildRail(Runnable openSyncLocation, Runnable backupNow, Runnable backToMenu) {
        JPanel rail = new AmbientRail(new BorderLayout(0, 18));
        rail.setPreferredSize(new Dimension(198, 0));
        rail.setBackground(new Color(5, 12, 22));
        rail.setBorder(BorderFactory.createEmptyBorder(18, 16, 18, 14));

        JLabel menuTitle = new JLabel("MAIN MENU");
        menuTitle.setFont(Theme.display(16));
        menuTitle.setForeground(Theme.CYAN);
        JPanel labels = new JPanel(new BorderLayout());
        labels.setOpaque(false);
        labels.add(menuTitle, BorderLayout.NORTH);
        rail.add(labels, BorderLayout.NORTH);

        JPanel commands = new JPanel();
        commands.setOpaque(false);
        commands.setLayout(new BoxLayout(commands, BoxLayout.Y_AXIS));
        commands.add(railCommand(CARD_EVIDENCE, "DAILY LOG", () -> showCard(CARD_EVIDENCE)));
        commands.add(railCommand(CARD_FIELD, "FIELD NOTES", () -> showCard(CARD_FIELD)));
        commands.add(railCommand(CARD_TASKS, "TASKS", () -> showCard(CARD_TASKS)));
        commands.add(Box.createVerticalStrut(6));
        commands.add(railCommand(null, "SYNC LOCATION", openSyncLocation));
        commands.add(railCommand(null, "BACKUP NOW", backupNow));
        commands.add(Box.createVerticalStrut(10));
        commands.add(railCommand(null, "<<  MAIN MENU", backToMenu));
        rail.add(commands, BorderLayout.CENTER);

        JLabel footer = new JLabel("CHAIN OF CUSTODY:  INTACT");
        footer.setFont(Theme.label(9));
        footer.setForeground(Theme.MUTED);
        rail.add(footer, BorderLayout.SOUTH);
        return rail;
    }

    /**
     * Re-applies the layered pane's manual child bounds to match its current size. The pane's own
     * componentResized listener already calls this on a normal resize, but it's exposed so the frame
     * can force it directly too — OS window snapping/restoring doesn't always fire the pane's own
     * resize event promptly, and this makes sure the desk keeps up without waiting for a card switch.
     */
    void refreshLayout() {
        sectionHost.setBounds(0, 0, sectionLayers.getWidth(), sectionLayers.getHeight());
        sectionWipe.setBounds(0, 0, sectionLayers.getWidth(), sectionLayers.getHeight());
        sectionHost.revalidate();
        revalidate();
        repaint();
    }

    void showCard(String card) {
        if (card.equals(activeCard) || sectionWipe.isRunning()) {
            return;
        }
        activeCard = card;
        for (var entry : railButtons.entrySet()) {
            entry.getValue().setActive(entry.getKey().equals(card));
        }
        sectionWipe.play(() -> sections.show(sectionHost, card));
    }

    private RailButton railCommand(String cardKey, String text, Runnable action) {
        RailButton command = new RailButton(text);
        command.addActionListener(event -> action.run());
        if (cardKey != null) {
            railButtons.put(cardKey, command);
            command.setActive(cardKey.equals(activeCard));
        }
        return command;
    }

    /** The nav rail: rising bubbles, faint drifting ripple lines, and a breathing edge instead of a static bar. */
    private static class AmbientRail extends JPanel {
        private float phase = 0f;
        private final Runnable tick = () -> {
            phase += 0.012f;
            repaint();
        };

        AmbientRail(BorderLayout layout) {
            super(layout);
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
        protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) graphics.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();

            g2.setColor(new Color(97, 223, 235, 14));
            for (int i = 0; i < 3; i++) {
                float driftPhase = (float) ((phase * 0.06 + i * 0.6) % 1.4);
                float x = driftPhase * (w + h) - h;
                g2.drawLine((int) x, 0, (int) (x + h * 0.6f), h);
            }

            Theme.paintBubbles(g2, w, h, phase, 9, Theme.CYAN);

            float glow = (float) (0.5 + 0.5 * Math.sin(phase * 0.9));
            g2.setColor(new Color(
                    (int) (Theme.EDGE.getRed() + (Theme.CYAN.getRed() - Theme.EDGE.getRed()) * glow * 0.7f),
                    (int) (Theme.EDGE.getGreen() + (Theme.CYAN.getGreen() - Theme.EDGE.getGreen()) * glow * 0.7f),
                    (int) (Theme.EDGE.getBlue() + (Theme.CYAN.getBlue() - Theme.EDGE.getBlue()) * glow * 0.7f)));
            g2.fillRect(w - 2, 0, 2, h);
            g2.dispose();
        }
    }

    /** The header bar with a faint diagonal glint slowly drifting across it, so the desk isn't static. */
    private static class GlintBar extends JPanel {
        private float phase = 0f;
        private final Runnable tick = () -> {
            phase = (phase + 0.0035f) % 1f;
            repaint();
        };

        GlintBar(BorderLayout layout) {
            super(layout);
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
        protected void paintComponent(java.awt.Graphics graphics) {
            super.paintComponent(graphics);
            java.awt.Graphics2D g2 = (java.awt.Graphics2D) graphics.create();
            g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            float x = phase * (w + 260) - 130;
            java.awt.geom.GeneralPath glint = new java.awt.geom.GeneralPath();
            glint.moveTo(x, 0);
            glint.lineTo(x + 70, 0);
            glint.lineTo(x - h, h);
            glint.lineTo(x - h - 70, h);
            glint.closePath();
            g2.setColor(new Color(97, 223, 235, 16));
            g2.fill(glint);
            g2.dispose();
        }
    }
}
