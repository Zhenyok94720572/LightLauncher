package lightmods;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Собственный запуск Minecraft без официального лаунчера: описание версии (с inheritsFrom),
 * библиотеки, natives, ассеты, нужная Java и сборка командной строки.
 */
public final class Game {
    static final String RUNTIMES = System.getProperty("lightmods.runtimes",
            "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json");
    static final String RESOURCES = System.getProperty("lightmods.resources", "https://resources.download.minecraft.net/");

    private Game() {
    }

    // ------------------------------------------------------------------ состояние запущенных игр

    public interface Listener {
        void changed();
    }

    private static final Map<String, Process> RUNNING = new ConcurrentHashMap<>();
    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    public static void addListener(Listener l) {
        LISTENERS.add(l);
    }

    private static void fire() {
        for (Listener l : LISTENERS) {
            try {
                l.changed();
            } catch (RuntimeException ignored) {
                // слушатель интерфейса
            }
        }
    }

    public static boolean isRunning(String profileId) {
        Process p = RUNNING.get(profileId);
        return p != null && p.isAlive();
    }

    public static void kill(String profileId) {
        Process p = RUNNING.get(profileId);
        if (p != null) {
            KILLED.add(profileId);
            p.destroy();
        }
    }

    /** Установки, игру в которых закрыли из лаунчера: их код выхода — не ошибка. */
    public static final java.util.Set<String> KILLED = ConcurrentHashMap.newKeySet();
    /** Последние строки вывода игры по установкам — для окна «игра вылетела». */
    public static final Map<String, java.util.Deque<String>> TAIL = new ConcurrentHashMap<>();
    /** Время старта текущего запуска. */
    public static final Map<String, Long> STARTED = new ConcurrentHashMap<>();

    public static List<String> tail(String profileId) {
        java.util.Deque<String> d = TAIL.get(profileId);
        if (d == null) return new ArrayList<>();
        synchronized (d) {
            return new ArrayList<>(d);
        }
    }

    // ------------------------------------------------------------------ ОС

