package lightmods;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Клиент Modrinth API v2 + установка модов, текстур, шейдеров и сборок (.mrpack). */
public final class Modrinth {
    static final String BASE = System.getProperty("lightmods.api", "https://api.modrinth.com/v2");

    private Modrinth() {
    }

    // ------------------------------------------------------------------ модели

    public static final class Hit {
        public String id;
        public String slug;
        public String title;
        public String description;
        public String author;
        public String iconUrl;
        public String type;
        public long downloads;
        public long follows;
        public List<String> categories = new ArrayList<>();
    }

    public static final class FileInfo {
        public String url;
        public String filename;
        public String sha1;
        public long size;
        public boolean primary;
    }

    public static final class Dep {
        public String projectId;
        public String versionId;
        public String type;
    }

    public static final class Ver {
        public String id;
        public String projectId;
        public String name;
        public String number;
        public String type;
        public String published = "";
        public List<String> gameVersions = new ArrayList<>();
        public List<String> loaders = new ArrayList<>();
        public List<FileInfo> files = new ArrayList<>();
        public List<Dep> deps = new ArrayList<>();

        public FileInfo mainFile() {
            for (FileInfo f : files) if (f.primary) return f;
            return files.isEmpty() ? null : files.get(0);
        }

        @Override
        public String toString() {
            String gv = gameVersions.isEmpty() ? "" : "  [" + String.join(", ", gameVersions.subList(0, Math.min(4, gameVersions.size()))) + (gameVersions.size() > 4 ? "…" : "") + "]";
            return (name == null || name.isBlank() ? number : name) + gv + ("release".equals(type) ? "" : "  (" + type + ")");
        }
    }

    // ------------------------------------------------------------------ API

    private static String q(String s) {
        return Net.enc(s);
    }

    private static String jsonList(List<String> l) {
        List<Object> o = new ArrayList<>(l);
        return Json.stringify(o, false);
    }

    public static List<String> loadersFor(String loader) {
        List<String> l = new ArrayList<>();
        if (loader == null || "vanilla".equals(loader)) return l;
        l.add(loader);
        if ("quilt".equals(loader)) l.add("fabric");
        return l;
    }

    public static List<Hit> search(String query, String type, String mc, String loader, String sort, int offset, int limit) throws IOException {
        List<List<String>> facets = new ArrayList<>();
        facets.add(List.of("project_type:" + type));
        if (mc != null && !mc.isBlank()) facets.add(List.of("versions:" + mc));
        if (("mod".equals(type) || "modpack".equals(type)) && loader != null && !"vanilla".equals(loader)) {
            List<String> cats = new ArrayList<>();
            for (String l : loadersFor(loader)) cats.add("categories:" + l);
            facets.add(cats);
        }
        if ("mod".equals(type)) facets.add(List.of("client_side:required", "client_side:optional"));
        List<Object> fo = new ArrayList<>();
        for (List<String> f : facets) fo.add(new ArrayList<Object>(f));
        StringBuilder url = new StringBuilder(BASE).append("/search?limit=").append(limit).append("&offset=").append(offset);
        if (query != null && !query.isBlank()) url.append("&query=").append(q(query.trim()));
        url.append("&index=").append(q(sort == null ? "relevance" : sort));
        url.append("&facets=").append(q(Json.stringify(fo, false)));
        Map<String, Object> res = Json.map(Net.getJson(url.toString()));
        List<Hit> hits = new ArrayList<>();
        for (Object o : Json.list(res.get("hits"))) {
            Map<String, Object> h = Json.map(o);
            Hit x = new Hit();
            x.id = Json.str(h, "project_id");
            x.slug = Json.str(h, "slug");
            x.title = Json.str(h, "title", x.slug);
            x.description = Json.str(h, "description", "");
            x.author = Json.str(h, "author", "");
            x.iconUrl = Json.str(h, "icon_url");
            x.type = Json.str(h, "project_type", type);
            x.downloads = Json.num(h, "downloads");
            x.follows = Json.num(h, "follows");
            x.categories = Json.strings(h.get("categories"));
            if (x.id != null) hits.add(x);
        }
        upgradeIcons(hits);
        return hits;
    }

