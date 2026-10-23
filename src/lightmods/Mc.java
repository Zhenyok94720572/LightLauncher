package lightmods;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Всё, что касается папки .minecraft: профили лаунчера, версии, установка Vanilla/Fabric/Quilt/Forge/NeoForge. */
public final class Mc {
    // Адреса можно переопределить -Dlightmods.xxx=... (используется для тестов)
    static final String MANIFEST = System.getProperty("lightmods.manifest", "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
    static final String FABRIC_META = System.getProperty("lightmods.fabric", "https://meta.fabricmc.net/v2");
    static final String QUILT_META = System.getProperty("lightmods.quilt", "https://meta.quiltmc.org/v3");
    static final String FORGE_META = System.getProperty("lightmods.forgemeta", "https://files.minecraftforge.net/net/minecraftforge/forge");
    static final String FORGE_MAVEN = System.getProperty("lightmods.forgemaven", "https://maven.minecraftforge.net/net/minecraftforge/forge");
    static final String NEO_META = System.getProperty("lightmods.neometa", "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml");
    static final String NEO_MAVEN = System.getProperty("lightmods.neomaven", "https://maven.neoforged.net/releases/net/neoforged/neoforge");

    private static final Path CFG = Paths.get(System.getProperty("user.home"), ".lightmods", "config.json");

    public static volatile Path dir = defaultDir();
    public static volatile String selectedId;
    /** Версия принятого пользовательского соглашения (null — не принято). */
    public static volatile String agreed;
    /** Язык интерфейса: «ru», «en» или null, пока пользователь не выбрал. */
    public static volatile String lang;
    /** Настройки запуска. */
    public static volatile String javaPath = "";
    public static volatile int ramMb = 3072;
    /** Память подбирается автоматически по установке (по умолчанию включено). */
    public static volatile boolean autoRam = true;
    /** Ускоряющие флаги JVM для игры (по умолчанию включено). */
    public static volatile boolean optimizeJvm = true;
    public static volatile String jvmArgs = "";
    public static volatile String msClientId = "";

    private Mc() {
    }

    // ------------------------------------------------------------------ конфиг и пути

    /** Папка оригинального Minecraft (.minecraft) — лаунчер по умолчанию в неё не заходит. */
    public static Path officialDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return Paths.get(appdata != null ? appdata : home + "\\AppData\\Roaming", ".minecraft");
        }
        if (os.contains("mac")) return Paths.get(home, "Library", "Application Support", "minecraft");
        return Paths.get(home, ".minecraft");
    }

    /** Собственная папка Light Launcher: версии, библиотеки, ассеты, Java и все установки. */
    public static Path defaultDir() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return Paths.get(appdata != null ? appdata : home + "\\AppData\\Roaming", ".lightlauncher");
        }
        if (os.contains("mac")) return Paths.get(home, "Library", "Application Support", "lightlauncher");
        return Paths.get(home, ".lightlauncher");
    }

    /** true, если пользователь сам выбрал папку (в том числе оригинальный .minecraft). */
    public static volatile boolean customDir;

    public static void loadConfig() {
        try {
            if (Files.isRegularFile(CFG)) {
                Map<String, Object> m = Json.map(Json.parse(Files.readString(CFG)));
                String d = Json.str(m, "mcDir");
                customDir = Json.bool(m, "customDir");
                if (d != null && !d.isBlank()) {
                    Path saved = Paths.get(d);
                    // прежние версии лаунчера по умолчанию писали в оригинальный .minecraft — переезжаем в свою папку,
                    // если папку не выбирали вручную
                    if (!customDir && saved.toAbsolutePath().normalize().equals(officialDir().toAbsolutePath().normalize())) {
                        dir = defaultDir();
                    } else {
                        dir = saved;
                    }
                }
                selectedId = Json.str(m, "selected");
                agreed = Json.str(m, "agreed");
                lang = Json.str(m, "lang");
                javaPath = Json.str(m, "javaPath", "");
                jvmArgs = Json.str(m, "jvmArgs", "");
                msClientId = Json.str(m, "msClientId", "");
                if (m.containsKey("autoRam")) autoRam = Json.bool(m, "autoRam");
                if (m.containsKey("optimizeJvm")) optimizeJvm = Json.bool(m, "optimizeJvm");
                long r = Json.num(m, "ramMb");
                if (r >= 512) ramMb = (int) r;
            }
        } catch (Exception e) {
            Log.i("Не удалось прочитать конфиг: " + e.getMessage());
        }
    }

    public static void saveConfig() {
        try {
            Files.createDirectories(CFG.getParent());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("mcDir", dir.toString());
            m.put("customDir", customDir);
            if (selectedId != null) m.put("selected", selectedId);
            if (agreed != null) m.put("agreed", agreed);
            if (lang != null) m.put("lang", lang);
            m.put("javaPath", javaPath);
            m.put("jvmArgs", jvmArgs);
            m.put("msClientId", msClientId);
            m.put("ramMb", (long) ramMb);
            m.put("autoRam", autoRam);
            m.put("optimizeJvm", optimizeJvm);
            Files.writeString(CFG, Json.stringify(m, true));
        } catch (Exception e) {
            Log.i("Не удалось сохранить конфиг: " + e.getMessage());
        }
    }

    public static Path versionsDir() {
        return dir.resolve("versions");
    }

    public static Path profilesFile() {
        return dir.resolve("launcher_profiles.json");
    }

    public static Path instancesDir() {
        return dir.resolve("lightmods").resolve("instances");
    }

    private static final String[] CYR = {"a", "b", "v", "g", "d", "e", "zh", "z", "i", "y", "k", "l", "m", "n", "o", "p", "r", "s", "t", "u", "f", "h",
            "c", "ch", "sh", "sch", "", "y", "", "e", "yu", "ya"};

    private static java.nio.charset.Charset fsCharset() {
        try {
            return java.nio.charset.Charset.forName(System.getProperty("sun.jnu.encoding", "UTF-8"));
        } catch (RuntimeException e) {
            return StandardCharsets.UTF_8;
        }
    }

    /** Делает строку безопасной для имени файла в текущей файловой системе: символы, которые нельзя закодировать, заменяются. */
    public static String fsSafe(String s) {
        java.nio.charset.CharsetEncoder enc = fsCharset().newEncoder();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (enc.canEncode(ch)) {
                sb.append(ch);
                continue;
            }
            char lo = Character.toLowerCase(ch);
            String t = null;
            if (lo >= '\u0430' && lo <= '\u044f') t = CYR[lo - '\u0430'];
            else if (lo == '\u0451') t = "e";
            if (t == null) {
                sb.append('_');
            } else if (Character.isUpperCase(ch) && !t.isEmpty()) {
                sb.append(Character.toUpperCase(t.charAt(0))).append(t.substring(1));
            } else {
                sb.append(t);
            }
        }
        return sb.toString();
    }

    public static String sanitize(String s) {
        String r = s == null ? "" : s.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_").trim();
        r = fsSafe(r);
        r = r.replaceAll("\\.+$", "").trim();
        if (r.length() > 60) r = r.substring(0, 60).trim();
        return r.isEmpty() || r.matches("_+") ? "instance" : r;
    }

    // ------------------------------------------------------------------ модель профиля

    public static final class Profile {
        public String id;
        public String name;
        public String versionId;
        public String icon;
        public String lastUsed = "";
        public Path gameDir;
        public boolean isolated;
        public String mc;
        public String loader = "vanilla";

        public boolean hasLoader() {
            return !"vanilla".equals(loader);
        }

        public String loaderTitle() {
            switch (loader) {
                case "fabric":
                    return "Fabric";
                case "forge":
                    return "Forge";
                case "neoforge":
                    return "NeoForge";
                case "quilt":
                    return "Quilt";
                default:
                    return "Vanilla";
            }
        }

        public String subtitle() {
            return (mc == null ? "?" : mc) + " · " + loaderTitle();
        }

        @Override
        public String toString() {
            return name;
        }
    }

    public static final class Info {
        public String mc;
        public String loader = "vanilla";
    }

    private static final Pattern MC_START = Pattern.compile("^(\\d+\\.\\d+(?:\\.\\d+)?)");
    private static final Pattern MC_END = Pattern.compile("(\\d+\\.\\d+(?:\\.\\d+)?)$");

    public static Info describe(String versionId) {
        Info in = new Info();
        if (versionId == null) return in;
        String low = versionId.toLowerCase(Locale.ROOT);
        if (low.contains("neoforge")) in.loader = "neoforge";
        else if (low.contains("forge")) in.loader = "forge";
        else if (low.contains("fabric")) in.loader = "fabric";
        else if (low.contains("quilt")) in.loader = "quilt";
        Path j = versionsDir().resolve(versionId).resolve(versionId + ".json");
        if (Files.isRegularFile(j)) {
            try {
                Map<String, Object> m = Json.map(Json.parse(Files.readString(j)));
                String inh = Json.str(m, "inheritsFrom");
                String jar = Json.str(m, "jar");
                if (inh != null) in.mc = inh;
                else if (jar != null) in.mc = jar;
                else in.mc = Json.str(m, "id");
            } catch (Exception ignored) {
                // попробуем по имени
            }
        }
        if (in.mc == null) {
            if ("vanilla".equals(in.loader)) in.mc = versionId;
            else {
                Matcher a = MC_START.matcher(versionId);
                Matcher b = MC_END.matcher(versionId);
                if (a.find() && a.group(1).startsWith("1.")) in.mc = a.group(1);
                else if (b.find()) in.mc = b.group(1);
            }
        }
        return in;
    }

    // ------------------------------------------------------------------ launcher_profiles.json

    private static Map<String, Object> newRoot() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("profiles", new LinkedHashMap<String, Object>());
        root.put("settings", new LinkedHashMap<String, Object>());
        root.put("version", 3L);
        return root;
    }

    /** Для чтения: при ошибке возвращает пустой корень. */
    private static Map<String, Object> readRootLenient() {
        try {
            return readRootStrict();
        } catch (IOException e) {
            Log.i("launcher_profiles.json: " + e.getMessage());
            return newRoot();
        }
    }

    /** Для записи: при повреждённом файле бросает исключение, чтобы ничего не затереть. */
    private static Map<String, Object> readRootStrict() throws IOException {
        Path f = profilesFile();
        if (!Files.isRegularFile(f)) return newRoot();
        try {
            Object o = Json.parse(Files.readString(f, StandardCharsets.UTF_8));
            if (!(o instanceof Map)) throw new IOException("неожиданная структура");
            return Json.map(o);
        } catch (IllegalArgumentException e) {
            throw new IOException("Файл launcher_profiles.json повреждён и не будет изменён: " + e.getMessage());
        }
    }

    private static void writeRoot(Map<String, Object> root) throws IOException {
        Path f = profilesFile();
        Files.createDirectories(f.getParent());
        Path bak = f.resolveSibling("launcher_profiles.json.lightmods.bak");
        if (Files.isRegularFile(f) && !Files.exists(bak)) Files.copy(f, bak);
        Path tmp = f.resolveSibling("launcher_profiles.json.lightmods.tmp");
        Files.writeString(tmp, Json.stringify(root, true), StandardCharsets.UTF_8);
        Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Нужен установщику Forge/NeoForge: файл должен существовать. */
    public static synchronized void ensureProfilesFile() throws IOException {
        if (!Files.isRegularFile(profilesFile())) writeRoot(newRoot());
    }

    private static volatile String latestRelease;

    public static String latestRelease() {
        if (latestRelease == null) {
            try {
                Map<String, Object> m = Json.map(Net.getJsonCached(MANIFEST));
                latestRelease = Json.str(Json.map(m.get("latest")), "release");
            } catch (Exception ignored) {
                // нет сети
            }
        }
        return latestRelease;
    }

    public static synchronized List<Profile> readProfiles() {
        List<Profile> res = new ArrayList<>();
        Map<String, Object> root = readRootLenient();
        Map<String, Object> profs = Json.map(root.get("profiles"));
        Path inst = instancesDir().toAbsolutePath().normalize();
        for (Map.Entry<String, Object> e : profs.entrySet()) {
            Map<String, Object> p = Json.map(e.getValue());
            Profile pr = new Profile();
            pr.id = e.getKey();
            String type = Json.str(p, "type", "custom");
            String vid = Json.str(p, "lastVersionId");
            String name = Json.str(p, "name", "");
            if ("latest-release".equals(type) || "latest-release".equals(vid)) {
                vid = latestRelease();
                if (name.isBlank()) name = "Последняя версия";
            } else if ("latest-snapshot".equals(type) || "latest-snapshot".equals(vid)) {
                if (name.isBlank()) name = "Последний снапшот";
                vid = null;
            }
            if (name.isBlank()) name = vid != null ? vid : pr.id;
            pr.name = name;
            pr.versionId = vid;
            pr.icon = Json.str(p, "icon", "Grass");
            pr.lastUsed = Json.str(p, "lastUsed", "");
            String gd = Json.str(p, "gameDir");
            pr.gameDir = gd != null && !gd.isBlank() ? Paths.get(gd) : dir;
            pr.isolated = pr.gameDir.toAbsolutePath().normalize().startsWith(inst) || (gd != null && !gd.isBlank());
            Info in = describe(vid);
            pr.mc = in.mc;
            pr.loader = in.loader;
            res.add(pr);
        }
        res.sort(Comparator.comparing((Profile x) -> x.lastUsed).reversed());
        return res;
    }

    public static synchronized void upsertProfile(String id, String name, String versionId, Path gameDir, String icon) throws IOException {
        Map<String, Object> root = readRootStrict();
        Map<String, Object> profs = Json.map(root.get("profiles"));
        root.put("profiles", profs);
        Map<String, Object> p = Json.map(profs.get(id));
        String now = Instant.now().toString();
        if (!profs.containsKey(id)) p.put("created", now);
        p.put("icon", icon);
        p.put("lastUsed", now);
        p.put("lastVersionId", versionId);
        p.put("name", name);
        p.put("type", "custom");
        if (gameDir != null) p.put("gameDir", gameDir.toAbsolutePath().toString());
        profs.put(id, p);
        writeRoot(root);
    }

    public static synchronized void removeProfile(String id) throws IOException {
        Map<String, Object> root = readRootStrict();
        Map<String, Object> profs = Json.map(root.get("profiles"));
        if (profs.remove(id) != null) {
            root.put("profiles", profs);
            writeRoot(root);
        }
    }

    // ------------------------------------------------------------------ установленные версии

    public static final class Installed {
        public String id;
        public String mc;
        public String loader;

        @Override
        public String toString() {
            return id;
        }
    }

    public static List<Installed> installedVersions() {
        List<Installed> res = new ArrayList<>();
        Path vd = versionsDir();
        if (!Files.isDirectory(vd)) return res;
        try (Stream<Path> s = Files.list(vd)) {
            s.filter(Files::isDirectory).sorted().forEach(d -> {
                String id = d.getFileName().toString();
                if (Files.isRegularFile(d.resolve(id + ".json"))) {
                    Installed i = new Installed();
                    i.id = id;
                    Info in = describe(id);
                    i.mc = in.mc;
                    i.loader = in.loader;
                    res.add(i);
                }
            });
        } catch (IOException ignored) {
            // пусто
        }
        return res;
    }

    // ------------------------------------------------------------------ списки версий

    public static final class McVer {
        public String id;
        public String type;
        public String url;
        public String sha1;

        @Override
        public String toString() {
            return id;
        }
    }

    public static List<McVer> manifest() throws IOException {
        Map<String, Object> m = Json.map(Net.getJsonCached(MANIFEST));
        List<McVer> r = new ArrayList<>();
        for (Object o : Json.list(m.get("versions"))) {
            Map<String, Object> v = Json.map(o);
            McVer x = new McVer();
            x.id = Json.str(v, "id");
            x.type = Json.str(v, "type");
            x.url = Json.str(v, "url");
            x.sha1 = Json.str(v, "sha1");
            if (x.id != null && x.url != null) r.add(x);
        }
        return r;
    }

    /** Версия загрузчика для списка. {@code v} — настоящая версия, {@code label} — как показывать. */
    public static final class LV {
        public final String v;
        public final String label;

        public LV(String v, String label) {
            this.v = v;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public static List<LV> loaderVersions(String loader, String mc) throws IOException {
        List<LV> r = new ArrayList<>();
        switch (loader) {
            case "fabric":
            case "quilt": {
                boolean q = loader.equals("quilt");
                String base = (q ? QUILT_META : FABRIC_META) + "/versions/loader/" + Net.enc(mc);
                List<Object> arr = Json.list(Net.getJsonCached(base));
                List<LV> stable = new ArrayList<>();
                List<LV> all = new ArrayList<>();
                for (Object o : arr) {
                    Map<String, Object> l = Json.map(Json.map(o).get("loader"));
                    String v = Json.str(l, "version");
                    if (v == null) continue;
                    boolean isStable = !q ? Json.bool(l, "stable") : !v.contains("-");
                    LV x = new LV(v, v + (isStable ? "" : " (нестабильная)"));
                    all.add(x);
                    if (isStable) stable.add(x);
                }
                r.addAll(stable.isEmpty() ? all : stable);
                break;
            }
            case "forge": {
                Map<String, Object> meta = Json.map(Net.getJsonCached(FORGE_META + "/maven-metadata.json"));
                List<String> vs = Json.strings(meta.get(mc));
                String rec = null;
                String lat = null;
                try {
                    Map<String, Object> promos = Json.map(Json.map(Net.getJsonCached(FORGE_META + "/promotions_slim.json")).get("promos"));
                    rec = Json.str(promos, mc + "-recommended");
                    lat = Json.str(promos, mc + "-latest");
                } catch (Exception ignored) {
                    // без пометок
                }
                String prefix = mc + "-";
                for (int i = vs.size() - 1; i >= 0; i--) {
                    String full = vs.get(i);
                    String ver = full.startsWith(prefix) ? full.substring(prefix.length()) : full;
                    String label = ver;
                    if (rec != null && ver.equals(rec)) label += " (рекомендуемая)";
                    else if (lat != null && ver.equals(lat)) label += " (последняя)";
                    r.add(new LV(ver, label));
                }
                break;
            }
            case "neoforge": {
                String xml = Net.getStringCached(NEO_META);
                Matcher m = Pattern.compile("<version>([^<]+)</version>").matcher(xml);
                String pre = neoPrefix(mc);
                List<String> vs = new ArrayList<>();
                while (m.find()) if (m.group(1).startsWith(pre)) vs.add(m.group(1));
                vs.sort(Mc::compareVersions);
                Collections.reverse(vs);
                for (String v : vs) r.add(new LV(v, v + (v.contains("beta") ? " (бета)" : "")));
                break;
            }
            default:
                break;
        }
        return r;
    }

    static String neoPrefix(String mc) {
        if (mc.startsWith("1.")) {
            String[] a = mc.split("\\.");
            String patch = a.length > 2 ? a[2] : "0";
            return a[1] + "." + patch + ".";
        }
        return mc + ".";
    }

    public static int compareVersions(String a, String b) {
        String[] x = a.split("[.\\-]");
        String[] y = b.split("[.\\-]");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            String p = i < x.length ? x[i] : "0";
            String q = i < y.length ? y[i] : "0";
            boolean pn = p.matches("\\d+");
            boolean qn = q.matches("\\d+");
            int c;
            if (pn && qn) c = Long.compare(Long.parseLong(p), Long.parseLong(q));
            else if (pn) c = 1;
            else if (qn) c = -1;
            else c = p.compareTo(q);
            if (c != 0) return c;
        }
        return 0;
    }

    // ------------------------------------------------------------------ установка

    public static void installVanilla(String mc, Progress p) throws IOException {
        Path vd = versionsDir().resolve(mc);
        Path json = vd.resolve(mc + ".json");
        Path jar = vd.resolve(mc + ".jar");
        Map<String, Object> vj = null;
        if (Files.isRegularFile(json)) {
            try {
                vj = Json.map(Json.parse(Files.readString(json)));
            } catch (Exception e) {
                vj = null;
            }
        }
        if (vj == null) {
            p.set(0.0, "Версия Minecraft " + mc + ": описание");
            McVer ver = null;
            for (McVer v : manifest()) if (v.id.equals(mc)) ver = v;
            if (ver == null) throw new IOException("Версия " + mc + " не найдена в манифесте Mojang");
            Net.download(ver.url, json, ver.sha1, null);
            vj = Json.map(Json.parse(Files.readString(json)));
        }
        Map<String, Object> client = Json.map(Json.map(vj.get("downloads")).get("client"));
        String url = Json.str(client, "url");
        if (url == null) throw new IOException("У версии " + mc + " нет клиента для скачивания");
        String sha = Json.str(client, "sha1");
        if (!Files.isRegularFile(jar) || (sha != null && !sha.equalsIgnoreCase(Net.sha1(jar)))) {
            Log.i("Скачиваю клиент Minecraft " + mc);
            Net.download(url, jar, sha, (r, t) -> p.set(t > 0 ? (double) r / t : 0.5, "Minecraft " + mc + ": клиент " + (t > 0 ? (100 * r / t) + "%" : "")));
        }
        p.set(1.0, "Minecraft " + mc + " установлен");
    }

    public static String installFabricLike(boolean quilt, String mc, String loaderVer, Progress p) throws IOException {
        installVanilla(mc, p.sub(0, 0.6));
        String name = quilt ? "Quilt" : "Fabric";
        p.set(0.65, name + " " + loaderVer + ": профиль");
        String url = (quilt ? QUILT_META : FABRIC_META) + "/versions/loader/" + Net.enc(mc) + "/" + Net.enc(loaderVer) + "/profile/json";
        String text = Net.getString(url);
        Map<String, Object> m = Json.map(Json.parse(text));
        String id = Json.str(m, "id");
        if (id == null) throw new IOException("Сервер вернул профиль без id");
        Path d = versionsDir().resolve(id);
        Files.createDirectories(d);
        Files.writeString(d.resolve(id + ".json"), text, StandardCharsets.UTF_8);
        Path jar = d.resolve(id + ".jar");
        if (!Files.exists(jar)) Files.createFile(jar);
        p.set(1.0, name + " " + loaderVer + " установлен");
        Log.i(name + " установлен: " + id);
        return id;
    }

    private static Set<String> versionDirNames() {
        Set<String> s = new HashSet<>();
        Path vd = versionsDir();
        if (Files.isDirectory(vd)) {
            try (Stream<Path> st = Files.list(vd)) {
                st.filter(Files::isDirectory).forEach(x -> s.add(x.getFileName().toString()));
            } catch (IOException ignored) {
                // пусто
            }
        }
        return s;
    }

    public static String installForgeLike(boolean neo, String mc, String loaderVer, Progress p) throws IOException {
        installVanilla(mc, p.sub(0, 0.15));
        ensureProfilesFile();
        String full;
        String url;
        if (neo) {
            full = loaderVer;
            url = NEO_MAVEN + "/" + full + "/neoforge-" + full + "-installer.jar";
        } else {
            full = mc + "-" + loaderVer;
            url = FORGE_MAVEN + "/" + full + "/forge-" + full + "-installer.jar";
        }
        String title = neo ? "NeoForge" : "Forge";
        Path tmpDir = Files.createTempDirectory("lightmods-installer");
        Path installer = tmpDir.resolve((neo ? "neoforge-" : "forge-") + full + "-installer.jar");
        try {
            Log.i("Скачиваю установщик " + title + ": " + url);
            Net.download(url, installer, null, (r, t) -> p.sub(0.15, 0.2).set(t > 0 ? (double) r / t : 0.5, title + ": загрузка установщика"));
            Set<String> before = versionDirNames();
            Map<String, Long> beforeTimes = new LinkedHashMap<>();
            for (String n : before) beforeTimes.put(n, mtime(versionsDir().resolve(n)));
            String java = Paths.get(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java").toString();
            ProcessBuilder pb = new ProcessBuilder(java, "-jar", installer.toString(), "--installClient", dir.toAbsolutePath().toString());
            pb.directory(tmpDir.toFile());
            pb.redirectErrorStream(true);
            Log.i("Запускаю установщик " + title + " (это может занять несколько минут)…");
            p.set(0.4, title + ": работает установщик (скачивает библиотеки)…");
            Process proc = pb.start();
            try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int n = 0;
                while ((line = r.readLine()) != null) {
                    Log.i("[" + title + "] " + line);
                    n++;
                    p.set(Math.min(0.95, 0.4 + n * 0.004), title + ": " + trimTo(line, 70));
                }
            }
            int code;
            try {
                code = proc.waitFor();
            } catch (InterruptedException e) {
                proc.destroyForcibly();
                Thread.currentThread().interrupt();
                throw new IOException("Прервано");
            }
            if (code != 0) throw new IOException("Установщик " + title + " завершился с кодом " + code + " (подробности — в «Журнале»)");
            String id = detectNewVersion(neo, mc, full, before, beforeTimes);
            if (id == null) throw new IOException("Установщик завершился, но новая версия не найдена в папке versions");
            p.set(1.0, title + " установлен");
            Log.i(title + " установлен: " + id);
            return id;
        } finally {
            deleteTree(tmpDir);
        }
    }

    private static String detectNewVersion(boolean neo, String mc, String full, Set<String> before, Map<String, Long> times) {
        String key = neo ? "neoforge" : "forge";
        String ver = neo ? full : full.substring(mc.length() + 1);
        List<String> candidates = new ArrayList<>();
        for (String n : versionDirNames()) {
            String low = n.toLowerCase(Locale.ROOT);
            if (!low.contains(key)) continue;
            if (!neo && low.contains("neoforge")) continue;
            boolean isNew = !before.contains(n) || mtime(versionsDir().resolve(n)) > times.getOrDefault(n, 0L);
            if (!isNew) continue;
            candidates.add(n);
        }
        for (String c : candidates) if (c.contains(ver)) return c;
        if (!candidates.isEmpty()) {
            candidates.sort(Comparator.comparingLong((String c) -> mtime(versionsDir().resolve(c))).reversed());
            return candidates.get(0);
        }
        // установщик мог ничего не менять, если версия уже стояла
        for (String n : versionDirNames()) if (n.toLowerCase(Locale.ROOT).contains(key) && n.contains(ver)) return n;
        return null;
    }

    public static String installLoader(String loader, String mc, String loaderVer, Progress p) throws IOException {
        switch (loader) {
            case "vanilla":
                installVanilla(mc, p);
                return mc;
            case "fabric":
                return installFabricLike(false, mc, loaderVer, p);
            case "quilt":
                return installFabricLike(true, mc, loaderVer, p);
            case "forge":
                return installForgeLike(false, mc, loaderVer, p);
            case "neoforge":
                return installForgeLike(true, mc, loaderVer, p);
            default:
                throw new IOException("Неизвестный загрузчик: " + loader);
        }
    }

    // ------------------------------------------------------------------ установки (изоляция)

    private static Path uniqueDir(Path parent, String base) {
        Path d = parent.resolve(base);
        int n = 2;
        while (Files.exists(d)) d = parent.resolve(base + " (" + n++ + ")");
        return d;
    }

    /** Создаёт изолированную установку: отдельная папка игры + профиль в лаунчере. */
    public static Profile createInstance(String name, String versionId, boolean copySettings, String icon) throws IOException {
        Path gd = uniqueDir(instancesDir(), sanitize(name));
        Files.createDirectories(gd);
        for (String sub : new String[]{"mods", "resourcepacks", "shaderpacks", "saves"}) Files.createDirectories(gd.resolve(sub));
        if (copySettings) {
            for (String f : new String[]{"options.txt", "servers.dat", "optionsof.txt"}) {
                Path src = dir.resolve(f);
                if (Files.isRegularFile(src)) Files.copy(src, gd.resolve(f), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        String id = "lightmods-" + Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1296), 36);
        upsertProfile(id, name, versionId, gd, icon);
        Log.i("Создана изолированная установка «" + name + "» → " + gd);
        Profile p = new Profile();
        p.id = id;
        p.name = name;
        p.versionId = versionId;
        p.gameDir = gd;
        p.isolated = true;
        p.icon = icon;
        Info in = describe(versionId);
        p.mc = in.mc;
        p.loader = in.loader;
        return p;
    }

    public static void deleteInstanceFiles(Path gameDir) throws IOException {
        Path n = gameDir.toAbsolutePath().normalize();
        if (!n.startsWith(instancesDir().toAbsolutePath().normalize()) || n.equals(instancesDir().toAbsolutePath().normalize())) {
            throw new IOException("Удаление разрешено только внутри папки установок Light Launcher");
        }
        deleteTree(n);
    }

    // ------------------------------------------------------------------ реестр установленного в установке

    private static Path registryFile(Path gameDir) {
        return gameDir.resolve("lightmods.json");
    }

    public static synchronized Map<String, Object> registry(Path gameDir) {
        try {
            Path f = registryFile(gameDir);
            if (Files.isRegularFile(f)) return Json.map(Json.map(Json.parse(Files.readString(f))).get("projects"));
        } catch (Exception ignored) {
            // пустой реестр
        }
        return new LinkedHashMap<>();
    }

    public static synchronized void registryPut(Path gameDir, String projectId, Map<String, Object> entry) {
        try {
            Map<String, Object> projs = registry(gameDir);
            projs.put(projectId, entry);
            Map<String, Object> root = new LinkedHashMap<>();
            root.put("projects", projs);
            Files.createDirectories(gameDir);
            Files.writeString(registryFile(gameDir), Json.stringify(root, true));
        } catch (Exception e) {
            Log.i("Не удалось обновить реестр: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ запуск официального лаунчера

    public static boolean launchOfficial(String profileId) {
        try {
            if (profileId != null) {
                Map<String, Object> root = readRootStrict();
                Map<String, Object> profs = Json.map(root.get("profiles"));
                Map<String, Object> p = Json.map(profs.get(profileId));
                if (!p.isEmpty()) {
                    p.put("lastUsed", Instant.now().toString());
                    profs.put(profileId, p);
                    root.put("profiles", profs);
                    root.put("selectedProfile", profileId);
                    writeRoot(root);
                }
            }
        } catch (IOException e) {
            Log.i("Не удалось выбрать профиль: " + e.getMessage());
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String[]> attempts = new ArrayList<>();
        if (os.contains("win")) {
            String pf86 = System.getenv("ProgramFiles(x86)");
            String pf = System.getenv("ProgramFiles");
            if (pf86 != null) attempts.add(new String[]{pf86 + "\\Minecraft Launcher\\MinecraftLauncher.exe"});
            if (pf != null) attempts.add(new String[]{pf + "\\Minecraft Launcher\\MinecraftLauncher.exe"});
            attempts.add(new String[]{"explorer.exe", "shell:AppsFolder\\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft"});
        } else if (os.contains("mac")) {
            attempts.add(new String[]{"open", "-a", "Minecraft Launcher"});
            attempts.add(new String[]{"open", "-a", "Minecraft"});
        } else {
            attempts.add(new String[]{"minecraft-launcher"});
            attempts.add(new String[]{"flatpak", "run", "com.mojang.Minecraft"});
            attempts.add(new String[]{"mc-installer"});
        }
        for (String[] a : attempts) {
            try {
                if (a.length == 1 && a[0].contains(File.separator) && !new File(a[0]).isFile()) continue;
                new ProcessBuilder(a).redirectErrorStream(true).start();
                return true;
            } catch (IOException ignored) {
                // следующий вариант
            }
        }
        return false;
    }

    public static void openFolder(Path p) {
        try {
            Files.createDirectories(p);
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(p.toFile());
        } catch (Exception e) {
            Log.i("Не удалось открыть папку: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ утилиты

    static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static long mtime(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static String trimTo(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    public static void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) return;
        try (Stream<Path> s = Files.walk(root)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // пропускаем
                }
            });
        } catch (IOException ignored) {
            // пропускаем
        }
    }
}
