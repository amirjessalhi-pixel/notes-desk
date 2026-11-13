package com.notesdesk;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Formats a timestamp as "just now" / "5m ago" / "3d ago" / an absolute date once it's old enough. */
final class RelativeTime {
    private static final DateTimeFormatter ABSOLUTE = DateTimeFormatter.ofPattern("MMM d, HH:mm");

    private RelativeTime() { }

    static String since(long epochMillis) {
        long seconds = Math.max(0, (System.currentTimeMillis() - epochMillis) / 1000);
        if (seconds < 10) {
            return "just now";
        }
        if (seconds < 60) {
            return seconds + "s ago";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = minutes / 60;
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = hours / 24;
        if (days < 7) {
            return days + "d ago";
        }
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(ABSOLUTE);
    }
}
