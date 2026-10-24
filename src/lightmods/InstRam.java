package lightmods;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JPanel;

/** Память для игры отдельно для каждой установки (~/.lightmods/ram.json). 0 / нет записи — как в общих настройках. */
public final class InstRam {
    private InstRam() {
    }

    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".lightmods", "ram.json");
    private static final Map<String, Long> MAP = new HashMap<>();
    private static boolean loaded;

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                Object o = Json.parse(Files.readString(FILE));
                if (o instanceof Map) for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) {
                    if (e.getValue() instanceof Number) MAP.put(String.valueOf(e.getKey()), ((Number) e.getValue()).longValue());
                }
            }
        } catch (Exception ignored) {
            // пустой список
        }
    }

    /** МБ для установки или 0, если используются общие настройки. */
    public static synchronized int get(String id) {
        load();
        Long v = MAP.get(id);
        return v == null ? 0 : v.intValue();
    }

    public static synchronized void set(String id, int mb) {
        load();
        if (mb <= 0) MAP.remove(id);
        else MAP.put(id, (long) mb);
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, Json.stringify(new LinkedHashMap<String, Object>(MAP), true));
        } catch (Exception ignored) {
            // не критично
        }
    }

    /** Диалог выбора памяти для установки. */
    public static void edit(java.awt.Component parent, Mc.Profile p) {
        int cur = get(p.id);
        int[] val = {cur > 0 ? cur : (Mc.autoRam ? Ram.recommended(p) : Mc.ramMb)};
        javax.swing.JCheckBox own = Ui.check("Своя память для этой установки", cur > 0);
        RamSlider rs = new RamSlider(val[0], v -> val[0] = v);
        rs.setEnabled(cur > 0);
        own.addActionListener(e -> rs.setEnabled(own.isSelected()));
        JPanel body = new JPanel(new java.awt.BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(Ui.label("Установка «" + p.name + "». Если галочка снята, берётся значение из Настроек.", 13f, Ui.MUTED, false), java.awt.BorderLayout.NORTH);
        JPanel mid = new JPanel(new java.awt.BorderLayout(0, 8));
        mid.setOpaque(false);
        mid.add(own, java.awt.BorderLayout.NORTH);
        mid.add(rs, java.awt.BorderLayout.CENTER);
        mid.setPreferredSize(new java.awt.Dimension(520, 110));
        body.add(mid, java.awt.BorderLayout.CENTER);
        if (Dlg.show(parent, "Память установки", body, new String[]{"Сохранить", "Отмена"}, 0) == 0) {
            set(p.id, own.isSelected() ? val[0] : 0);
            Tasks.bar.info("Память установки сохранена");
        }
    }
}
