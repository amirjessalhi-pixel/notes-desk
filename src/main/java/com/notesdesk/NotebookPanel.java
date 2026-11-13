package com.notesdesk;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A searchable, editable notebook: a titled list of {@link Entry} records on the left, a title+body
 * editor on the right. Backs both the personal notes panel and the field-notes glossary. Pinned
 * entries float to the top, then everything else sorts most-recently-touched first.
 */
class NotebookPanel extends AngledCard {
    private final DefaultListModel<Entry> source = new DefaultListModel<>();
    private final DefaultListModel<Entry> view = new DefaultListModel<>();
    private final JList<Entry> list = new JList<>(view);
    private final JTextField titleField = new JTextField();
    private final JTextArea bodyArea = new JTextArea();
    private final JLabel status;
    private final JLabel wordCount;
    private final JButton pinButton;
    private final PlaceholderField search;
    private final String rowTag;
    private final Runnable onChange;
    private final java.util.function.Supplier<String> defaultTitle;
    private final boolean diaryMode;
    private int onThisDayCursor = 0;
    private Entry lastDeleted;
    private int flashIndex = -1;
    private float flashAmount = 0f;
    private final Runnable flashTick = () -> {
        if (flashAmount > 0f) {
            flashAmount = Math.max(0f, flashAmount - 0.09f);
            list.repaint();
        }
    };

    NotebookPanel(String heading, String description, String rowTag, String newLabel, Runnable onChange) {
        this(heading, description, rowTag, newLabel, onChange, () -> "Untitled", false);
    }