    /**
     * В выдаче поиска только миниатюра WebP (…_96.webp), которую Java не читает. Одним запросом берём
     * raw_icon_url (оригинал png/jpeg/gif) для всех проектов страницы и подменяем ссылку.
     */
    private static void upgradeIcons(List<Hit> hits) {
        if (hits.isEmpty()) return;
        try {
            List<String> ids = new ArrayList<>();
            for (Hit h : hits) ids.add(h.id);
            Object r = Net.getJson(BASE + "/projects?ids=" + q(jsonList(ids)));
            Map<String, String> raw = new java.util.HashMap<>();
            for (Object o : Json.list(r)) {
                Map<String, Object> m = Json.map(o);
                String u = Json.str(m, "raw_icon_url");
                if (u == null || u.isBlank()) u = Json.str(m, "icon_url");
                if (u != null) raw.put(Json.str(m, "id"), u);
            }
            for (Hit h : hits) {
                String u = raw.get(h.id);
                if (u != null) h.iconUrl = u;
            }
        } catch (Exception e) {
            Log.i("Обложки: " + e);
        }
    }

    private static Ver parseVersion(Map<String, Object> v) {
        Ver r = new Ver();
        r.id = Json.str(v, "id");
        r.projectId = Json.str(v, "project_id");
        r.name = Json.str(v, "name", "");
        r.number = Json.str(v, "version_number", "");
        r.type = Json.str(v, "version_type", "release");
        r.published = Json.str(v, "date_published", "");
        r.gameVersions = Json.strings(v.get("game_versions"));
        r.loaders = Json.strings(v.get("loaders"));
        for (Object o : Json.list(v.get("files"))) {
            Map<String, Object> f = Json.map(o);
            FileInfo fi = new FileInfo();
            fi.url = Json.str(f, "url");
            fi.filename = Json.str(f, "filename");
            fi.size = Json.num(f, "size");
            fi.primary = Json.bool(f, "primary");
            fi.sha1 = Json.str(Json.map(f.get("hashes")), "sha1");
            if (fi.url != null && fi.filename != null) r.files.add(fi);
        }
        for (Object o : Json.list(v.get("dependencies"))) {
            Map<String, Object> d = Json.map(o);
            Dep dd = new Dep();
            dd.projectId = Json.str(d, "project_id");
            dd.versionId = Json.str(d, "version_id");
            dd.type = Json.str(d, "dependency_type", "optional");
            r.deps.add(dd);
        }
        return r;
    }

    /** Версии проекта, новые первыми. loaders/mc можно не задавать (null). */
    public static List<Ver> versions(String projectId, String mc, List<String> loaders) throws IOException {
        StringBuilder url = new StringBuilder(BASE).append("/project/").append(q(projectId)).append("/version");
        String sep = "?";
        if (loaders != null && !loaders.isEmpty()) {
            url.append(sep).append("loaders=").append(q(jsonList(loaders)));
            sep = "&";
        }
        if (mc != null && !mc.isBlank()) url.append(sep).append("game_versions=").append(q(jsonList(List.of(mc))));
        List<Ver> r = new ArrayList<>();
        for (Object o : Json.list(Net.getJson(url.toString()))) {
            Ver v = parseVersion(Json.map(o));
            if (v.mainFile() != null) r.add(v);
        }
        return r;
    }

    public static Ver version(String versionId) throws IOException {
        return parseVersion(Json.map(Net.getJson(BASE + "/version/" + q(versionId))));
    }

    public static String projectTitle(String projectId) {
        try {
            return Json.str(Json.map(Net.getJson(BASE + "/project/" + q(projectId))), "title", projectId);
        } catch (Exception e) {
            return projectId;
        }
    }

