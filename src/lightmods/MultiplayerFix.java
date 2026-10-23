package lightmods;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * В Minecraft 1.16.4 и 1.16.5 кнопка «Сетевая игра» с офлайн-ником неактивна: игра считает аккаунт ограниченным.
 * Лечится модом EnableMultiPlayerMode (Fabric/Forge), который лаунчер добавляет в установку сам.
 */
public final class MultiplayerFix {
    static final String PROJECT_ID = System.getProperty("lightmods.mpfix", "oH7RCgH6");

    private MultiplayerFix() {
    }

    /** Нужна ли установке эта правка: офлайн-аккаунт и версия 1.16.4/1.16.5. */
    public static boolean applies(Mc.Profile p, Account acc) {
        return p != null && acc != null && !acc.isOnline() && ("1.16.4".equals(p.mc) || "1.16.5".equals(p.mc));
    }

    private static boolean present(Mc.Profile p) {
        Path mods = p.gameDir.resolve("mods");
        if (!Files.isDirectory(mods)) return false;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods)) {
            for (Path f : ds) {
                String n = f.getFileName().toString().toLowerCase();
                if (n.endsWith(".jar") && n.replace("-", "").replace("_", "").contains("enablemultiplayer")) return true;
            }
        } catch (IOException ignored) {
            // считаем, что мода нет
        }
        Map<String, Object> have = Json.map(Mc.registry(p.gameDir).get(PROJECT_ID));
        return !have.isEmpty() && Files.isRegularFile(mods.resolve(Json.str(have, "file", "?")));
    }

    /** Для Fabric/Quilt/Forge скачивает мод, если его ещё нет. Для чистого Vanilla возвращает false. */
    public static boolean ensure(Mc.Profile p, Account acc, Progress prog) {
        if (!applies(p, acc)) return true;
        if (!p.hasLoader() || "neoforge".equals(p.loader)) return false;
        if (present(p)) return true;
        try {
            prog.set(0, "Мод для сетевой игры (1.16)");
            List<Modrinth.Ver> vs = Modrinth.versions(PROJECT_ID, p.mc, Modrinth.loadersFor(p.loader));
            Modrinth.Ver v = Modrinth.pickBest(vs);
            if (v == null) {
                Log.i("Мод EnableMultiPlayerMode для " + p.mc + "/" + p.loader + " не найден");
                return false;
            }
            Modrinth.installVersion(v, "EnableMultiPlayerMode", "mod", p, prog);
            Log.i("Для сетевой игры с офлайн-ником добавлен мод EnableMultiPlayerMode");
            return true;
        } catch (Exception e) {
            Log.i("Не удалось добавить мод для сетевой игры: " + e.getMessage());
            return false;
        }
    }
}
