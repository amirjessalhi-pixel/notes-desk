package com.notesdesk;

import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.Timer;

/** One shared ~33fps ticker every animated component subscribes to, instead of each running its own Timer. */
final class Pulse {
    private static final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    private static final Timer timer = new Timer(30, event -> {
        for (Runnable listener : listeners) {
            listener.run();
        }
    });

    static {
        timer.start();
    }

    private Pulse() { }

    static void add(Runnable listener) {
        listeners.addIfAbsent(listener);
    }

    static void remove(Runnable listener) {
        listeners.remove(listener);
    }
}
