package com.notesdesk;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** A Ctrl+K command-palette style search across evidence notes, field notes, and tasks at once. */
class QuickSearchDialog extends JDialog {
    record Hit(String category, String title, String preview) { }

    private final DefaultListModel<Hit> results = new DefaultListModel<>();
    private final JList<Hit> list = new JList<>(results);
    private final PlaceholderField field = new PlaceholderField("Search everything...");
    private final Supplier<List<Hit>> allHits;
    private final Consumer<Hit> onSelect;

    QuickSearchDialog(JFrame owner, Supplier<List<Hit>> allHits, Consumer<Hit> onSelect) {
        super(owner, "Quick Search", true);
        this.allHits = allHits;
        this.onSelect = onSelect;
        setUndecorated(true);
        getRootPane().setBorder(BorderFactory.createLineBorder(Theme.CYAN, 2));

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(Theme.PANEL);
        field.setFont(Theme.heading(16));
        field.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        root.add(field, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Theme.PANEL_RAISED);
        list.setFixedCellHeight(46);
        list.setCellRenderer(new HitRenderer());
        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 1) {
                    choose();
                }
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setPreferredSize(new Dimension(560, 340));
        DarkScrollBarUI.install(scroll);
        root.add(scroll, BorderLayout.CENTER);

        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter(); }
            @Override public void removeUpdate(DocumentEvent e) { filter(); }
            @Override public void changedUpdate(DocumentEvent e) { filter(); }
        });
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_DOWN) {
                    list.requestFocusInWindow();
                    if (!results.isEmpty()) {
                        list.setSelectedIndex(Math.min(list.getSelectedIndex() + 1, results.size() - 1));
                    }
                } else if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    choose();
                } else if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                }
            }
        });
        list.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_ENTER) {
                    choose();
                } else if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    dispose();
                }
            }
        });

        setContentPane(root);
        setSize(560, 400);
        if (owner != null) {
            setLocationRelativeTo(owner);
        }
    }

    @Override
    public void setVisible(boolean visible) {
        if (visible) {
            field.setText("");
            filter();
            SwingUtilities.invokeLater(field::requestFocusInWindow);
        }
        super.setVisible(visible);
    }

    private void filter() {
        String needle = field.query().toLowerCase();
        results.clear();
        for (Hit hit : allHits.get()) {
            if (needle.isEmpty() || hit.title().toLowerCase().contains(needle)
                    || hit.preview().toLowerCase().contains(needle)) {
                results.addElement(hit);
            }
        }
        if (!results.isEmpty()) {
            list.setSelectedIndex(0);
        }
    }

    private void choose() {
        Hit selected = list.getSelectedValue();
        if (selected != null) {
            dispose();
            onSelect.accept(selected);
        }
    }

    private static class HitRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> jlist, Object value, int index,
                                                        boolean selected, boolean focused) {
            Hit hit = (Hit) value;
            JLabel label = (JLabel) super.getListCellRendererComponent(jlist,
                    "<html><font color='#61dfeb'>[" + hit.category() + "]</font> <b>" + escape(hit.title())
                            + "</b><br><font color='#7a9bba'>" + escape(truncate(hit.preview(), 70)) + "</font></html>",
                    index, selected, focused);
            label.setFont(Theme.mono(12));
            label.setForeground(Theme.INK);
            label.setBackground(selected ? Theme.BLUE_DEEP : Theme.PANEL_RAISED);
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, Theme.EDGE),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));
            return label;
        }

        private String truncate(String text, int max) {
            String flat = text.replace("\n", " ");
            return flat.length() > max ? flat.substring(0, max) + "..." : flat;
        }

        private String escape(String text) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }
}
