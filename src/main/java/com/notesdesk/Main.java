package com.notesdesk;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** App entry point: owns the frame, the card-switch + wipe-transition wiring, and the autosave loop. */
public class Main {
    private static final String CARD_MENU = "MENU";
    private static final String CARD_DESK = "DESK";

    private static final java.time.format.DateTimeFormatter DIARY_TITLE =
            java.time.format.DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");

    private final NotebookPanel notesPanel = new NotebookPanel(
            "DAILY LOG", "A day-to-day log of what you did and learned here.", "LOG",
            "[ + ]  NEW ENTRY", this::scheduleAutosave,
            () -> java.time.LocalDate.now().format(DIARY_TITLE), true);
    private final NotebookPanel fieldPanel = new NotebookPanel(
            "FIELD NOTES", "Things worth remembering.", "NOTE",
            "[ + ]  NEW NOTE", this::scheduleAutosave);
    private final TaskPanel taskPanel = new TaskPanel(this::scheduleAutosave);
    private final FlashLabel saveState = new FlashLabel("SYNC: READY");

    private JFrame frame;
    private WorkspaceScreen workspace;
    private final CardLayout cards = new CardLayout();
    private final JPanel cardHost = new JPanel(cards);
    private WipeTransition wipeOverlay;
    private Timer autosaveTimer;
    private long firstUseEpochDay;

    public static void main(String[] args) {
        Theme.installLookAndFeel();
        SwingUtilities.invokeLater(() -> new Main().show());
    }