    /** diaryMode adds an "On This Day" recall button — only makes sense when entries are date-titled. */
    NotebookPanel(String heading, String description, String rowTag, String newLabel, Runnable onChange,
                  java.util.function.Supplier<String> defaultTitle, boolean diaryMode) {
        super(new BorderLayout(12, 12));
        this.rowTag = rowTag;
        this.onChange = onChange;
        this.defaultTitle = defaultTitle;
        this.diaryMode = diaryMode;

        JPanel top = new JPanel(new BorderLayout(10, 8));
        top.setOpaque(false);
        top.add(sectionTitle(heading, description), BorderLayout.NORTH);
        search = new PlaceholderField("SEARCH " + rowTag + "...  (Ctrl+F)");
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { refresh(search.query()); }
            @Override public void removeUpdate(DocumentEvent event) { refresh(search.query()); }
            @Override public void changedUpdate(DocumentEvent event) { refresh(search.query()); }
        });
        top.add(search, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(58);
        list.setFont(Theme.mono(13));
        list.setForeground(Theme.INK);
        list.setBackground(Theme.PANEL_RAISED);
        list.setSelectionBackground(Theme.BLUE_DEEP);
        list.setSelectionForeground(Color.WHITE);
        list.setCellRenderer(new EntryRenderer());
        list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && list.getSelectedValue() != null) {
                loadIntoEditor(list.getSelectedValue());
                flashIndex = list.getSelectedIndex();
                flashAmount = 1f;
            }
        });

        JPanel listColumn = new JPanel(new BorderLayout(0, 8));
        listColumn.setOpaque(false);
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        DarkScrollBarUI.install(listScroll);
        listColumn.add(listScroll, BorderLayout.CENTER);
        JButton newEntry = AngledButton.ghost(newLabel + "  (Ctrl+N)");
        newEntry.addActionListener(event -> startNewEntry());
        if (diaryMode) {
            JPanel southButtons = new JPanel(new java.awt.GridLayout(2, 1, 0, 6));
            southButtons.setOpaque(false);
            JButton onThisDay = AngledButton.ghost("[ ON THIS DAY ]");
            onThisDay.addActionListener(event -> jumpToOnThisDay());
            southButtons.add(onThisDay);
            southButtons.add(newEntry);
            listColumn.add(southButtons, BorderLayout.SOUTH);
        } else {
            listColumn.add(newEntry, BorderLayout.SOUTH);
        }

        JPanel editor = new JPanel(new BorderLayout(0, 8));
        editor.setOpaque(false);
        titleField.setFont(Theme.heading(17));
        titleField.setForeground(Theme.INK);
        titleField.setBackground(Theme.PANEL_RAISED);
        titleField.setCaretColor(Theme.CYAN);
        titleField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.BLUE),
                BorderFactory.createEmptyBorder(6, 2, 8, 2)));
        editor.add(titleField, BorderLayout.NORTH);

        bodyArea.setLineWrap(true);
        bodyArea.setWrapStyleWord(true);
        bodyArea.setFont(Theme.mono(14));
        bodyArea.setForeground(Theme.INK);
        bodyArea.setBackground(Theme.PANEL_RAISED);
        bodyArea.setCaretColor(Theme.CYAN);
        bodyArea.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        bodyArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { updateWordCount(); }
            @Override public void removeUpdate(DocumentEvent event) { updateWordCount(); }
            @Override public void changedUpdate(DocumentEvent event) { updateWordCount(); }
        });
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setBorder(BorderFactory.createLineBorder(Theme.EDGE));
        bodyScroll.getViewport().setBackground(Theme.PANEL_RAISED);
        DarkScrollBarUI.install(bodyScroll);
        editor.add(bodyScroll, BorderLayout.CENTER);

        JPanel editorFooter = new JPanel(new BorderLayout());
        editorFooter.setOpaque(false);
        JPanel statusBox = new JPanel(new java.awt.GridLayout(2, 1));
        statusBox.setOpaque(false);
        status = new JLabel(" ");
        status.setForeground(Theme.MUTED);
        status.setFont(Theme.mono(11));
        wordCount = new JLabel(" ");
        wordCount.setForeground(Theme.MUTED);
        wordCount.setFont(Theme.mono(10));
        statusBox.add(status);
        statusBox.add(wordCount);
        editorFooter.add(statusBox, BorderLayout.WEST);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        pinButton = AngledButton.ghost("[ PIN ]");
        pinButton.addActionListener(event -> togglePin());
        JButton export = AngledButton.ghost("[ EXPORT .TXT ]");
        export.addActionListener(event -> exportSelected());
        JButton delete = AngledButton.ghost("[ DELETE ]  (Del)");
        delete.addActionListener(event -> confirmDelete());
        JButton save = AngledButton.solid("[ SAVE ]");
        save.addActionListener(event -> commitEditor());
        actions.add(pinButton);
        actions.add(export);
        actions.add(delete);
        actions.add(save);
        editorFooter.add(actions, BorderLayout.EAST);
        editor.add(editorFooter, BorderLayout.SOUTH);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listColumn, editor);
        split.setDividerLocation(210);
        split.setDividerSize(7);
        split.setBorder(null);
        split.setOpaque(false);
        Theme.styleSplitDivider(split);
        add(split, BorderLayout.CENTER);

        installShortcuts();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        Pulse.add(flashTick);
    }

    @Override
    public void removeNotify() {
        Pulse.remove(flashTick);
        super.removeNotify();
    }

    private void installShortcuts() {
        bindKey(this, "focusSearch", KeyStroke.getKeyStroke(KeyEvent.VK_F, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                event -> search.requestFocusInWindow());
        bindKey(this, "newEntry", KeyStroke.getKeyStroke(KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                event -> startNewEntry());
        bindKey(list, "deleteSelected", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                event -> confirmDelete());
        bindKey(this, "undoDelete", KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                event -> undoDelete());
    }

    private void bindKey(JComponent component, String name, KeyStroke stroke, java.util.function.Consumer<ActionEvent> action) {
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.accept(event);
            }
        });
    }

    /**
     * Recalls a past diary entry from the same calendar day in an earlier month/year — a small nod
     * to the "you remember this" social-link callbacks the reference game builds its story around.
     * Cycles through matches on repeated clicks; entries must have kept their date-formatted title.
     */
    private void jumpToOnThisDay() {
        String todayMarker = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("MMMM d"));
        String todayFull = defaultTitle.get();
        java.util.List<Entry> matches = entries().stream()
                .filter(e -> e.title().contains(todayMarker) && !e.title().equals(todayFull))
                .sorted(java.util.Comparator.comparingLong(Entry::updatedAtMillis).reversed())
                .toList();
        if (matches.isEmpty()) {
            status.setText("Nothing else from this day yet");
            return;
        }
        onThisDayCursor = onThisDayCursor % matches.size();
        Entry target = matches.get(onThisDayCursor);
        onThisDayCursor++;
        search.setValue("");
        refresh("");
        list.setSelectedValue(target, true);
        list.ensureIndexIsVisible(list.getSelectedIndex());
        status.setText("On this day (" + (onThisDayCursor) + " of " + matches.size() + ")");
    }

    private void startNewEntry() {
        list.clearSelection();
        titleField.setText(defaultTitle.get());
        bodyArea.setText("");
        pinButton.setText("[ PIN ]");
        updateWordCount();
        titleField.requestFocusInWindow();
        titleField.selectAll();
    }

    private void loadIntoEditor(Entry entry) {
        titleField.setText(entry.title());
        bodyArea.setText(entry.body());
        bodyArea.setCaretPosition(0);
        pinButton.setText(entry.pinned() ? "[ PINNED ]" : "[ PIN ]");
        updateWordCount();
    }

    private void updateWordCount() {
        String text = bodyArea.getText().trim();
        int words = text.isEmpty() ? 0 : text.split("\\s+").length;
        wordCount.setText(words + " words, " + text.length() + " chars");
    }

    void setEntries(java.util.List<Entry> entries) {
        source.clear();
        entries.forEach(source::addElement);
        refresh(search.query());
    }

    java.util.List<Entry> entries() {
        java.util.List<Entry> result = new java.util.ArrayList<>();
        for (int i = 0; i < source.size(); i++) {
            result.add(source.get(i));
        }
        return result;
    }

    private void refresh(String query) {
        Entry selected = list.getSelectedValue();
        String needle = query.toLowerCase();
        view.clear();
        for (int i = 0; i < source.size(); i++) {
            Entry entry = source.get(i);
            if (needle.isEmpty() || entry.title().toLowerCase().contains(needle)
                    || entry.body().toLowerCase().contains(needle)) {
                view.addElement(entry);
            }
        }
        if (selected != null) {
            list.setSelectedValue(selected, true);
        }
    }

    private void resort() {
        java.util.List<Entry> ordered = entries();
        ordered.sort(Storage.ENTRY_ORDER);
        source.clear();
        ordered.forEach(source::addElement);
    }

    private void commitEditor() {
        String title = titleField.getText().trim();
        if (title.isEmpty()) {
            title = "Untitled";
        }
        String body = bodyArea.getText();
        Entry selected = list.getSelectedValue();
        boolean pinned = selected != null && selected.pinned();
        Entry saved = new Entry(title, body, System.currentTimeMillis(), pinned);
        if (selected != null) {
            source.set(source.indexOf(selected), saved);
        } else {
            source.addElement(saved);
        }
        resort();
        refresh(search.query());
        list.setSelectedValue(saved, true);
        status.setText("Saved just now");
        onChange.run();
    }

    private void togglePin() {
        Entry selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        Entry updated = selected.withPinned(!selected.pinned());
        source.set(source.indexOf(selected), updated);
        resort();
        refresh(search.query());
        list.setSelectedValue(updated, true);
        pinButton.setText(updated.pinned() ? "[ PINNED ]" : "[ PIN ]");
        status.setText(updated.pinned() ? "Pinned" : "Unpinned");
        onChange.run();
    }

    private void exportSelected() {
        Entry selected = list.getSelectedValue();
        if (selected == null) {
            status.setText("Select an entry to export first");
            return;
        }
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Export \"" + selected.title() + "\"");
        String safeName = selected.title().replaceAll("[\\\\/:*?\"<>|]", "_");
        chooser.setSelectedFile(new java.io.File(safeName + ".txt"));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.io.File target = chooser.getSelectedFile();
        String content = selected.title() + "\n\n" + selected.body() + "\n";
        try {
            java.nio.file.Files.writeString(target.toPath(), content);
            status.setText("Exported to " + target.getName());
        } catch (java.io.IOException e) {
            JOptionPane.showMessageDialog(this, "Couldn't write that file: " + e.getMessage(),
                    "Export failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void confirmDelete() {
        Entry selected = list.getSelectedValue();
        if (selected == null) {
            return;
        }
        int result = JOptionPane.showConfirmDialog(this,
                "Delete \"" + selected.title() + "\"? This can't be undone.",
                "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        lastDeleted = selected;
        source.removeElement(selected);
        refresh(search.query());
        titleField.setText("");
        bodyArea.setText("");
        updateWordCount();
        status.setText("Deleted — Ctrl+Z to undo");
        onChange.run();
    }

    private void undoDelete() {
        if (lastDeleted == null) {
            return;
        }
        Entry restored = lastDeleted;
        lastDeleted = null;
        source.addElement(restored);
        resort();
        refresh(search.query());
        list.setSelectedValue(restored, true);
        status.setText("Restored");
        onChange.run();
    }

    /** Selects the entry with this exact title, if present, and returns whether it found one. Used by global search. */
    boolean selectByTitle(String title) {
        if (!search.query().isEmpty()) {
            search.setValue("");
            refresh("");
        }
        for (int i = 0; i < view.size(); i++) {
            if (view.get(i).title().equals(title)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return true;
            }
        }
        return false;
    }

    private JLabel sectionTitle(String title, String description) {
        JLabel label = new JLabel("<html><font color='#61dfeb'><b>" + title + "</b></font><br><font color='#7a9bba'>"
                + description + "</font></html>");
        label.setFont(Theme.mono(12));
        return label;
    }


    private class EntryRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> jlist, Object value, int index,
                                                        boolean selected, boolean focused) {
            Entry entry = (Entry) value;
            String pin = entry.pinned() ? "★ " : "";
            JLabel label = (JLabel) super.getListCellRendererComponent(jlist,
                    "<html><font color='#7a9bba'>" + rowTag + " " + String.format("%02d", index + 1)
                            + "  ·  " + RelativeTime.since(entry.updatedAtMillis())
                            + "</font><br><b>" + pin + escape(entry.title()) + "</b></html>", index, selected, focused);
            label.setForeground(Theme.INK);
            Color base = selected ? Theme.BLUE_DEEP : Theme.PANEL_RAISED;
            if (index == flashIndex && flashAmount > 0f) {
                base = Theme.blend(base, Theme.CYAN, flashAmount * 0.55f);
            }
            label.setBackground(base);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.EDGE),
                    BorderFactory.createEmptyBorder(7, 7, 7, 7)));
            return label;
        }

        private String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
