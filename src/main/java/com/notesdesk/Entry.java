package com.notesdesk;

/** A titled, editable block of text. Used both for personal notes and for the field-notes glossary. */
record Entry(String title, String body, long updatedAtMillis, boolean pinned) {
    Entry withPinned(boolean value) {
        return new Entry(title, body, updatedAtMillis, value);
    }

    @Override
    public String toString() {
        return title;
    }
}
