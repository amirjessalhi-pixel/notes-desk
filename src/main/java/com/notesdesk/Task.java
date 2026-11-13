package com.notesdesk;

/** dueDateEpochDay is a day count from epoch (LocalDate.toEpochDay()), or null when no due date is set. */
record Task(String text, Priority priority, boolean done, long updatedAtMillis, Long dueDateEpochDay) {
    Task(String text, Priority priority, boolean done, long updatedAtMillis) {
        this(text, priority, done, updatedAtMillis, null);
    }

    Task withDone(boolean value) {
        return new Task(text, priority, value, System.currentTimeMillis(), dueDateEpochDay);
    }

    Task withDueDate(Long epochDay) {
        return new Task(text, priority, done, System.currentTimeMillis(), epochDay);
    }
}