    /** Лучшая версия: сначала релиз, потом бета, потом альфа; внутри — самая свежая. */
    public static Ver pickBest(List<Ver> vs) {
        for (String t : new String[]{"release", "beta", "alpha"}) {
            Ver best = null;
            for (Ver v : vs) {
                if (!t.equals(v.type)) continue;
                if (best == null || v.published.compareTo(best.published) > 0) best = v;
            }
            if (best != null) return best;
        }
        return vs.isEmpty() ? null : vs.get(0);
    }

    // ------------------------------------------------------------------ установка проектов

    public static String subdirFor(String type) {
        switch (type) {
            case "resourcepack":
                return "resourcepacks";
            case "shader":
                return "shaderpacks";
            default:
                return "mods";
        }
    }

    /** Только для автотестов (-Dlightmods.testhost=localhost): дополнительный разрешённый хост. */
    private static final String TEST_HOST = System.getProperty("lightmods.testhost", "");

    private static void checkCdn(String url) throws IOException {
        String h = Net.host(url);
        if (!h.equals("cdn.modrinth.com") && !(!TEST_HOST.isEmpty() && h.equals(TEST_HOST))) {
            throw new IOException("Файл не с cdn.modrinth.com, пропущен из соображений безопасности: " + url);
        }
    }

    private static String safeName(String fn) {
        String n = Mc.fsSafe(fn.replace('\\', '/'));
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\\\/:*?\"<>|]", "_");
        return n.isEmpty() ? "file.jar" : n;
    }

    public static List<String> resultNotes(Set<String> notes) {
        return new ArrayList<>(notes);
    }

    /**
     * Устанавливает конкретную версию проекта в установку и (для модов) её обязательные зависимости.
     * Возвращает список предупреждений (например, не найденные зависимости).
     */
    public static List<String> installVersion(Ver v, String title, String type, Mc.Profile inst, Progress p) throws IOException {
        List<String> notes = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        doInstall(v, title, type, inst, p, seen, notes, 0);
        return notes;
    }

    private static void doInstall(Ver v, String title, String type, Mc.Profile inst, Progress p, Set<String> seen, List<String> notes, int depth) throws IOException {
        FileInfo f = v.mainFile();
        if (f == null) throw new IOException("У версии нет файлов");
        checkCdn(f.url);
        Path dir = inst.gameDir.resolve(subdirFor(type));
        Files.createDirectories(dir);
        Path dest = dir.resolve(safeName(f.filename));
        seen.add(v.projectId);
        Log.i("Скачиваю " + title + " → " + dest);
        double base = Math.min(0.9, depth * 0.2);
        Net.download(f.url, dest, f.sha1, (r, t) -> p.set(base + (t > 0 ? 0.2 * r / t : 0.1), "Скачивание: " + f.filename + (t > 0 ? " — " + (100 * r / t) + "%" : "")));
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("title", title);
        entry.put("file", dest.getFileName().toString());
        entry.put("versionId", v.id);
        entry.put("type", type);
        Mc.registryPut(inst.gameDir, v.projectId, entry);
        if (!"mod".equals(type) || depth > 6) return;
        for (Dep d : v.deps) {
            if (!"required".equals(d.type)) continue;
            String pid = d.projectId;
            if (pid == null && d.versionId != null) {
                try {
                    pid = version(d.versionId).projectId;
                } catch (IOException e) {
                    notes.add("Не удалось определить зависимость " + d.versionId);
                    continue;
                }
            }
            if (pid == null || seen.contains(pid)) continue;
            Map<String, Object> reg = Mc.registry(inst.gameDir);
            Map<String, Object> have = Json.map(reg.get(pid));
            if (!have.isEmpty() && Files.isRegularFile(dir.resolve(Json.str(have, "file", "?")))) {
                seen.add(pid);
                continue;
            }
            seen.add(pid);
            String dtitle = projectTitle(pid);
            List<Ver> dvs = versions(pid, inst.mc, loadersFor(inst.loader));
            Ver best = pickBest(dvs);
            if (best == null) {
                notes.add("Не найдена совместимая версия зависимости «" + dtitle + "» — установите её вручную");
                continue;
            }
            p.set(base + 0.2, "Зависимость: " + dtitle);
            doInstall(best, dtitle, "mod", inst, p, seen, notes, depth + 1);
        }
    }

    // ------------------------------------------------------------------ сборки (.mrpack)

    private static final Set<String> ALLOWED_PACK_HOSTS = Set.of("cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com");

    public static Mc.Profile installModpack(String title, Ver v, Progress p) throws IOException {
        FileInfo f = v.mainFile();
        if (f == null) throw new IOException("У сборки нет файла");
        checkCdn(f.url);
        Path tmpDir = Files.createTempDirectory("lightmods-pack");
        try {
            Path pack = tmpDir.resolve(safeName(f.filename));
            p.set(0.0, "Скачивание сборки «" + title + "»");
            Net.download(f.url, pack, f.sha1, (r, t) -> p.sub(0, 0.1).set(t > 0 ? (double) r / t : 0.5, "Скачивание сборки: " + (t > 0 ? (100 * r / t) + "%" : "")));
            try (ZipFile zip = new ZipFile(pack.toFile())) {
                ZipEntry idx = zip.getEntry("modrinth.index.json");
                if (idx == null) throw new IOException("В файле нет modrinth.index.json — это не сборка Modrinth (.mrpack)");
                Map<String, Object> index;
                try (InputStream in = zip.getInputStream(idx)) {
                    index = Json.map(Json.parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
                }
                Map<String, Object> deps = Json.map(index.get("dependencies"));
                String mc = Json.str(deps, "minecraft");
                if (mc == null) throw new IOException("В сборке не указана версия Minecraft");
                String loader = "vanilla";
                String loaderVer = null;
                if (deps.containsKey("fabric-loader")) {
                    loader = "fabric";
                    loaderVer = Json.str(deps, "fabric-loader");
                } else if (deps.containsKey("quilt-loader")) {
                    loader = "quilt";
                    loaderVer = Json.str(deps, "quilt-loader");
                } else if (deps.containsKey("neoforge")) {
                    loader = "neoforge";
                    loaderVer = Json.str(deps, "neoforge");
                } else if (deps.containsKey("forge")) {
                    loader = "forge";
                    loaderVer = Json.str(deps, "forge");
                }
                Log.i("Сборка «" + title + "»: Minecraft " + mc + ", загрузчик " + loader + (loaderVer != null ? " " + loaderVer : ""));
                String versionId = Mc.installLoader(loader, mc, loaderVer, p.sub(0.1, 0.35));

                Path inst = uniqueInstanceDir(title);
                Files.createDirectories(inst);
                List<Object> files = Json.list(index.get("files"));
                List<Map<String, Object>> todo = new ArrayList<>();
                for (Object o : files) {
                    Map<String, Object> fm = Json.map(o);
                    String client = Json.str(Json.map(fm.get("env")), "client", "required");
                    if ("unsupported".equals(client)) continue;
                    todo.add(fm);
                }
                ExecutorService pool = Executors.newFixedThreadPool(4);
                AtomicInteger done = new AtomicInteger();
                int total = Math.max(1, todo.size());
                List<Future<?>> futs = new ArrayList<>();
                try {
                    for (Map<String, Object> fm : todo) {
                        futs.add(pool.submit(() -> {
                            String rel = Json.str(fm, "path", "");
                            Path target = inst.resolve(rel).normalize();
                            if (rel.isEmpty() || Paths.get(rel).isAbsolute() || !target.startsWith(inst.normalize())) {
                                throw new IOException("Небезопасный путь в сборке: " + rel);
                            }
                            List<String> urls = Json.strings(fm.get("downloads"));
                            if (urls.isEmpty()) throw new IOException("Нет ссылок для " + rel);
                            String sha1 = Json.str(Json.map(fm.get("hashes")), "sha1");
                            IOException last = null;
                            for (String u : urls) {
                                boolean testOk = !TEST_HOST.isEmpty() && Net.host(u).equals(TEST_HOST);
                                if (!testOk && (!ALLOWED_PACK_HOSTS.contains(Net.host(u)) || !u.startsWith("https://"))) {
                                    last = new IOException("Источник не разрешён: " + u);
                                    continue;
                                }
                                for (int attempt = 0; attempt < 3; attempt++) {
                                    try {
                                        Net.download(u, target, sha1, null);
                                        last = null;
                                        break;
                                    } catch (IOException e) {
                                        last = new IOException(rel + ": " + e.getMessage(), e);
                                        try {
                                            Thread.sleep(700L * (attempt + 1));
                                        } catch (InterruptedException ie) {
                                            Thread.currentThread().interrupt();
                                            throw new IOException("Прервано");
                                        }
                                    }
                                }
                                if (last == null) break;
                            }
                            if (last != null) throw last;
                            int d = done.incrementAndGet();
                            p.sub(0.45, 0.45).set((double) d / total, "Файлы сборки: " + d + " / " + total);
                            return null;
                        }));
                    }
                    for (Future<?> fu : futs) {
                        try {
                            fu.get();
                        } catch (ExecutionException e) {
                            for (Future<?> x : futs) x.cancel(true);
                            Throwable c = e.getCause();
                            throw c instanceof IOException ? (IOException) c : new IOException(c);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Прервано");
                        }
                    }
                } finally {
                    pool.shutdownNow();
                }
                p.set(0.92, "Применяю файлы сборки (overrides)…");
                extractOverrides(zip, "overrides/", inst);
                extractOverrides(zip, "client-overrides/", inst);

                String id = "lightmods-" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
                Mc.upsertProfile(id, title, versionId, inst, "Furnace");
                Mc.Profile pr = new Mc.Profile();
                pr.id = id;
                pr.name = title;
                pr.versionId = versionId;
                pr.gameDir = inst;
                pr.isolated = true;
                Mc.Info info = Mc.describe(versionId);
                pr.mc = info.mc;
                pr.loader = info.loader;
                p.set(1.0, "Сборка «" + title + "» установлена");
                return pr;
            }
        } finally {
            Mc.deleteTree(tmpDir);
        }
    }

    private static Path uniqueInstanceDir(String title) {
        Path base = Mc.instancesDir();
        Path d = base.resolve(Mc.sanitize(title));
        int n = 2;
        while (Files.exists(d)) d = base.resolve(Mc.sanitize(title) + " (" + n++ + ")");
        return d;
    }

    private static void extractOverrides(ZipFile zip, String prefix, Path inst) throws IOException {
        Enumeration<? extends ZipEntry> en = zip.entries();
        Path root = inst.normalize();
        while (en.hasMoreElements()) {
            ZipEntry e = en.nextElement();
            String n = e.getName();
            if (!n.startsWith(prefix) || e.isDirectory()) continue;
            String rel = n.substring(prefix.length());
            if (rel.isEmpty()) continue;
            Path t = inst.resolve(rel).normalize();
            if (!t.startsWith(root)) {
                Log.i("Пропущен небезопасный путь в архиве: " + n);
                continue;
            }
            Files.createDirectories(t.getParent());
            try (InputStream in = zip.getInputStream(e)) {
                Files.copy(in, t, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public static String fmtCount(long n) {
        boolean en = I18n.isEn();
        if (n >= 1_000_000_000L) return String.format(Locale.ROOT, en ? "%.1fB" : "%.1f млрд", n / 1e9);
        if (n >= 1_000_000L) return String.format(Locale.ROOT, en ? "%.1fM" : "%.1f млн", n / 1e6);
        if (n >= 1_000L) return String.format(Locale.ROOT, en ? "%.1fK" : "%.1f тыс.", n / 1e3);
        return Long.toString(n);
    }
}
