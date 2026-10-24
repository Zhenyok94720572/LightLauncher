package lightmods;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Мост к нативному ядру загрузок llcore (C++). Ядро качает файлы параллельно с keep-alive и проверяет SHA-1 без JVM,
 * поэтому тысячи ассетов и библиотек грузятся заметно быстрее и почти не нагружают процессор.
 * Если ядра нет (не Windows, антивирус удалил файл) — вызывающий код использует обычную Java-загрузку.
 */
public final class Core {
    private Core() {
    }

    public static final class Job {
        public final String url;
        public final Path dest;
        public final String sha1;
        public final long size;

        public Job(String url, Path dest, String sha1, long size) {
            this.url = url;
            this.dest = dest;
            this.sha1 = sha1;
            this.size = size;
        }
    }

    public interface Progress {
        void at(int done, int total);
    }

    private static volatile Path exe;
    private static volatile boolean probed;
    public static volatile boolean disabled = Boolean.getBoolean("lightmods.nocore");

    /** Путь к ядру или null, если его нет. */
    static synchronized Path exe() {
        if (disabled) return null;
        if (probed) return exe;
        probed = true;
        String over = System.getProperty("lightmods.core");
        if (over != null) {
            Path p = Paths.get(over);
            exe = Files.isRegularFile(p) ? p : null;
            return exe;
        }
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return null;
        try (InputStream in = Core.class.getResourceAsStream("/native/llcore.exe")) {
            if (in == null) return null;
            byte[] data = in.readAllBytes();
            Path dir = Paths.get(System.getProperty("user.home"), ".lightmods", "bin");
            Path f = dir.resolve("llcore-" + Integer.toHexString(java.util.Arrays.hashCode(data)) + ".exe");
            if (!Files.isRegularFile(f) || Files.size(f) != data.length) {
                Files.createDirectories(dir);
                Path tmp = dir.resolve("llcore.tmp");
                Files.write(tmp, data);
                Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
            }
            exe = f;
        } catch (Exception e) {
            Log.i("Нативное ядро недоступно: " + e);
            exe = null;
        }
        return exe;
    }

    public static boolean available() {
        return exe() != null;
    }

    /**
     * Скачивает все задания ядром. Возвращает null при успехе, иначе текст ошибки.
     * Если ядро не запустилось вовсе — бросает IOException (тогда нужно качать на Java).
     */
    public static String fetch(List<Job> jobs, int threads, Progress prog) throws IOException {
        Path x = exe();
        if (x == null) throw new IOException("ядро не найдено");
        Path list = Files.createTempFile("llcore-jobs", ".tsv");
        try {
            StringBuilder sb = new StringBuilder();
            for (Job j : jobs) {
                sb.append(j.url).append('\t').append(j.dest.toAbsolutePath()).append('\t')
                    .append(j.sha1 == null || j.sha1.isEmpty() ? "-" : j.sha1.toLowerCase()).append('\t').append(Math.max(0, j.size)).append('\n');
            }
            Files.write(list, sb.toString().getBytes(StandardCharsets.UTF_8));
            Process pr = new ProcessBuilder(x.toString(), "fetch", list.toString(), String.valueOf(threads)).redirectErrorStream(true).start();
            List<String> fails = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    String[] f = line.split("\t");
                    if (f[0].equals("PROG") && f.length >= 3) {
                        if (prog != null) prog.at(Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                    } else if (f[0].equals("FAIL")) {
                        fails.add(f.length >= 3 ? f[1] + " — " + f[2] : line);
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        pr.destroyForcibly();
                        throw new IOException("Прервано");
                    }
                }
            }
            int code;
            try {
                code = pr.waitFor();
            } catch (InterruptedException e) {
                pr.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Прервано");
            }
            if (code == 0) return null;
            if (fails.isEmpty()) throw new IOException("ядро завершилось с кодом " + code);
            return String.join("\n", fails.subList(0, Math.min(5, fails.size())));
        } finally {
            Files.deleteIfExists(list);
        }
    }
}
