package com.notesdesk;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Flat pipe-delimited save file, one record per line: N=log entry, T=task, F=field note, M=metadata.
 *
 * <p>The file lives inside a synced folder when one can be found, so the same save file is shared
 * across machines instead of being pinned to one computer. Because two computers can be signed into
 * <em>different</em> cloud accounts, the location is resolved per machine (see {@link #resolveSaveFile()})
 * and can be pointed anywhere via {@link #setConfiguredDirectory(Path)}; it does not have to be the
 * same provider on both ends, just a folder that both machines see the same copy of.
 */
final class Storage {
    private static final String FILE_NAME = "notes-desk-save.txt";
    private static final Path HOME = Path.of(System.getProperty("user.home"));
    private static final Path DEFAULT_DIR = HOME.resolve(".notes-desk");
    private static final Path LOCATION_CONFIG = HOME.resolve(".notes-desk-location");

    static final Path SAVE_FILE = resolveSaveFile();

    private static volatile FileTime lastKnownWrite;
    /** Set when an existing save file couldn't be read, so we never overwrite it with an empty state. */
    private static volatile boolean readFailed;

    private Storage() { }

    record Loaded(List<Entry> notes, List<Task> tasks, List<Entry> fieldNotes, long firstUseEpochDay) { }

    enum SaveOutcome { SAVED, CONFLICT, ERROR }

    // ---------------------------------------------------------------- location resolution

    /**
     * Search order: the {@code notesdesk.dir} system property, the {@code NOTES_DESK_SAVE_DIR} env var,
     * a folder the user picked in-app (persisted in a small local pointer file, never itself synced,
     * since each machine may mount the same shared folder at a different local path), an auto-detected
     * OneDrive / Google Drive / Dropbox root, and finally {@code ~/.notes-desk}.
     */
    private static Path resolveSaveFile() {
        Path dir = DEFAULT_DIR;
        String property = System.getProperty("notesdesk.dir");
        String env = System.getenv("NOTES_DESK_SAVE_DIR");
        if (property != null && !property.isBlank()) {
            dir = Path.of(property);
        } else if (env != null && !env.isBlank()) {
            dir = Path.of(env);
        } else {
            Optional<Path> configured = readConfiguredDirectory();
            if (configured.isPresent()) {
                dir = configured.get();
            } else {
                Path detected = autoDetectSyncFolder();
                if (detected != null) {
                    dir = detected.resolve("NotesDesk");
                }
            }
        }
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            dir = DEFAULT_DIR;
        }
        return dir.resolve(FILE_NAME);
    }

    private static Path autoDetectSyncFolder() {
        for (String var : new String[] {"OneDriveCommercial", "OneDrive", "OneDriveConsumer"}) {
            String value = System.getenv(var);
            if (value != null && Files.isDirectory(Path.of(value))) {
                return Path.of(value);
            }
        }
        for (String name : new String[] {"OneDrive", "Google Drive", "GoogleDrive", "Dropbox"}) {
            Path guess = HOME.resolve(name);
            if (Files.isDirectory(guess)) {
                return guess;
            }
        }
        return null;
    }

    private static Optional<Path> readConfiguredDirectory() {
        try {
            if (!Files.exists(LOCATION_CONFIG)) {
                return Optional.empty();
            }
            String value = Files.readString(LOCATION_CONFIG).trim();
            if (value.isEmpty()) {
                return Optional.empty();
            }
            Path dir = Path.of(value);
            return Files.isDirectory(dir) || !Files.exists(dir) ? Optional.of(dir) : Optional.empty();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Points this machine at a new shared folder. If that folder already holds a save file, it is
     * left alone (so an existing shared copy is never clobbered); otherwise the current save file is
     * copied in. Takes effect after the app restarts, since {@link #SAVE_FILE} is resolved once.
     */
    static boolean setConfiguredDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            Files.writeString(LOCATION_CONFIG, directory.toAbsolutePath().toString());
            Path newTarget = directory.resolve(FILE_NAME);
            if (!Files.exists(newTarget) && Files.exists(SAVE_FILE)) {
                Files.copy(SAVE_FILE, newTarget, StandardCopyOption.COPY_ATTRIBUTES);
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Writes a timestamped copy of the current save file into a backups/ folder next to it. */
    static Optional<Path> backupNow() {
        if (!Files.exists(SAVE_FILE)) {
            return Optional.empty();
        }
        try {
            String stamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path backupDir = SAVE_FILE.resolveSibling("backups");
            Files.createDirectories(backupDir);
            Path backup = backupDir.resolve("notes-desk-" + stamp + ".txt");
            Files.copy(SAVE_FILE, backup, StandardCopyOption.COPY_ATTRIBUTES);
            return Optional.of(backup);
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- load / save

    static Loaded load() {
        return load(SAVE_FILE);
    }

    static Loaded load(Path file) {
        long today = java.time.LocalDate.now().toEpochDay();
        readFailed = false;
        lastKnownWrite = null;
        List<Entry> notes = new ArrayList<>();
        List<Task> tasks = new ArrayList<>();
        List<Entry> fieldNotes = new ArrayList<>();
        if (!Files.exists(file)) {
            return new Loaded(notes, tasks, fieldNotes, today);
        }
        long fallbackNow = System.currentTimeMillis();
        long firstUseEpochDay = today;
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    String[] parts = line.split("\\|", -1);
                    switch (parts[0]) {
                        case "N" -> notes.add(new Entry(unescape(parts[1]), unescape(parts[2]),
                                timestampOr(parts, 3, fallbackNow), boolOr(parts, 4)));
                        case "F" -> fieldNotes.add(new Entry(unescape(parts[1]), unescape(parts[2]),
                                timestampOr(parts, 3, fallbackNow), boolOr(parts, 4)));
                        case "T" -> tasks.add(new Task(unescape(parts[1]), Priority.valueOf(parts[2]),
                                Boolean.parseBoolean(parts[3]), timestampOr(parts, 4, fallbackNow), dueDateOr(parts, 5)));
                        case "M" -> {
                            if (parts[1].equals("firstUseEpochDay")) {
                                firstUseEpochDay = timestampOr(parts, 2, today);
                            }
                        }
                        default -> { }
                    }
                } catch (RuntimeException malformed) {
                    // Skip one bad line (hand-edited or truncated by a sync client) instead of
                    // discarding the whole file.
                }
            }
            lastKnownWrite = Files.getLastModifiedTime(file);
        } catch (IOException e) {
            readFailed = true;
        }
        notes.sort(ENTRY_ORDER);
        fieldNotes.sort(ENTRY_ORDER);
        return new Loaded(notes, tasks, fieldNotes, firstUseEpochDay);
    }

    /** Pinned entries first, then most-recently-touched first within each group. */
    static final java.util.Comparator<Entry> ENTRY_ORDER = java.util.Comparator
            .comparing(Entry::pinned).reversed()
            .thenComparing(java.util.Comparator.comparingLong(Entry::updatedAtMillis).reversed());

    /** A stored value of 0 (or missing/unparseable) is treated as absent. */
    private static long timestampOr(String[] parts, int index, long fallback) {
        if (parts.length > index) {
            try {
                long value = Long.parseLong(parts[index]);
                if (value > 0) {
                    return value;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return fallback;
    }

    private static boolean boolOr(String[] parts, int index) {
        return parts.length > index && Boolean.parseBoolean(parts[index]);
    }

    private static Long dueDateOr(String[] parts, int index) {
        if (parts.length <= index || parts[index].isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(parts[index]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static SaveOutcome save(List<Entry> notes, List<Task> tasks, List<Entry> fieldNotes, long firstUseEpochDay) {
        return save(SAVE_FILE, notes, tasks, fieldNotes, firstUseEpochDay);
    }

    /**
     * Writes the save file atomically (temp file + move), unless the file changed on disk since our
     * last read (e.g. the other computer's sync just landed a newer copy). In that case the write is
     * skipped and {@link SaveOutcome#CONFLICT} is reported rather than silently clobbering newer data.
     */
    static SaveOutcome save(Path file, List<Entry> notes, List<Task> tasks, List<Entry> fieldNotes,
                            long firstUseEpochDay) {
        if (readFailed) {
            return SaveOutcome.ERROR;
        }
        try {
            if (lastKnownWrite != null && Files.exists(file)
                    && !Files.getLastModifiedTime(file).equals(lastKnownWrite)) {
                return SaveOutcome.CONFLICT;
            }
        } catch (IOException ignored) {
            // fall through and attempt the write anyway
        }

        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary)) {
            for (Entry note : notes) {
                writer.write("N|" + escape(note.title()) + "|" + escape(note.body()) + "|" + note.updatedAtMillis()
                        + "|" + note.pinned());
                writer.newLine();
            }
            for (Task task : tasks) {
                writer.write("T|" + escape(task.text()) + "|" + task.priority() + "|" + task.done()
                        + "|" + task.updatedAtMillis() + "|" + (task.dueDateEpochDay() == null ? "" : task.dueDateEpochDay()));
                writer.newLine();
            }
            for (Entry note : fieldNotes) {
                writer.write("F|" + escape(note.title()) + "|" + escape(note.body()) + "|" + note.updatedAtMillis()
                        + "|" + note.pinned());
                writer.newLine();
            }
            writer.write("M|firstUseEpochDay|" + firstUseEpochDay);
            writer.newLine();
        } catch (IOException e) {
            return SaveOutcome.ERROR;
        }
        try {
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            lastKnownWrite = Files.getLastModifiedTime(file);
            return SaveOutcome.SAVED;
        } catch (IOException e) {
            return SaveOutcome.ERROR;
        }
    }

    /** Escapes backslash, pipe, and newlines so any text round-trips through one pipe-delimited line. */
    static String escape(String value) {
        return value.replace("\\", "\\\\").replace("|", "\\p").replace("\r", "").replace("\n", "\\n");
    }

    static String unescape(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\' && i + 1 < value.length()) {
                char next = value.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 'p' -> out.append('|');
                    case '\\' -> out.append('\\');
                    default -> out.append(c).append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