    static String osName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }

    private static String archRaw() {
        return System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
    }

    static boolean is64() {
        String a = archRaw();
        return a.contains("64") || a.equals("aarch64");
    }

    static boolean isArm() {
        String a = archRaw();
        return a.contains("aarch64") || a.contains("arm");
    }

    // ------------------------------------------------------------------ правила

    private static boolean ruleApplies(Map<String, Object> rule) {
        Map<String, Object> os = Json.map(rule.get("os"));
        if (!os.isEmpty()) {
            String n = Json.str(os, "name");
            if (n != null && !n.equals(osName())) return false;
            String a = Json.str(os, "arch");
            if (a != null) {
                // в манифестах встречается только "x86" (32-бит)
                boolean is32 = !is64() && !isArm();
                if (a.equals("x86") && !is32) return false;
                if (a.equals("arm64") && !(isArm() && is64())) return false;
                if (a.equals("x86_64") && (isArm() || !is64())) return false;
            }
            String v = Json.str(os, "version");
            if (v != null) {
                try {
                    if (!Pattern.compile(v).matcher(System.getProperty("os.version", "")).find()) return false;
                } catch (RuntimeException ignored) {
                    // кривое выражение — игнорируем
                }
            }
        }
        Map<String, Object> feats = Json.map(rule.get("features"));
        // ни одна из «особых» возможностей (демо, свой размер окна, quick play) не включена
        for (Object v : feats.values()) if (Boolean.TRUE.equals(v)) return false;
        return true;
    }

    static boolean allowed(Object rulesObj) {
        List<Object> rules = Json.list(rulesObj);
        if (rules.isEmpty()) return true;
        boolean res = false;
        for (Object o : rules) {
            Map<String, Object> r = Json.map(o);
            if (ruleApplies(r)) res = "allow".equals(Json.str(r, "action", "allow"));
        }
        return res;
    }

    // ------------------------------------------------------------------ описание версии

    /** Итоговое описание версии: json самой версии, объединённый с родительским (inheritsFrom). */
    static Map<String, Object> resolveVersion(String id) throws IOException {
        return resolveVersion(id, 0);
    }

    private static Map<String, Object> resolveVersion(String id, int depth) throws IOException {
        if (depth > 6) throw new IOException("Слишком глубокая цепочка inheritsFrom у версии " + id);
        Path f = Mc.versionsDir().resolve(id).resolve(id + ".json");
        if (!Files.isRegularFile(f)) throw new IOException("Версия " + id + " не установлена (нет " + f.getFileName() + "). Установите её во вкладке «Версии».");
        Map<String, Object> child;
        try {
            child = Json.map(Json.parse(Files.readString(f)));
        } catch (RuntimeException e) {
            throw new IOException("Файл версии " + id + " повреждён: " + e.getMessage());
        }
        String parent = Json.str(child, "inheritsFrom");
        if (parent == null) return child;
        Map<String, Object> base = resolveVersion(parent, depth + 1);
        Map<String, Object> out = new LinkedHashMap<>(base);
        for (Map.Entry<String, Object> e : child.entrySet()) {
            String k = e.getKey();
            if (k.equals("libraries")) {
                List<Object> l = new ArrayList<>(Json.list(e.getValue()));
                l.addAll(Json.list(base.get("libraries")));
                out.put(k, l);
            } else if (k.equals("arguments")) {
                Map<String, Object> ca = Json.map(e.getValue());
                Map<String, Object> ba = Json.map(base.get("arguments"));
                Map<String, Object> ma = new LinkedHashMap<>();
                for (String t : new String[]{"jvm", "game"}) {
                    List<Object> l = new ArrayList<>(Json.list(ba.get(t)));
                    l.addAll(Json.list(ca.get(t)));
                    ma.put(t, l);
                }
                out.put(k, ma);
            } else {
                out.put(k, e.getValue());
            }
        }
        out.put("lightmods.jarId", Json.str(base, "lightmods.jarId", parent));
        return out;
    }

    // ------------------------------------------------------------------ библиотеки

    static final class Lib {
        String key;
        String name;
        Path path;
        String url;
        String sha1;
        long size;
        boolean extract;
        List<String> exclude = new ArrayList<>();
    }

    private static String mavenPath(String name, String classifier) {
        String[] p = name.split(":");
        if (p.length < 3) return null;
        String ext = "jar";
        String ver = p[2];
        if (ver.contains("@")) {
            ext = ver.substring(ver.indexOf('@') + 1);
            ver = ver.substring(0, ver.indexOf('@'));
        }
        String cl = p.length > 3 ? "-" + p[3] : "";
        if (classifier != null) cl = "-" + classifier;
        return p[0].replace('.', '/') + "/" + p[1] + "/" + ver + "/" + p[1] + "-" + ver + cl + "." + ext;
    }

    static List<Lib> libraries(Map<String, Object> v) {
        Path libs = Mc.dir.resolve("libraries");
        List<Lib> out = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Object o : Json.list(v.get("libraries"))) {
            Map<String, Object> l = Json.map(o);
            if (!allowed(l.get("rules"))) continue;
            String name = Json.str(l, "name", "");
            String[] np = name.split(":");
            Map<String, Object> dl = Json.map(l.get("downloads"));
            Map<String, Object> natives = Json.map(l.get("natives"));
            String nativeKey = natives.isEmpty() ? null : Json.str(natives, osName());
            if (nativeKey != null) nativeKey = nativeKey.replace("${arch}", is64() ? "64" : "32");
            Lib lib = new Lib();
            lib.name = name;
            if (nativeKey != null) {
                // старые natives: отдельный файл-классификатор, распаковывается в папку natives
                Map<String, Object> a = Json.map(Json.map(dl.get("classifiers")).get(nativeKey));
                String path = Json.str(a, "path");
                if (path == null) path = mavenPath(name, nativeKey);
                if (path == null) continue;
                lib.path = libs.resolve(path);
                lib.url = Json.str(a, "url");
                if (lib.url == null && Json.str(l, "url") != null) lib.url = Json.str(l, "url") + path;
                lib.sha1 = Json.str(a, "sha1");
                lib.size = Json.num(a, "size");
                lib.extract = true;
                lib.exclude = Json.strings(Json.map(l.get("extract")).get("exclude"));
                lib.key = name + ":" + nativeKey;
                out.add(lib);
                // основной артефакт у таких библиотек тоже может быть (без классификатора)
                Map<String, Object> art = Json.map(dl.get("artifact"));
                if (!art.isEmpty() && Json.str(art, "path") != null) {
                    Lib main = new Lib();
                    main.name = name;
                    main.path = libs.resolve(Json.str(art, "path"));
                    main.url = Json.str(art, "url");
                    main.sha1 = Json.str(art, "sha1");
                    main.size = Json.num(art, "size");
                    main.key = np.length >= 2 ? np[0] + ":" + np[1] : name;
                    if (seen.add(main.key)) out.add(main);
                }
                continue;
            }
            Map<String, Object> art = Json.map(dl.get("artifact"));
            String path = Json.str(art, "path");
            if (path == null) path = mavenPath(name, null);
            if (path == null) continue;
            lib.path = libs.resolve(path);
            lib.url = Json.str(art, "url");
            if (lib.url == null && Json.str(l, "url") != null) lib.url = Json.str(l, "url") + path;
            if (lib.url != null && lib.url.isEmpty()) lib.url = null;
            lib.sha1 = Json.str(art, "sha1");
            lib.size = Json.num(art, "size");
            // ключ без версии: при конфликте побеждает библиотека версии-потомка (она идёт первой)
            lib.key = np.length >= 2 ? np[0] + ":" + np[1] + (np.length > 3 ? ":" + np[3] : "") : name;
            if (!seen.add(lib.key)) continue;
            out.add(lib);
        }
        return out;
    }

    // ------------------------------------------------------------------ скачивание

    private static final class Pool {
        // не больше 8 потоков и пониженный приоритет: тысячи мелких файлов с TLS-рукопожатиями иначе забивают процессор и тормозят интерфейс
        final ExecutorService ex = Executors.newFixedThreadPool(Math.max(4, Math.min(8, Runtime.getRuntime().availableProcessors())), r -> {
            Thread t = new Thread(r, "lightmods-dl");
            t.setDaemon(true);
            t.setPriority(Thread.NORM_PRIORITY - 2);
            return t;
        });
        final List<Future<?>> fs = new ArrayList<>();
        final AtomicInteger done = new AtomicInteger();
        int total;
        final List<Core.Job> jobs = new ArrayList<>();
        final List<Runnable> afters = new ArrayList<>();

        /** Загрузка файла: нативным ядром (одним пакетом), а если его нет — обычным потоком. after — действие после успешной загрузки. */
        void download(String url, Path dest, String sha1, long size, Runnable after) {
            jobs.add(new Core.Job(url, dest, sha1, size));
            afters.add(after);
        }

        private void runJobs(Progress p, String text) throws IOException {
            if (jobs.isEmpty()) return;
            boolean viaCore = false;
            if (Core.available()) {
                try {
                    int th = Math.max(4, Math.min(12, Runtime.getRuntime().availableProcessors() * 2));
                    String err = Core.fetch(jobs, th, (d, t) -> p.set(t == 0 ? 1 : (double) d / t, text + " " + d + "/" + t));
                    if (err != null) throw new IOException("Не удалось скачать:\n" + err);
                    viaCore = true;
                } catch (IOException e) {
                    if (e.getMessage() != null && e.getMessage().startsWith("Не удалось скачать")) throw e;
                    Log.i("Нативное ядро не сработало (" + e.getMessage() + "), качаю средствами Java");
                }
            }
            for (int i = 0; i < jobs.size(); i++) {
                Core.Job j = jobs.get(i);
                Runnable after = afters.get(i);
                if (viaCore) {
                    if (after != null) submit(after);
                } else {
                    submit(() -> {
                        fetch(j.url, j.dest, j.sha1, j.size);
                        if (after != null) after.run();
                    });
                }
            }
            jobs.clear();
            afters.clear();
        }

        void submit(Runnable r) {
            total++;
            fs.add(ex.submit(() -> {
                try {
                    r.run();
                } finally {
                    done.incrementAndGet();
                }
            }));
        }

        void await(Progress p, String text) throws IOException {
            try {
                runJobs(p, text);
                for (Future<?> f : fs) {
                    try {
                        f.get();
                    } catch (java.util.concurrent.ExecutionException e) {
                        Throwable c = e.getCause();
                        if (c instanceof Unchecked) c = c.getCause();
                        throw c instanceof IOException ? (IOException) c : new IOException(String.valueOf(c), c);
                    }
                    p.set(total == 0 ? 1 : (double) done.get() / total, text + " " + done.get() + "/" + total);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервано");
            } finally {
                ex.shutdownNow();
            }
        }
    }

    private static final class Unchecked extends RuntimeException {
        Unchecked(IOException e) {
            super(e);
        }
    }

    private static boolean fresh(Path f, long size) {
        try {
            return Files.isRegularFile(f) && (size <= 0 || Files.size(f) == size);
        } catch (IOException e) {
            return false;
        }
    }

    private static void fetch(String url, Path dest, String sha1, long size) {
        if (fresh(dest, size)) return;
        try {
            Net.download(url, dest, sha1, null);
        } catch (IOException e) {
            throw new Unchecked(e);
        }
    }

    // ------------------------------------------------------------------ Java

    static final class JavaRt {
        Path exe;
        int major;
    }

    private static int javaMajor(String ver) {
        try {
            String v = ver.startsWith("1.") ? ver.substring(2) : ver;
            int i = 0;
            while (i < v.length() && Character.isDigit(v.charAt(i))) i++;
            return Integer.parseInt(v.substring(0, i));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static Path currentJava() {
        Path bin = Path.of(System.getProperty("java.home"), "bin", isWin() ? "javaw.exe" : "java");
        if (!Files.exists(bin) && isWin()) bin = Path.of(System.getProperty("java.home"), "bin", "java.exe");
        return bin;
    }

    private static boolean isWin() {
        return osName().equals("windows");
    }

    private static String runtimePlatform() {
        switch (osName()) {
            case "windows":
                return isArm() ? "windows-arm64" : (is64() ? "windows-x64" : "windows-x86");
            case "osx":
                return isArm() ? "mac-os-arm64" : "mac-os";
            default:
                return is64() ? "linux" : "linux-i386";
        }
    }

    static Path javaExe(Path home) {
        if (isWin()) {
            Path w = home.resolve("bin").resolve("javaw.exe");
            if (Files.isRegularFile(w)) return w;
            return home.resolve("bin").resolve("java.exe");
        }
        Path mac = home.resolve("jre.bundle").resolve("Contents").resolve("Home").resolve("bin").resolve("java");
        if (Files.isRegularFile(mac)) return mac;
        return home.resolve("bin").resolve("java");
    }

    /** Подбирает Java: путь из настроек → скачанная Mojang-среда нужной версии → текущая Java (если подходит). */
    static Path findJava(Map<String, Object> v, Progress p) throws IOException {
        String forced = System.getProperty("lightmods.java", "");
        if (forced.isEmpty()) forced = Mc.javaPath == null ? "" : Mc.javaPath.trim();
        if (!forced.isEmpty()) {
            Path f = Path.of(forced);
            if (Files.isDirectory(f)) f = javaExe(f);
            if (!Files.isRegularFile(f)) throw new IOException("Java из настроек не найдена: " + forced);
            return f;
        }
        Map<String, Object> jv = Json.map(v.get("javaVersion"));
        String comp = Json.str(jv, "component", "jre-legacy");
        int need = (int) Json.num(jv, "majorVersion");
        if (need == 0) need = 8;
        Path home = Mc.dir.resolve("runtime").resolve(comp).resolve(runtimePlatform()).resolve(comp);
        Path ready = home.resolve(".ready");
        if (Files.isRegularFile(ready) && Files.isRegularFile(javaExe(home))) return javaExe(home);
        try {
            installRuntime(comp, home, p);
            return javaExe(home);
        } catch (IOException e) {
            Log.i("Не удалось скачать Java " + comp + ": " + e.getMessage());
            int cur = javaMajor(System.getProperty("java.version", ""));
            if (cur >= need && Files.exists(currentJava())) {
                Log.i("Использую Java, на которой работает лаунчер (" + System.getProperty("java.version") + ")");
                return currentJava();
            }
            throw new IOException("Не удалось получить Java " + need + " для этой версии игры: " + e.getMessage()
                    + ". Установите Java " + need + " и укажите путь в Настройках.");
        }
    }

    private static void installRuntime(String comp, Path home, Progress p) throws IOException {
        p.set(0, "Java: поиск среды " + comp);
        Map<String, Object> all = Json.map(Net.getJsonCached(RUNTIMES));
        List<Object> arr = Json.list(Json.map(all.get(runtimePlatform())).get(comp));
        if (arr.isEmpty()) throw new IOException("для вашей системы (" + runtimePlatform() + ") у Mojang нет Java-среды " + comp);
        Map<String, Object> mf = Json.map(Json.map(Json.map(arr.get(0)).get("manifest")));
        Map<String, Object> man = Json.map(Json.parse(Net.getString(Json.str(mf, "url"))));
        Map<String, Object> files = Json.map(man.get("files"));
        Files.createDirectories(home);
        Pool pool = new Pool();
        List<String[]> links = new ArrayList<>();
        List<Path> exec = new ArrayList<>();
        for (Map.Entry<String, Object> e : files.entrySet()) {
            Map<String, Object> f = Json.map(e.getValue());
            Path dest = home.resolve(e.getKey()).normalize();
            if (!dest.startsWith(home)) continue;
            String type = Json.str(f, "type", "file");
            if (type.equals("directory")) {
                Files.createDirectories(dest);
            } else if (type.equals("link")) {
                links.add(new String[]{e.getKey(), Json.str(f, "target", "")});
            } else {
                Map<String, Object> raw = Json.map(Json.map(f.get("downloads")).get("raw"));
                String url = Json.str(raw, "url");
                if (url == null) continue;
                if (Json.bool(f, "executable")) exec.add(dest);
                pool.download(url, dest, Json.str(raw, "sha1"), Json.num(raw, "size"), null);
            }
        }
        try {
            pool.await(p.sub(0, 1), "Java " + comp + ":");
        } catch (Unchecked u) {
            throw (IOException) u.getCause();
        }
        for (Path x : exec) x.toFile().setExecutable(true, false);
        if (!isWin()) {
            for (String[] l : links) {
                try {
                    Path link = home.resolve(l[0]).normalize();
                    if (!link.startsWith(home)) continue;
                    Files.createDirectories(link.getParent());
                    Files.deleteIfExists(link);
                    Files.createSymbolicLink(link, Path.of(l[1]));
                } catch (IOException | UnsupportedOperationException ex) {
                    Log.i("Ссылка Java: " + ex);
                }
            }
        }
        Files.writeString(home.resolve(".ready"), "ok");
    }

    // ------------------------------------------------------------------ ассеты

    private static Path prepareAssets(Map<String, Object> v, Path gameDir, Progress p) throws IOException {
        Map<String, Object> ai = Json.map(v.get("assetIndex"));
        String id = Json.str(ai, "id", Json.str(v, "assets", "legacy"));
        Path assets = Mc.dir.resolve("assets");
        Path idx = assets.resolve("indexes").resolve(id + ".json");
        String url = Json.str(ai, "url");
        if (url != null && !fresh(idx, Json.num(ai, "size"))) {
            p.set(0, "Ассеты: индекс " + id);
            Net.download(url, idx, Json.str(ai, "sha1"), null);
        }
        if (!Files.isRegularFile(idx)) return assets;
        Map<String, Object> index = Json.map(Json.parse(Files.readString(idx)));
        boolean virtual = Json.bool(index, "virtual");
        boolean mapRes = Json.bool(index, "map_to_resources");
        Map<String, Object> objects = Json.map(index.get("objects"));
        Pool pool = new Pool();
        Path virtualDir = assets.resolve("virtual").resolve(id);
        Path resDir = gameDir.resolve("resources");
        // у многих ассетов одинаковый хэш (разные имена): каждый объект качаем один раз, копии делаем после
        Map<String, List<String>> byHash = new LinkedHashMap<>();
        Map<String, Long> sizes = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : objects.entrySet()) {
            Map<String, Object> o = Json.map(e.getValue());
            String hash = Json.str(o, "hash");
            if (hash == null || hash.length() < 2) continue;
            byHash.computeIfAbsent(hash, k -> new ArrayList<>()).add(e.getKey());
            sizes.put(hash, Json.num(o, "size"));
        }
        for (Map.Entry<String, List<String>> e : byHash.entrySet()) {
            String hash = e.getKey();
            long size = sizes.get(hash);
            Path obj = assets.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
            List<String> names = e.getValue();
            // уже скачанное и не требующее копий не ставим в очередь: при запуске игры это тысячи лишних задач
            if (fresh(obj, size) && !virtual && !mapRes) continue;
            Runnable copies = (virtual || mapRes) ? () -> {
                for (String name : names) {
                    Path extra = virtual ? virtualDir.resolve(name) : resDir.resolve(name);
                    if (fresh(extra, size)) continue;
                    try {
                        Files.createDirectories(extra.getParent());
                        Files.copy(obj, extra, StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException ex) {
                        throw new Unchecked(ex);
                    }
                }
            } : null;
            pool.download(RESOURCES + hash.substring(0, 2) + "/" + hash, obj, hash, size, copies);
        }
        try {
            pool.await(p, "Ассеты");
        } catch (Unchecked u) {
            throw (IOException) u.getCause();
        }
        if (virtual) return virtualDir;
        if (mapRes) return resDir;
        return assets;
    }

    // ------------------------------------------------------------------ запуск

    public static final class Plan {
        public List<String> command = new ArrayList<>();
        public Path workDir;
        public String versionId;
    }

    private static void extractNatives(List<Lib> libs, Path nativesDir) throws IOException {
        Files.createDirectories(nativesDir);
        for (Lib l : libs) {
            if (!l.extract || !Files.isRegularFile(l.path)) continue;
            try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(l.path.toFile())) {
                java.util.Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    ZipEntry e = en.nextElement();
                    if (e.isDirectory()) continue;
                    String n = e.getName();
                    boolean skip = false;
                    for (String ex : l.exclude) if (n.startsWith(ex)) skip = true;
                    if (skip || n.startsWith("META-INF/")) continue;
                    Path out = nativesDir.resolve(n).normalize();
                    if (!out.startsWith(nativesDir)) continue;
                    // уже распакованное не трогаем: заново писать DLL при каждом запуске долго, а антивирус проверяет их снова
                    if (e.getSize() >= 0 && Files.isRegularFile(out) && Files.size(out) == e.getSize()) continue;
                    Files.createDirectories(out.getParent());
                    try (InputStream in = z.getInputStream(e)) {
                        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private static void collectArgs(Object list, Map<String, String> vars, List<String> out) {
        for (Object o : Json.list(list)) {
            if (o instanceof String) {
                out.add(subst((String) o, vars));
            } else if (o instanceof Map) {
                Map<String, Object> m = Json.map(o);
                if (!allowed(m.get("rules"))) continue;
                Object val = m.get("value");
                if (val instanceof String) out.add(subst((String) val, vars));
                else for (String s : Json.strings(val)) out.add(subst(s, vars));
            }
        }
    }

    static String subst(String s, Map<String, String> vars) {
        if (!s.contains("${")) return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            int a = s.indexOf("${", i);
            if (a < 0) {
                sb.append(s, i, s.length());
                break;
            }
            int b = s.indexOf('}', a);
            if (b < 0) {
                sb.append(s, i, s.length());
                break;
            }
            sb.append(s, i, a);
            String k = s.substring(a + 2, b);
            String r = vars.get(k);
            sb.append(r != null ? r : s.substring(a, b + 1));
            i = b + 1;
        }
        return sb.toString();
    }

    /** Скачивает всё нужное и собирает команду запуска. */
    public static Plan prepare(Mc.Profile prof, Account.Session acc, Progress p) throws IOException {
        if (prof == null || prof.versionId == null) throw new IOException("Не выбрана установка");
        final long t0 = System.nanoTime();
        String vid = prof.versionId;
        Map<String, Object> v = resolveVersion(vid);
        String jarId = Json.str(v, "lightmods.jarId", vid);
        if (Json.str(v, "jar") != null) jarId = Json.str(v, "jar");
        Path gameDir = prof.gameDir;
        Files.createDirectories(gameDir);

        // клиентский jar (для Fabric/Forge — jar родительской ванильной версии)
        p.set(0.0, "Клиент Minecraft");
        Path clientJar = Mc.versionsDir().resolve(jarId).resolve(jarId + ".jar");
        Map<String, Object> clientDl = Json.map(Json.map(v.get("downloads")).get("client"));
        if (Json.str(clientDl, "url") != null) {
            try {
                fetch(Json.str(clientDl, "url"), clientJar, Json.str(clientDl, "sha1"), Json.num(clientDl, "size"));
            } catch (Unchecked u) {
                throw (IOException) u.getCause();
            }
        } else if (!Files.isRegularFile(clientJar) || Files.size(clientJar) == 0) {
            Mc.installVanilla(jarId, p.sub(0, 0.1));
        }

        // библиотеки
        List<Lib> libs = libraries(v);
        Pool pool = new Pool();
        java.util.Set<Path> queued = new java.util.HashSet<>();
        for (Lib l : libs) {
            if (fresh(l.path, l.size) || !queued.add(l.path)) continue;
            if (l.url == null) {
                if (!Files.isRegularFile(l.path)) Log.i("Предупреждение: нет библиотеки и ссылки на неё: " + l.name);
                continue;
            }
            pool.download(l.url, l.path, l.sha1, l.size, null);
        }
        try {
            pool.await(p.sub(0.05, 0.25), "Библиотеки");
        } catch (Unchecked u) {
            throw (IOException) u.getCause();
        }

        // логирование клиента
        Map<String, Object> logCfg = Json.map(Json.map(Json.map(v.get("logging")).get("client")).get("file"));
        Path logPath = null;
        if (Json.str(logCfg, "url") != null) {
            logPath = Mc.dir.resolve("assets").resolve("log_configs").resolve(Json.str(logCfg, "id", "client-log.xml"));
            try {
                fetch(Json.str(logCfg, "url"), logPath, Json.str(logCfg, "sha1"), Json.num(logCfg, "size"));
            } catch (Unchecked u) {
                Log.i("Конфиг логов не скачан: " + u.getCause());
                logPath = null;
            }
        }

        // ассеты
        Path gameAssets = prepareAssets(v, gameDir, p.sub(0.3, 0.45));

        // Java
        Path java = findJava(v, p.sub(0.75, 0.2));

        // natives
        Path natives = Mc.versionsDir().resolve(vid).resolve("natives");
        extractNatives(libs, natives);

        // classpath
        String sep = File.pathSeparator;
        StringBuilder cp = new StringBuilder();
        for (Lib l : libs) {
            if (l.extract || !Files.isRegularFile(l.path)) continue;
            if (cp.length() > 0) cp.append(sep);
            cp.append(l.path.toAbsolutePath());
        }
        if (cp.length() > 0) cp.append(sep);
        cp.append(clientJar.toAbsolutePath());

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("auth_player_name", acc.name);
        vars.put("version_name", vid);
        vars.put("game_directory", gameDir.toAbsolutePath().toString());
        vars.put("assets_root", Mc.dir.resolve("assets").toAbsolutePath().toString());
        vars.put("game_assets", gameAssets.toAbsolutePath().toString());
        vars.put("assets_index_name", Json.str(Json.map(v.get("assetIndex")), "id", Json.str(v, "assets", "legacy")));
        vars.put("auth_uuid", acc.uuid);
        vars.put("auth_access_token", acc.accessToken);
        vars.put("auth_session", "token:" + acc.accessToken + ":" + acc.uuid);
        vars.put("clientid", "");
        vars.put("auth_xuid", acc.xuid == null ? "" : acc.xuid);
        vars.put("user_type", acc.ely ? "mojang" : acc.online ? "msa" : "legacy");
        vars.put("user_properties", "{}");
        vars.put("version_type", Json.str(v, "type", "release"));
        vars.put("natives_directory", natives.toAbsolutePath().toString());
        vars.put("launcher_name", "Light Launcher");
        vars.put("launcher_version", "1.1");
        vars.put("classpath", cp.toString());
        vars.put("classpath_separator", sep);
        vars.put("library_directory", Mc.dir.resolve("libraries").toAbsolutePath().toString());

        List<String> jvm = new ArrayList<>();
        Map<String, Object> args = Json.map(v.get("arguments"));
        List<String> game = new ArrayList<>();
        // у версий старого формата (1.12–1.16) аргументы игры лежат в minecraftArguments, а у загрузчиков (Fabric/Quilt)
        // поверх них бывает блок arguments — учитываем оба источника
        String legacy = Json.str(v, "minecraftArguments", "");
        for (String a : legacy.trim().split("\\s+")) if (!a.isEmpty()) game.add(subst(a, vars));
        collectArgs(args.get("jvm"), vars, jvm);
        collectArgs(args.get("game"), vars, game);
        if (!jvm.contains("-cp") && !jvm.contains("-classpath")) {
            List<String> head = new ArrayList<>();
            head.add("-Djava.library.path=" + natives.toAbsolutePath());
            head.add("-Dminecraft.launcher.brand=Light Launcher");
            head.add("-Dminecraft.launcher.version=1.1");
            head.add("-cp");
            head.add(cp.toString());
            jvm.addAll(0, head);
        }
        if (logPath != null) {
            String la = Json.str(Json.map(Json.map(v.get("logging")).get("client")), "argument");
            if (la != null) {
                Map<String, String> lv = new LinkedHashMap<>();
                lv.put("path", logPath.toAbsolutePath().toString());
                jvm.add(subst(la, lv));
            }
        }

        Plan plan = new Plan();
        plan.workDir = gameDir;
        plan.versionId = vid;
        plan.command.add(java.toAbsolutePath().toString());
        int own = InstRam.get(prof.id);
        int ram = own > 0 ? own : (Mc.autoRam ? Ram.recommended(prof) : Math.max(512, Mc.ramMb));
        plan.command.add("-Xmx" + ram + "M");
        // стартовый размер кучи побольше: иначе тяжёлая сборка при загрузке много раз расширяет память и делает полные сборки мусора
        plan.command.add("-Xms" + (Mc.optimizeJvm ? Math.min(ram, 2048) : Math.min(512, Math.max(256, ram / 4))) + "M");
        boolean userGc = Mc.jvmArgs != null && Mc.jvmArgs.contains("XX:+Use");
        if (Mc.optimizeJvm && !userGc) {
            for (String a : Ram.JVM_FLAGS) plan.command.add(a);
        }
        Log.i("Память игры: " + ram + " МБ" + (own > 0 ? " (для установки)" : Mc.autoRam ? " (авто)" : "") + (Mc.optimizeJvm ? ", оптимизация JVM включена" : ""));
        if (Mc.jvmArgs != null && !Mc.jvmArgs.isBlank()) {
            for (String a : Mc.jvmArgs.trim().split("\\s+")) plan.command.add(a);
        }
        if (acc.ely) {
            p.set(0.99, "authlib-injector");
            // явный адрес API Ely.by: так authlib-injector не зависит от разрешения короткого имени и сразу получает ключи и домены скинов
            plan.command.add("-javaagent:" + Account.authlibInjector().toAbsolutePath() + "=" + Account.ELY_API);
            plan.command.add("-Dauthlibinjector.debuglog=true");
            // Java 8 от Mojang не доверяет современным сертификатам Ely.by — даём игре актуальное хранилище из Java лаунчера
            Path ts = Account.trustStore();
            if (ts != null) {
                plan.command.add("-Djavax.net.ssl.trustStore=" + ts.toAbsolutePath());
                plan.command.add("-Djavax.net.ssl.trustStoreType=JKS");
                plan.command.add("-Djavax.net.ssl.trustStorePassword=changeit");
            }
        }
        plan.command.addAll(jvm);
        plan.command.add(Json.str(v, "mainClass", "net.minecraft.client.main.Main"));
        plan.command.addAll(game);
        p.set(1.0, "Запуск игры");
        Log.i("Подготовка запуска заняла " + (System.nanoTime() - t0) / 1_000_000 + " мс");
        return plan;
    }

    /** Запускает игру; вывод процесса идёт в журнал. */
    public static Process start(Mc.Profile prof, Plan plan, Account.Session acc) throws IOException {
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < plan.command.size(); i++) {
            String a = plan.command.get(i);
            if (acc.accessToken != null && acc.online && a.contains(acc.accessToken)) a = a.replace(acc.accessToken, "***");
            shown.add(a);
        }
        Log.i("Запуск: " + String.join(" ", shown));
        ProcessBuilder pb = new ProcessBuilder(plan.command).directory(plan.workDir.toFile()).redirectErrorStream(true);
        Process pr = pb.start();
        RUNNING.put(prof.id, pr);
        KILLED.remove(prof.id);
        java.util.ArrayDeque<String> tail = new java.util.ArrayDeque<>();
        TAIL.put(prof.id, tail);
        long t0 = System.currentTimeMillis();
        STARTED.put(prof.id, t0);
        Stats.started(prof.id);
        fire();
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(pr.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Log.i("[игра] " + line);
                    synchronized (tail) {
                        tail.addLast(line);
                        if (tail.size() > 400) tail.removeFirst();
                    }
                }
            } catch (IOException ignored) {
                // процесс завершён
            }
            int code;
            try {
                code = pr.waitFor();
            } catch (InterruptedException e) {
                return;
            }
            RUNNING.remove(prof.id);
            STARTED.remove(prof.id);
            Stats.finished(prof.id, System.currentTimeMillis() - t0);
            Log.i("Игра завершилась с кодом " + code);
            LAST_EXIT.put(prof.id, code);
            fire();
        }, "lightmods-game");
        t.setDaemon(true);
        t.start();
        return pr;
    }

    public static final Map<String, Integer> LAST_EXIT = new ConcurrentHashMap<>();
}
