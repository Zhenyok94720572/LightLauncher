package lightmods;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Локальные скины: библиотека PNG-файлов и применение выбранного скина в игре.
 * Скин подставляется ресурспаком, заменяющим стандартные скины игрока (работает для офлайн-аккаунтов,
 * у которых нет настоящего скина). Ресурспак создаётся при каждом запуске и включается в options.txt.
 */
public final class Skins {
    public static final String PACK_NAME = "LightLauncher-Skin.zip";

    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".lightmods", "skins");
    private static final Path INDEX = DIR.resolve("skins.json");

    public static final class Skin {
        public String id;
        public String name;
        public boolean slim;

        public Path file() {
            return DIR.resolve(id + ".png");
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private static final List<Skin> LIST = new ArrayList<>();
    private static final Map<String, String> ACTIVE = new LinkedHashMap<>();
    private static final List<Runnable> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();
    private static boolean loaded;

    private Skins() {
    }

    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    private static void fire() {
        for (Runnable r : LISTENERS) r.run();
    }

    // ------------------------------------------------------------------ хранилище

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        try {
            if (!Files.isRegularFile(INDEX)) return;
            Map<String, Object> m = Json.map(Json.parse(Files.readString(INDEX)));
            for (Object o : Json.list(m.get("skins"))) {
                Map<String, Object> x = Json.map(o);
                Skin s = new Skin();
                s.id = Json.str(x, "id");
                s.name = Json.str(x, "name", "Скин");
                s.slim = Json.bool(x, "slim");
                if (s.id != null && Files.isRegularFile(s.file())) LIST.add(s);
            }
            for (Map.Entry<String, Object> e : Json.map(m.get("active")).entrySet()) ACTIVE.put(e.getKey(), String.valueOf(e.getValue()));
        } catch (Exception e) {
            Log.i("Не удалось прочитать скины: " + e.getMessage());
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(DIR);
            List<Object> arr = new ArrayList<>();
            for (Skin s : LIST) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("id", s.id);
                x.put("name", s.name);
                x.put("slim", s.slim);
                arr.add(x);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("skins", arr);
            m.put("active", new LinkedHashMap<String, Object>(ACTIVE));
            Files.writeString(INDEX, Json.stringify(m, true));
        } catch (IOException e) {
            Log.i("Не удалось сохранить скины: " + e.getMessage());
        }
    }

    public static synchronized List<Skin> list() {
        ensureLoaded();
        return new ArrayList<>(LIST);
    }

    public static synchronized Skin active(String accountKey) {
        ensureLoaded();
        String id = ACTIVE.get(accountKey);
        if (id == null) return null;
        for (Skin s : LIST) if (s.id.equals(id)) return s;
        return null;
    }

    public static synchronized void setActive(String accountKey, Skin s) {
        ensureLoaded();
        if (s == null) ACTIVE.remove(accountKey);
        else ACTIVE.put(accountKey, s.id);
        save();
        fire();
    }

    public static synchronized void rename(Skin s, String name) {
        s.name = name;
        save();
        fire();
    }

    public static synchronized void setSlim(Skin s, boolean slim) {
        s.slim = slim;
        save();
        fire();
    }

    public static synchronized void remove(Skin s) {
        ensureLoaded();
        LIST.removeIf(x -> x.id.equals(s.id));
        ACTIVE.values().removeIf(v -> v.equals(s.id));
        try {
            Files.deleteIfExists(s.file());
        } catch (IOException ignored) {
            // файл останется — не страшно
        }
        save();
        fire();
    }

    // ------------------------------------------------------------------ импорт

    /** Проверяет файл скина: PNG 64x64 или 64x32 (и их увеличенные версии). Возвращает картинку или бросает IOException. */
    public static BufferedImage validate(Path f) throws IOException {
        if (Files.size(f) > 4L * 1024 * 1024) throw new IOException("Файл слишком большой для скина (больше 4 МБ)");
        BufferedImage im = ImageIO.read(f.toFile());
        if (im == null) throw new IOException("Это не изображение PNG");
        int w = im.getWidth();
        int h = im.getHeight();
        if (w < 64 || w % 64 != 0 || (h != w && h * 2 != w) || w > 1024) {
            throw new IOException("Неподходящий размер " + w + "×" + h + ". Нужен скин 64×64 или 64×32 пикселя.");
        }
        return im;
    }

    /** Эвристика: у тонких (slim) скинов правый столбец руки прозрачен. */
    public static boolean guessSlim(BufferedImage im) {
        if (im.getHeight() * 2 == im.getWidth()) return false; // старый формат — только классическая модель
        int k = im.getWidth() / 64;
        for (int y = 20 * k; y < 32 * k; y++) {
            for (int x = 54 * k; x < 56 * k; x++) {
                if (((im.getRGB(x, y) >>> 24) & 0xFF) != 0) return false;
            }
        }
        return true;
    }

    public static synchronized Skin importFile(Path f, String name) throws IOException {
        ensureLoaded();
        BufferedImage im = validate(f);
        Skin s = new Skin();
        s.id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        s.name = name == null || name.isBlank() ? "Скин" : name.trim();
        s.slim = guessSlim(im);
        Files.createDirectories(DIR);
        BufferedImage n = normalize(im);
        ImageIO.write(n, "png", s.file().toFile());
        LIST.add(s);
        save();
        fire();
        return s;
    }

    /** Приводит старый формат 64x32 к 64x64 (как делает сама игра) и гарантирует ARGB. */
    public static BufferedImage normalize(BufferedImage im) {
        int w = im.getWidth();
        int k = w / 64;
        BufferedImage out = new BufferedImage(w, w, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(im, 0, 0, null);
        g.dispose();
        if (im.getHeight() * 2 == w) {
            int[][] c = {
                {4, 16, 16, 32, 4, 4}, {8, 16, 16, 32, 4, 4}, {0, 20, 24, 32, 4, 12}, {4, 20, 16, 32, 4, 12},
                {8, 20, 8, 32, 4, 12}, {12, 20, 16, 32, 4, 12}, {44, 16, -8, 32, 4, 4}, {48, 16, -8, 32, 4, 4},
                {40, 20, 0, 32, 4, 12}, {44, 20, -8, 32, 4, 12}, {48, 20, -16, 32, 4, 12}, {52, 20, -8, 32, 4, 12}};
            for (int[] r : c) {
                for (int i = 0; i < r[4] * k; i++) {
                    for (int j = 0; j < r[5] * k; j++) {
                        int sx = r[0] * k + i;
                        int sy = r[1] * k + j;
                        int dx = (r[0] + r[2]) * k + (r[4] * k - 1 - i);
                        int dy = (r[1] + r[3]) * k + j;
                        out.setRGB(dx, dy, out.getRGB(sx, sy));
                    }
                }
            }
        }
        // у скинов без второго слоя игра игнорирует прозрачность базового слоя: делаем его непрозрачным
        int[][] opaque = {{0, 0, 32, 16}, {0, 16, 64, 16}, {16, 48, 32, 16}};
        for (int[] r : opaque) {
            for (int x = r[0] * k; x < (r[0] + r[2]) * k; x++) {
                for (int y = r[1] * k; y < (r[1] + r[3]) * k; y++) {
                    out.setRGB(x, y, out.getRGB(x, y) | 0xFF000000);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ применение в игре

    static int packFormat(String mc) {
        if (mc == null) return 69;
        String[] p = mc.split("\\.");
        try {
            if (!p[0].equals("1")) return 69;
            int minor = Integer.parseInt(p[1].replaceAll("\\D.*", ""));
            int patch = p.length > 2 ? Integer.parseInt(p[2].replaceAll("\\D.*", "")) : 0;
            if (minor <= 8) return 1;
            if (minor <= 10) return 2;
            if (minor <= 12) return 3;
            if (minor <= 14) return 4;
            if (minor == 15) return 5;
            if (minor == 16) return patch >= 2 ? 6 : 5;
            if (minor == 17) return 7;
            if (minor == 18) return 8;
            if (minor == 19) return patch >= 4 ? 13 : (patch == 3 ? 12 : 9);
            if (minor == 20) return patch >= 5 ? 32 : (patch >= 3 ? 22 : (patch == 2 ? 18 : 15));
            if (minor == 21) return patch >= 9 ? 69 : (patch >= 7 ? 64 : (patch == 6 ? 63 : (patch == 5 ? 55 : (patch == 4 ? 46 : (patch >= 2 ? 42 : 34)))));
            return 69;
        } catch (RuntimeException e) {
            return 69;
        }
    }

    private static boolean oldStyleOptions(String mc) {
        try {
            String[] p = mc.split("\\.");
            return p[0].equals("1") && Integer.parseInt(p[1].replaceAll("\\D.*", "")) <= 12;
        } catch (RuntimeException e) {
            return false;
        }
    }

    static byte[] buildPack(BufferedImage skin, String mc) throws IOException {
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(skin, "png", png);
        byte[] data = png.toByteArray();
        int pf = packFormat(mc);
        String meta = "{\"pack\":{\"pack_format\":" + pf + ",\"min_format\":1,\"max_format\":9999,"
                + "\"supported_formats\":{\"min_inclusive\":1,\"max_inclusive\":9999},"
                + "\"description\":\"Light Launcher: локальный скин\"}}";
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            z.putNextEntry(new ZipEntry("pack.mcmeta"));
            z.write(meta.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            List<String> paths = new ArrayList<>();
            // до 1.19.3
            paths.add("assets/minecraft/textures/entity/steve.png");
            paths.add("assets/minecraft/textures/entity/alex.png");
            // с 1.19.3
            for (String n : new String[]{"alex", "ari", "efe", "kai", "makena", "noor", "steve", "sunny", "zuri"}) {
                paths.add("assets/minecraft/textures/entity/player/wide/" + n + ".png");
                paths.add("assets/minecraft/textures/entity/player/slim/" + n + ".png");
            }
            for (String p : paths) {
                z.putNextEntry(new ZipEntry(p));
                z.write(data);
                z.closeEntry();
            }
        }
        return bo.toByteArray();
    }

    /** Подключает (или убирает) наш ресурспак в options.txt. */
    static void updateOptions(Path gameDir, String mc, boolean enable) throws IOException {
        Path opt = gameDir.resolve("options.txt");
        String entry = oldStyleOptions(mc) ? PACK_NAME : "file/" + PACK_NAME;
        List<String> lines = Files.isRegularFile(opt) ? new ArrayList<>(Files.readAllLines(opt, StandardCharsets.UTF_8)) : new ArrayList<>();
        int idx = -1;
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).startsWith("resourcePacks:")) idx = i;
        List<String> packs = new ArrayList<>();
        if (idx >= 0) {
            try {
                packs.addAll(Json.strings(Json.parse(lines.get(idx).substring("resourcePacks:".length()))));
            } catch (RuntimeException ignored) {
                // повреждённая строка — начнём с пустого списка
            }
        }
        packs.removeIf(x -> x.equals(entry) || x.equals(PACK_NAME) || x.equals("file/" + PACK_NAME));
        if (enable) packs.add(entry);
        if (idx < 0 && !enable) return;
        if (idx >= 0 && !enable && packs.equals(Json.strings(Json.parse(lines.get(idx).substring("resourcePacks:".length()))))) return;
        StringBuilder sb = new StringBuilder("resourcePacks:[");
        for (int i = 0; i < packs.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(Json.stringify(packs.get(i), false));
        }
        sb.append(']');
        if (idx >= 0) lines.set(idx, sb.toString());
        else lines.add(sb.toString());
        Files.write(opt, lines, StandardCharsets.UTF_8);
    }

    /** Вызывается перед запуском игры. Ошибки скина не мешают запуску. */
    public static void applyForLaunch(Mc.Profile prof, Account acc) {
        try {
            Skin s = acc.isOnline() ? null : active(acc.key());
            Path rp = prof.gameDir.resolve("resourcepacks");
            Path zip = rp.resolve(PACK_NAME);
            if (s == null) {
                Files.deleteIfExists(zip);
                updateOptions(prof.gameDir, prof.mc, false);
                return;
            }
            BufferedImage img = ImageIO.read(s.file().toFile());
            if (img == null) throw new IOException("файл скина повреждён");
            Files.createDirectories(rp);
            Path tmp = rp.resolve(PACK_NAME + ".tmp");
            Files.write(tmp, buildPack(normalize(img), prof.mc));
            Files.move(tmp, zip, StandardCopyOption.REPLACE_EXISTING);
            updateOptions(prof.gameDir, prof.mc, true);
            Log.i("Скин «" + s.name + "» подключён к установке «" + prof.name + "»");
        } catch (Exception e) {
            Log.i("Скин не применён: " + e.getMessage());
        }
    }
}
