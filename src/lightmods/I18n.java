package lightmods;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Перевод интерфейса. Исходные тексты в программе русские; английский берётся из словаря /i18n/en.json.
 * Сначала ищется точное совпадение, затем в строке заменяются известные фрагменты (самые длинные первыми) —
 * так переводятся и составные сообщения вида «Версия » + имя + « не установлена».
 */
public final class I18n {
    public static final String RU = "ru";
    public static final String EN = "en";

    private static volatile String lang = RU;
    private static volatile Map<String, String> exact = Map.of();
    private static volatile List<String[]> frags = List.of();
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private I18n() {
    }

    public static String lang() {
        return lang;
    }

    public static boolean isEn() {
        return EN.equals(lang);
    }

    /** Язык системы: русский — если системная локаль русская/украинская/белорусская, иначе английский. */
    public static String systemLang() {
        String l = Locale.getDefault().getLanguage();
        return l.equals("ru") || l.equals("uk") || l.equals("be") ? RU : EN;
    }

    public static synchronized void set(String l) {
        lang = EN.equals(l) ? EN : RU;
        CACHE.clear();
        if (isEn() && exact.isEmpty()) load();
    }

    private static void load() {
        try (InputStream in = I18n.class.getResourceAsStream("/i18n/en.json")) {
            if (in == null) return;
            Map<String, Object> m = Json.map(Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)));
            Map<String, String> ex = new java.util.HashMap<>();
            List<String[]> fr = new ArrayList<>();
            for (Map.Entry<String, Object> e : m.entrySet()) {
                String v = String.valueOf(e.getValue());
                ex.put(e.getKey(), v);
                fr.add(new String[]{e.getKey(), v});
            }
            fr.sort((a, b) -> b[0].length() - a[0].length());
            exact = ex;
            frags = fr;
        } catch (Exception e) {
            System.err.println("i18n: " + e);
        }
    }

    private static boolean hasCyr(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x0400 && c <= 0x04FF) return true;
        }
        return false;
    }

    /** Переводит строку на выбранный язык. Английские и пустые строки возвращаются как есть. */
    public static String t(String s) {
        if (s == null || lang.equals(RU) || s.isEmpty() || !hasCyr(s)) return s;
        String hit = CACHE.get(s);
        if (hit != null) return hit;
        String r = exact.get(s);
        if (r == null) {
            r = s;
            for (String[] f : frags) {
                if (r.contains(f[0])) r = r.replace(f[0], f[1]);
                if (!hasCyr(r)) break;
            }
        }
        if (CACHE.size() < 5000) CACHE.put(s, r);
        return r;
    }

    /** Выбирает язык: сохранённый, а при первом запуске — системный (затем показывается окно выбора). */
    public static void init(String saved) {
        set(saved == null ? systemLang() : saved);
    }
}
