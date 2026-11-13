package com.notesdesk;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Dependency-free self-check for the save format. Run with assertions on: {@code java -ea}. */
public class StorageTest {
    public static void main(String[] args) throws Exception {
        // escape/unescape round-trips the characters the line format uses as delimiters
        for (String s : new String[] {"", "plain", "a|b", "line1\nline2", "C:\\path\\n-not-a-newline", "\\p"}) {
            assert Storage.unescape(Storage.escape(s)).equals(s) : "round-trip failed for: " + s;
            assert !Storage.escape(s).contains("\n") : "escaped value must stay on one line";
        }

        Path dir = Files.createTempDirectory("notes-desk-test");
        Path file = dir.resolve("save.txt");

        // missing file loads empty: no seeded entries
        Storage.Loaded empty = Storage.load(file);
        assert empty.notes().isEmpty() && empty.tasks().isEmpty() && empty.fieldNotes().isEmpty();

        // save + load round-trip, pinned entries sorted first
        List<Entry> notes = List.of(
                new Entry("older", "body | with pipe\nand newline", 1_000L, false),
                new Entry("pinned", "x", 500L, true));
        List<Task> tasks = List.of(new Task("ship it", Priority.HIGH, false, 2_000L, 20_000L));
        assert Storage.save(file, notes, tasks, List.of(), 19_000L) == Storage.SaveOutcome.SAVED;
        Storage.Loaded loaded = Storage.load(file);
        assert loaded.notes().get(0).title().equals("pinned");
        assert loaded.notes().get(1).body().equals("body | with pipe\nand newline");
        assert loaded.tasks().equals(tasks);
        assert loaded.firstUseEpochDay() == 19_000L;

        // one corrupt line is skipped; the rest of the file survives
        Files.writeString(file, "N|ok|fine|1000|false\nT|bad|NOT_A_PRIORITY|false\nN|truncated\n");
        Storage.Loaded partial = Storage.load(file);
        assert partial.notes().size() == 1 && partial.tasks().isEmpty();

        // a file changed on disk by another machine is not overwritten
        Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(42_000L));
        assert Storage.save(file, notes, tasks, List.of(), 1L) == Storage.SaveOutcome.CONFLICT;

        System.out.println("StorageTest: all checks passed");
    }
}
