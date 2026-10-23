package lightmods;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Простой журнал: хранит историю и уведомляет подписчика (панель «Журнал»). */
public final class Log {
    private static final java.util.ArrayDeque<String> HISTORY = new java.util.ArrayDeque<>();
    private static volatile Consumer<String> sink;
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Log() {
    }

    public static void i(String msg) {
        String line = "[" + LocalTime.now().format(F) + "] " + msg;
        synchronized (HISTORY) {
            HISTORY.addLast(line);
            if (HISTORY.size() > 3000) HISTORY.removeFirst();
        }
        Consumer<String> s = sink;
        if (s != null) s.accept(line);
        if (System.console() != null) System.out.println(line);
    }

    public static List<String> history() {
        synchronized (HISTORY) {
            return new ArrayList<>(HISTORY);
        }
    }

    public static void setSink(Consumer<String> c) {
        sink = c;
    }
}
