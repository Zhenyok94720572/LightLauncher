package lightmods;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.SwingUtilities;

/** Общее состояние приложения: список установок (профилей лаунчера) и выбранная установка. */
public final class State {
    private static volatile List<Mc.Profile> profiles = new ArrayList<>();
    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private State() {
    }

    public static List<Mc.Profile> profiles() {
        return profiles;
    }

    public static Mc.Profile current() {
        List<Mc.Profile> ps = profiles;
        if (ps.isEmpty()) return null;
        for (Mc.Profile p : ps) if (p.id.equals(Mc.selectedId)) return p;
        return ps.get(0);
    }

    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    private static void fire() {
        for (Runnable r : LISTENERS) {
            try {
                r.run();
            } catch (RuntimeException e) {
                Log.i("Ошибка обновления интерфейса: " + e);
            }
        }
    }

    public static void select(String id) {
        if (id == null || id.equals(Mc.selectedId)) return;
        Mc.selectedId = id;
        Mc.saveConfig();
        fire();
    }

    /** Перечитывает launcher_profiles.json в фоне и обновляет интерфейс. */
    public static void refresh() {
        refreshAndSelect(null);
    }

    public static void refreshAndSelect(String selectId) {
        Tasks.IO.submit(() -> {
            List<Mc.Profile> ps = Mc.readProfiles();
            SwingUtilities.invokeLater(() -> {
                profiles = ps;
                if (selectId != null) Mc.selectedId = selectId;
                boolean ok = false;
                for (Mc.Profile p : ps) if (p.id.equals(Mc.selectedId)) ok = true;
                if (!ok && !ps.isEmpty()) Mc.selectedId = ps.get(0).id;
                if (selectId != null) Mc.saveConfig();
                fire();
            });
        });
    }
}
