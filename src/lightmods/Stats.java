package lightmods;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

/** Статистика по установкам: сколько наиграно, когда запускали последний раз, сколько раз. */
public final class Stats {
    public static final class Entry {
        public long seconds;
        public long lastPlayed;
        public long launches;
    }

    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".lightmods", "stats.json");
    private static final Map<String, Entry> DATA = new LinkedHashMap<>();
    private static boolean loaded;

    private Stats() {
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.isRegularFile(FILE)) return;
            Map<String, Object> m = Json.map(Json.parse(Files.readString(FILE)));
            for (Map.Entry<String, Object> e : m.entrySet()) {
                Map<String, Object> v = Json.map(e.getValue());
                Entry en = new Entry();
                en.seconds = Json.num(v, "seconds");
                en.lastPlayed = Json.num(v, "lastPlayed");
                en.launches = Json.num(v, "launches");
                DATA.put(e.getKey(), en);
            }
        } catch (Exception e) {
            Log.i("Не удалось прочитать статистику: " + e.getMessage());
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<String, Entry> e : DATA.entrySet()) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("seconds", e.getValue().seconds);
                v.put("lastPlayed", e.getValue().lastPlayed);
                v.put("launches", e.getValue().launches);
                m.put(e.getKey(), v);
            }
            Files.writeString(FILE, Json.stringify(m, true));
        } catch (Exception e) {
            Log.i("Не удалось сохранить статистику: " + e.getMessage());
        }
    }

    public static synchronized Entry get(String id) {
        load();
        Entry e = DATA.get(id);
        return e == null ? new Entry() : e;
    }

    public static synchronized void started(String id) {
        load();
        Entry e = DATA.computeIfAbsent(id, k -> new Entry());
        e.launches++;
        e.lastPlayed = System.currentTimeMillis();
        save();
    }

    public static synchronized void finished(String id, long playedMs) {
        load();
        Entry e = DATA.computeIfAbsent(id, k -> new Entry());
        e.seconds += Math.max(0, playedMs / 1000);
        e.lastPlayed = System.currentTimeMillis();
        save();
    }

    /** «2 ч 15 мин», «12 мин», «меньше минуты». */
    public static String duration(long seconds) {
        boolean en = "en".equals(I18n.lang());
        long m = seconds / 60;
        if (m < 1) return en ? "under a minute" : "меньше минуты";
        long h = m / 60;
        m %= 60;
        if (h == 0) return m + (en ? " min" : " мин");
        return h + (en ? " h " : " ч ") + m + (en ? " min" : " мин");
    }

    /** «сегодня», «вчера», «3 дня назад», «никогда». */
    public static String ago(long ts) {
        boolean en = "en".equals(I18n.lang());
        if (ts <= 0) return en ? "never" : "ещё не запускали";
        java.time.LocalDate then = java.time.Instant.ofEpochMilli(ts).atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        long d = java.time.temporal.ChronoUnit.DAYS.between(then, java.time.LocalDate.now());
        if (d <= 0) return en ? "today" : "сегодня";
        if (d == 1) return en ? "yesterday" : "вчера";
        if (en) return d + " days ago";
        long r10 = d % 10;
        long r100 = d % 100;
        String w = r10 == 1 && r100 != 11 ? "день" : (r10 >= 2 && r10 <= 4 && (r100 < 12 || r100 > 14)) ? "дня" : "дней";
        return d + " " + w + " назад";
    }
}
