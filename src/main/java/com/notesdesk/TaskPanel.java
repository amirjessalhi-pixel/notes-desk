package com.notesdesk;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.GeneralPath;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;

/**
 * The task queue: a Persona-style angled card with a slanted priority tab per row. Active tasks sort
 * most-recently-touched first; completing one sinks it below the active group instead of leaving it
 * in place.
 */
class TaskPanel extends AngledCard {
    private final DefaultListModel<Task> model = new DefaultListModel<>();
    private final JList<Task> list = new JList<>(model);
    private final JTextField input = new JTextField();
    private final JComboBox<Priority> priorityBox = new JComboBox<>(Priority.values());
    private final JButton dueButton = AngledButton.ghost("[ + DUE ]");
    private final Runnable onChange;
    private Task lastDeleted;
    private Long pendingDueDate;
    private int flashIndex = -1;
    private float flashAmount = 0f;
    private final Runnable flashTick = () -> {
        if (flashAmount > 0f) {
            flashAmount = Math.max(0f, flashAmount - 0.09f);
            list.repaint();
        }
    };

    TaskPanel(Runnable onChange) {
        super(new BorderLayout(12, 12));
        this.onChange = onChange;

        JLabel heading = new JLabel("<html><font color='#61dfeb'><b>TASKS</b></font><br>"
                + "<font color='#7a9bba'>Keep up to date on work.</font></html>");
        heading.setFont(Theme.mono(12));

        JPanel addRow = new JPanel(new BorderLayout(8, 8));
        addRow.setOpaque(false);
        input.setFont(Theme.mono(13));
        input.setForeground(Theme.INK);
        input.setBackground(Theme.PANEL_RAISED);
        input.setCaretColor(Theme.CYAN);
        input.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Theme.BLUE),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)));
        input.setToolTipText("What needs to get done? (Enter to add)");
        input.addActionListener(event -> addTask());
        addRow.add(input, BorderLayout.CENTER);
        priorityBox.setSelectedItem(Priority.NORMAL);
        priorityBox.setFont(Theme.monoBold(11));
        priorityBox.setForeground(Theme.CYAN);
        priorityBox.setBackground(Theme.PANEL_RAISED);
        priorityBox.setBorder(BorderFactory.createLineBorder(Theme.BLUE));
        priorityBox.setUI(new FlatComboBoxUI());
        // BasicComboBoxUI sizes itself before the custom font/UI apply, which truncated the label to "...".
        priorityBox.setPreferredSize(new java.awt.Dimension(118, 34));
        addRow.add(priorityBox, BorderLayout.WEST);
        dueButton.addActionListener(event -> showDatePopup(dueButton, epochDay -> {
            pendingDueDate = epochDay;
            dueButton.setText(dueButtonLabel(epochDay));
        }));
        JButton addButton = AngledButton.solid("[ + ADD ]");
        addButton.addActionListener(event -> addTask());
        JPanel eastButtons = new JPanel(new java.awt.GridLayout(1, 2, 6, 0));
        eastButtons.setOpaque(false);
        eastButtons.add(dueButton);
        eastButtons.add(addButton);
        addRow.add(eastButtons, BorderLayout.EAST);

        JPanel north = new JPanel(new BorderLayout(0, 10));
        north.setOpaque(false);
        north.add(heading, BorderLayout.NORTH);
        north.add(addRow, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && list.getSelectedIndex() >= 0) {
                flashIndex = list.getSelectedIndex();
                flashAmount = 1f;
            }
        });
        list.setCellRenderer(new TaskRenderer());
        list.setBackground(Theme.PANEL_RAISED);
        list.setFixedCellHeight(48);
        list.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setHorizontalScrollBarPolicy(javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(Theme.EDGE));
        scroll.getViewport().setBackground(Theme.PANEL_RAISED);
        DarkScrollBarUI.install(scroll);
        add(scroll, BorderLayout.CENTER);

        JButton complete = AngledButton.ghost("[ TOGGLE STATUS ]");
        complete.addActionListener(event -> completeTask());
        JButton setDue = AngledButton.ghost("[ SET DUE ]");
        setDue.addActionListener(event -> showDatePopup(setDue, this::setSelectedDueDate));
        JButton remove = AngledButton.ghost("[ PURGE ]  (Del)");
        remove.addActionListener(event -> confirmRemove());
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(complete);
        actions.add(setDue);
        actions.add(remove);
        add(actions, BorderLayout.SOUTH);

        bindKey(list, "deleteSelected", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), event -> confirmRemove());
        bindKey(this, "focusInput", KeyStroke.getKeyStroke(KeyEvent.VK_N, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                event -> input.requestFocusInWindow());
        bindKey(this, "undoDelete", KeyStroke.getKeyStroke(KeyEvent.VK_Z, java.awt.event.InputEvent.CTRL_DOWN_MASK),
                event -> undoDelete());
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

    private void bindKey(JComponent component, String name, KeyStroke stroke, java.util.function.Consumer<ActionEvent> action) {
        component.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, name);
        component.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent event) {
                action.accept(event);
            }
        });
    }

    void setTasks(java.util.List<Task> tasks) {
        model.clear();
        sorted(tasks).forEach(model::addElement);
    }

    java.util.List<Task> tasks() {
        java.util.List<Task> result = new java.util.ArrayList<>();
        for (int i = 0; i < model.size(); i++) {
            result.add(model.get(i));
        }
        return result;
    }

    /**
     * Not-done tasks first, then within that group whichever has the soonest due date (tasks with no
     * due date sort after all dated ones), then most-recently-touched first. Done tasks sink to the
     * bottom, most-recently-touched first.
     */
    private java.util.List<Task> sorted(java.util.List<Task> tasks) {
        java.util.List<Task> copy = new java.util.ArrayList<>(tasks);
        copy.sort(java.util.Comparator.comparing(Task::done)
                .thenComparing(t -> t.dueDateEpochDay() == null ? Long.MAX_VALUE : t.dueDateEpochDay())
                .thenComparing(java.util.Comparator.comparingLong(Task::updatedAtMillis).reversed()));
        return copy;
    }

    private void resort() {
        java.util.List<Task> current = tasks();
        model.clear();
        sorted(current).forEach(model::addElement);
    }

    private void addTask() {
        String text = input.getText().trim();
        if (!text.isEmpty()) {
            Task task = new Task(text, (Priority) priorityBox.getSelectedItem(), false, System.currentTimeMillis(),
                    pendingDueDate);
            model.addElement(task);
            input.setText("");
            pendingDueDate = null;
            dueButton.setText(dueButtonLabel(null));
            resort();
            list.setSelectedValue(task, true);
            onChange.run();
        }
    }

    private void setSelectedDueDate(Long epochDay) {
        int index = list.getSelectedIndex();
        if (index >= 0) {
            Task updated = model.get(index).withDueDate(epochDay);
            model.set(index, updated);
            resort();
            list.setSelectedValue(updated, true);
            onChange.run();
        }
    }

    private String dueButtonLabel(Long epochDay) {
        return epochDay == null ? "[ + DUE ]" : "[ DUE: " + java.time.LocalDate.ofEpochDay(epochDay) + " ]";
    }

    /** A small quick-pick menu instead of a full calendar widget — fast to use, nothing to build a date picker for. */
    private void showDatePopup(java.awt.Component invoker, java.util.function.Consumer<Long> onPick) {
        javax.swing.JPopupMenu menu = new javax.swing.JPopupMenu();
        java.time.LocalDate today = java.time.LocalDate.now();
        menu.add(dateItem("No due date", () -> onPick.accept(null)));
        menu.addSeparator();
        menu.add(dateItem("Today", () -> onPick.accept(today.toEpochDay())));
        menu.add(dateItem("Tomorrow", () -> onPick.accept(today.plusDays(1).toEpochDay())));
        menu.add(dateItem("In 3 days", () -> onPick.accept(today.plusDays(3).toEpochDay())));
        menu.add(dateItem("Next week", () -> onPick.accept(today.plusWeeks(1).toEpochDay())));
        menu.addSeparator();
        menu.add(dateItem("Custom (yyyy-mm-dd)...", () -> {
            String value = JOptionPane.showInputDialog(this, "Due date (yyyy-mm-dd):",
                    today.toString());
            if (value != null && !value.isBlank()) {
                try {
                    onPick.accept(java.time.LocalDate.parse(value.trim()).toEpochDay());
                } catch (java.time.format.DateTimeParseException ignored) {
                    JOptionPane.showMessageDialog(this, "Couldn't read that date — use yyyy-mm-dd.",
                            "Invalid date", JOptionPane.WARNING_MESSAGE);
                }
            }
        }));
        menu.show(invoker, 0, invoker.getHeight());
    }

    private javax.swing.JMenuItem dateItem(String label, Runnable action) {
        javax.swing.JMenuItem item = new javax.swing.JMenuItem(label);
        item.addActionListener(event -> action.run());
        return item;
    }

    private void completeTask() {
        int index = list.getSelectedIndex();
        if (index >= 0) {
            Task task = model.get(index).withDone(!model.get(index).done());
            model.set(index, task);
            resort();
            list.setSelectedValue(task, true);
            onChange.run();
        }
    }

    private void confirmRemove() {
        int index = list.getSelectedIndex();
        if (index < 0) {
            return;
        }
        Task task = model.get(index);
        int result = JOptionPane.showConfirmDialog(this,
                "Delete \"" + task.text() + "\"? This can't be undone.",
                "Confirm delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) {
            return;
        }
        lastDeleted = task;
        model.remove(index);
        onChange.run();
    }

    private void undoDelete() {
        if (lastDeleted == null) {
            return;
        }
        Task restored = lastDeleted;
        lastDeleted = null;
        model.addElement(restored);
        resort();
        list.setSelectedValue(restored, true);
        onChange.run();
    }

    /** Selects the task with this exact text, if present, and returns whether it found one. Used by global search. */
    boolean selectByText(String text) {
        for (int i = 0; i < model.size(); i++) {
            if (model.get(i).text().equals(text)) {
                list.setSelectedIndex(i);
                list.ensureIndexIsVisible(i);
                return true;
            }
        }
        return false;
    }

    /** Paints each row as a slanted tab (priority color) bleeding into the row text, P3R list style. */
    private class TaskRenderer extends JPanel implements ListCellRenderer<Task> {
        private String text = "";
        private String age = "";
        private String due = null;
        private Color dueColor = Theme.MUTED;
        private Color tabColor = Theme.NORMAL;
        private boolean selected;
        private boolean done;
        private int index;

        private TaskRenderer() {
            setOpaque(false);
            setPreferredSize(new java.awt.Dimension(10, 46));
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Task> list, Task task, int index,
                                                        boolean isSelected, boolean cellHasFocus) {
            this.text = task.text();
            this.age = RelativeTime.since(task.updatedAtMillis());
            this.done = task.done();
            this.selected = isSelected;
            this.index = index;
            this.tabColor = switch (task.priority()) {
                case HIGH -> Theme.HIGH;
                case NORMAL -> Theme.NORMAL;
                case LOW -> Theme.LOW;
            };
            if (task.dueDateEpochDay() == null) {
                this.due = null;
            } else {
                java.time.LocalDate dueDate = java.time.LocalDate.ofEpochDay(task.dueDateEpochDay());
                long daysAway = java.time.LocalDate.now().until(dueDate, java.time.temporal.ChronoUnit.DAYS);
                this.due = "DUE " + dueDate;
                if (done) {
                    this.dueColor = Theme.MUTED;
                } else if (daysAway < 0) {
                    this.due = "OVERDUE " + dueDate;
                    this.dueColor = Theme.HIGH;
                } else if (daysAway == 0) {
                    this.due = "DUE TODAY";
                    this.dueColor = Theme.NORMAL;
                } else if (daysAway <= 2) {
                    this.dueColor = Theme.NORMAL;
                } else {
                    this.dueColor = Theme.CYAN;
                }
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g2 = (Graphics2D) graphics.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            Color base = selected ? Theme.BLUE_DEEP : Theme.PANEL_RAISED;
            if (index == flashIndex && flashAmount > 0f) {
                base = Theme.blend(base, Theme.CYAN, flashAmount * 0.55f);
            }
            g2.setColor(base);
            g2.fillRect(0, 0, w, h);

            GeneralPath tab = new GeneralPath();
            tab.moveTo(0, 0);
            tab.lineTo(86, 0);
            tab.lineTo(70, h);
            tab.lineTo(0, h);
            tab.closePath();
            g2.setColor(done ? new Color(tabColor.getRed(), tabColor.getGreen(), tabColor.getBlue(), 110) : tabColor);
            g2.fill(tab);

            g2.setColor(new Color(0, 0, 0, 130));
            g2.setFont(Theme.monoBold(10));
            g2.drawString("[" + (index + 1) + "]", 10, h / 2 + 4);

            g2.setColor(done ? Theme.MUTED : Theme.INK);
            g2.setFont(done ? Theme.mono(13) : Theme.monoBold(13));
            int textY = h / 2 + 2;
            g2.drawString(text, 98, textY);
            if (done) {
                int textWidth = g2.getFontMetrics().stringWidth(text);
                g2.drawLine(98, textY - 5, 98 + textWidth, textY - 5);
            }

            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.mono(10));
            int ageWidth = g2.getFontMetrics().stringWidth(age);
            g2.drawString(age, w - ageWidth - 12, h - 8);

            if (due != null) {
                g2.setColor(dueColor);
                g2.setFont(Theme.monoBold(10));
                int dueWidth = g2.getFontMetrics().stringWidth(due);
                g2.drawString(due, w - dueWidth - 12, 16);
            }
            g2.dispose();
        }
    }
}
