package lightmods;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Подбор памяти для игры и набор флагов JVM, ускоряющих загрузку и сглаживающих работу сборщика мусора. */
public final class Ram {
    private Ram() {
    }

    /** Флаги G1, рекомендованные сообществом для Minecraft с модами (Java 8 и новее). */
    public static final String[] JVM_FLAGS = {
        "-XX:+UseG1GC", "-XX:+UnlockExperimentalVMOptions", "-XX:+ParallelRefProcEnabled",
        "-XX:MaxGCPauseMillis=50", "-XX:G1NewSizePercent=20", "-XX:G1ReservePercent=20",
        "-XX:G1HeapRegionSize=16M", "-XX:+AlwaysActAsServerClassMachine", "-Xss4M"
    };

    /** Сколько .jar лежит в папке mods установки. */
    public static int modCount(Mc.Profile p) {
        if (p == null || p.gameDir == null) return 0;
        Path mods = p.gameDir.resolve("mods");
        int n = 0;
        if (!Files.isDirectory(mods)) return 0;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mods, "*.jar")) {
            for (Path ignored : ds) n++;
        } catch (IOException ignored) {
            // считаем как есть
        }
        return n;
    }

    /** Рекомендуемая память (МБ) для установки: растёт с числом модов, но не больше 60% ОЗУ компьютера. */
    public static int recommended(Mc.Profile p) {
        int total = RamSlider.systemMb();
        int mods = modCount(p);
        int want;
        if (p == null || !p.hasLoader()) want = 2048;
        else if (mods == 0) want = 3072;
        else want = 3072 + Math.min(5120, mods * 48);
        int cap = Math.max(2048, (int) (total * 0.6));
        int v = Math.min(want, cap);
        return Math.max(1024, v / 512 * 512);
    }
}