    private void show() {
        Storage.Loaded loaded = Storage.load();
        notesPanel.setEntries(loaded.notes());
        fieldPanel.setEntries(loaded.fieldNotes());
        taskPanel.setTasks(loaded.tasks());
        firstUseEpochDay = loaded.firstUseEpochDay();
        long dayNumber = java.time.LocalDate.now().toEpochDay() - firstUseEpochDay + 1;

        frame = new JFrame("Notes Desk");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1040, 680));
        frame.setSize(1360, 820);
        frame.setLocationRelativeTo(null);

        workspace = new WorkspaceScreen(notesPanel, fieldPanel, taskPanel,
                () -> goTo(CARD_MENU), this::chooseSyncLocation, this::backupNow,
                saveState, Storage.SAVE_FILE);
        MainMenuScreen menu = new MainMenuScreen(
                () -> goTo(CARD_DESK),
                () -> goTo(CARD_DESK, () -> workspace.showCard(WorkspaceScreen.CARD_FIELD)),
                this::showOptions,
                this::exitApp,
                dayNumber);

        cardHost.add(menu, CARD_MENU);
        cardHost.add(workspace, CARD_DESK);
        cards.show(cardHost, CARD_MENU);
        frame.setContentPane(cardHost);

        wipeOverlay = new WipeTransition();
        frame.setGlassPane(wipeOverlay);
        installGlobalShortcuts();

        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        autosaveTimer = new Timer(900, event -> save());
        autosaveTimer.setRepeats(false);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                save();
            }
        });
        // Windows' own snap-to-half-screen resize doesn't always propagate through the layered pane's
        // manually-managed bounds (see WorkspaceScreen.refreshLayout) until something else forces a
        // relayout — so force one directly on every resize instead of waiting for the next card switch.
        frame.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override
            public void componentResized(java.awt.event.ComponentEvent event) {
                workspace.refreshLayout();
                cardHost.revalidate();
                cardHost.repaint();
            }
        });
        frame.setVisible(true);
    }

    private void installGlobalShortcuts() {
        javax.swing.KeyStroke quickSearch = javax.swing.KeyStroke.getKeyStroke(
                java.awt.event.KeyEvent.VK_K, java.awt.event.InputEvent.CTRL_DOWN_MASK);
        frame.getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(quickSearch, "quickSearch");
        frame.getRootPane().getActionMap().put("quickSearch", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                openQuickSearch();
            }
        });
    }

    private void openQuickSearch() {
        QuickSearchDialog dialog = new QuickSearchDialog(frame, this::allSearchHits, this::jumpTo);
        dialog.setVisible(true);
    }

    private java.util.List<QuickSearchDialog.Hit> allSearchHits() {
        java.util.List<QuickSearchDialog.Hit> hits = new java.util.ArrayList<>();
        for (Entry entry : notesPanel.entries()) {
            hits.add(new QuickSearchDialog.Hit("LOG", entry.title(), entry.body()));
        }
        for (Entry entry : fieldPanel.entries()) {
            hits.add(new QuickSearchDialog.Hit("FIELD NOTE", entry.title(), entry.body()));
        }
        for (Task task : taskPanel.tasks()) {
            hits.add(new QuickSearchDialog.Hit("TASK", task.text(), task.priority().toString()));
        }
        return hits;
    }

    private void jumpTo(QuickSearchDialog.Hit hit) {
        cards.show(cardHost, CARD_DESK);
        switch (hit.category()) {
            case "LOG" -> {
                workspace.showCard(WorkspaceScreen.CARD_EVIDENCE);
                notesPanel.selectByTitle(hit.title());
            }
            case "FIELD NOTE" -> {
                workspace.showCard(WorkspaceScreen.CARD_FIELD);
                fieldPanel.selectByTitle(hit.title());
            }
            case "TASK" -> {
                workspace.showCard(WorkspaceScreen.CARD_TASKS);
                taskPanel.selectByText(hit.title());
            }
            default -> { }
        }
    }

    private void showOptions() {
        OptionsDialog dialog = new OptionsDialog(frame, Storage.SAVE_FILE, this::chooseSyncLocation, this::backupNow);
        dialog.setVisible(true);
    }

    private void goTo(String cardName) {
        goTo(cardName, null);
    }

    private void goTo(String cardName, Runnable afterShow) {
        if (wipeOverlay.isRunning()) {
            return;
        }
        wipeOverlay.play(() -> {
            cards.show(cardHost, cardName);
            if (afterShow != null) {
                afterShow.run();
            }
        });
    }

    private void exitApp() {
        save();
        System.exit(0);
    }

    private void scheduleAutosave() {
        saveState.setText("SYNC: PENDING...");
        saveState.setForeground(new Color(245, 196, 81));
        if (autosaveTimer != null) {
            autosaveTimer.restart();
        } else {
            save();
        }
    }

    private void save() {
        Storage.SaveOutcome outcome = Storage.save(notesPanel.entries(), taskPanel.tasks(), fieldPanel.entries(),
                firstUseEpochDay);
        switch (outcome) {
            case SAVED -> {
                saveState.setText("SYNC: SAVED  //  " + java.time.LocalTime.now().withNano(0));
                saveState.setForeground(Theme.CYAN);
                saveState.flash();
            }
            case CONFLICT -> {
                saveState.setText("SYNC: NEWER COPY SYNCED IN — RESTART TO RELOAD");
                saveState.setForeground(new Color(245, 196, 81));
            }
            case ERROR -> {
                saveState.setText("SYNC: ERROR");
                saveState.setForeground(new Color(247, 91, 115));
            }
        }
    }

    /**
     * Lets the user point the save file at any folder — a Google Drive, Dropbox, or manually-synced
     * folder — regardless of which cloud provider the *other* computer uses, since each machine only
     * needs to agree on which folder holds the file, not which app keeps that folder in sync.
     */
    private void chooseSyncLocation() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setFileSelectionMode(javax.swing.JFileChooser.DIRECTORIES_ONLY);
        chooser.setDialogTitle("Choose a folder both computers can see (e.g. Google Drive, Dropbox)");
        chooser.setCurrentDirectory(Storage.SAVE_FILE.getParent().toFile());
        if (chooser.showOpenDialog(frame) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path chosen = chooser.getSelectedFile().toPath();
        save();
        boolean ok = Storage.setConfiguredDirectory(chosen);
        if (!ok) {
            javax.swing.JOptionPane.showMessageDialog(frame,
                    "Couldn't write to that folder. Check permissions and try again.",
                    "Sync location", javax.swing.JOptionPane.ERROR_MESSAGE);
            return;
        }
        int restart = javax.swing.JOptionPane.showConfirmDialog(frame,
                "Notes will save to:\n" + chosen.resolve("notes-desk-save.txt")
                        + "\n\nThis takes effect after a restart. Restart now?",
                "Sync location saved", javax.swing.JOptionPane.YES_NO_OPTION);
        if (restart == javax.swing.JOptionPane.YES_OPTION) {
            relaunch();
        }
    }

    /** Best-effort relaunch (works when running from the packaged jar); otherwise asks for a manual restart. */
    private void relaunch() {
        try {
            String javaBin = System.getProperty("java.home") + java.io.File.separator + "bin"
                    + java.io.File.separator + "javaw";
            java.io.File jar = new java.io.File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (jar.getName().endsWith(".jar")) {
                new ProcessBuilder(javaBin, "-jar", jar.getAbsolutePath()).start();
                System.exit(0);
                return;
            }
        } catch (Exception ignored) {
            // fall through to the manual-restart message
        }
        javax.swing.JOptionPane.showMessageDialog(frame, "Please close and reopen Notes to finish switching.",
                "Restart needed", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    private void backupNow() {
        save();
        java.util.Optional<java.nio.file.Path> backup = Storage.backupNow();
        if (backup.isPresent()) {
            javax.swing.JOptionPane.showMessageDialog(frame, "Backup written to:\n" + backup.get(),
                    "Backup saved", javax.swing.JOptionPane.INFORMATION_MESSAGE);
        } else {
            javax.swing.JOptionPane.showMessageDialog(frame, "Nothing to back up yet.",
                    "Backup", javax.swing.JOptionPane.WARNING_MESSAGE);
        }
    }

}
