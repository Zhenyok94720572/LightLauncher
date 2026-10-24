package lightmods;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;

/** Пользовательские значки установок: встроенные пиксельные или свой файл. Хранится в ~/.lightmods. */
public final class Icons {
    private Icons() {
    }

    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".lightmods");
    private static final Path FILE = DIR.resolve("icons.json");
    private static final Path IMG_DIR = DIR.resolve("icons");
    private static final Map<String, String> SPEC = new HashMap<>();
    private static final Map<String, BufferedImage> IMG = new HashMap<>();
    private static boolean loaded;

    /** Встроенные значки 8x8: символы — индексы цветов палитры, '.' — прозрачно. */
    public static final Map<String, String[]> BUILTIN = new LinkedHashMap<>();
    private static final Map<String, int[]> COLORS = new HashMap<>();

    private static void def(String id, int[] pal, String... rows) {
        BUILTIN.put(id, rows);
        COLORS.put(id, pal);
    }

    static {
        def("sword", new int[]{0xCFD8DC, 0x8D6E63, 0x607D8B},
            "......00", ".....00.", "....00..", "2..00...", ".200....", "..21....", ".1.2....", "1.......");
        def("flame", new int[]{0xFF7043, 0xFFCA28, 0xB71C1C},
            "...0....", "...00...", "..000.0.", ".0001.00", ".00110.0", ".01111.0", "..111.0.", "...22...");
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try {
            if (Files.exists(FILE)) {
                Object o = Json.parse(new String(Files.readAllBytes(FILE), java.nio.charset.StandardCharsets.UTF_8));
                if (o instanceof Map) {
                    for (Map.Entry<?, ?> e : ((Map<?, ?>) o).entrySet()) SPEC.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
                }
            }
        } catch (Exception ignored) {
            // повреждённый файл — начнём с пустого
        }
    }

    private static void save() {
        try {
            Files.createDirectories(DIR);
            Files.write(FILE, Json.stringify(new LinkedHashMap<String, Object>(SPEC), false).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // не критично
        }
    }

    /** Текущий значок установки: "b:имя", "file" или null (по умолчанию). */
    public static synchronized String get(String profileId) {
        load();
        return SPEC.get(profileId);
    }

    public static synchronized void setBuiltin(String profileId, String name) {
        load();
        SPEC.put(profileId, "b:" + name);
        IMG.remove(profileId);
        save();
    }

    public static synchronized void reset(String profileId) {
        load();
        SPEC.remove(profileId);
        IMG.remove(profileId);
        try {
            Files.deleteIfExists(IMG_DIR.resolve(profileId + ".png"));
        } catch (IOException ignored) {
            // ничего
        }
        save();
    }

    /** Свой значок из картинки: обрезается по центру до квадрата и сжимается до 64x64. */
    public static synchronized void setFile(String profileId, Path src) throws IOException {
        load();
        BufferedImage in = ImageIO.read(src.toFile());
        if (in == null) throw new IOException("Не удалось прочитать изображение");
        int s = Math.min(in.getWidth(), in.getHeight());
        BufferedImage sq = in.getSubimage((in.getWidth() - s) / 2, (in.getHeight() - s) / 2, s, s);
        BufferedImage out = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(sq, 0, 0, 64, 64, null);
        g.dispose();
        Files.createDirectories(IMG_DIR);
        Path tmp = IMG_DIR.resolve(profileId + ".tmp");
        ImageIO.write(out, "png", tmp.toFile());
        Files.move(tmp, IMG_DIR.resolve(profileId + ".png"), StandardCopyOption.REPLACE_EXISTING);
        SPEC.put(profileId, "file");
        IMG.put(profileId, out);
        save();
    }

    private static synchronized BufferedImage fileImage(String id) {
        BufferedImage b = IMG.get(id);
        if (b == null) {
            try {
                b = ImageIO.read(IMG_DIR.resolve(id + ".png").toFile());
            } catch (Exception e) {
                b = null;
            }
            if (b != null) IMG.put(id, b);
        }
        return b;
    }

    /** Рисует значок установки; false — значок не задан (рисуйте стандартный). */
    public static boolean paint(Graphics2D g, int x, int y, int s, String profileId) {
        String sp = get(profileId);
        if (sp == null) return false;
        if (sp.equals("file")) {
            BufferedImage b = fileImage(profileId);
            if (b == null) return false;
            Object old = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(b, x, y, s, s, null);
            if (old != null) g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, old);
            return true;
        }
        if (sp.startsWith("b:")) return paintBuiltin(g, x, y, s, sp.substring(2));
        return false;
    }

    public static boolean paintBuiltin(Graphics2D g, int x, int y, int s, String name) {
        String[] rows = BUILTIN.get(name);
        if (rows == null) return false;
        int[] pal = COLORS.get(name);
        g.setColor(new Color(0x1B1C21));
        g.fillRect(x, y, s, s);
        g.setColor(new Color(0x363943));
        g.drawRect(x, y, s - 1, s - 1);
        int pad = Math.max(1, s / 12);
        int in = s - 2 * pad;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                char ch = rows[r].charAt(c);
                if (ch == '.') continue;
                g.setColor(new Color(pal[ch - '0']));
                int x0 = x + pad + c * in / 8, x1 = x + pad + (c + 1) * in / 8;
                int y0 = y + pad + r * in / 8, y1 = y + pad + (r + 1) * in / 8;
                g.fillRect(x0, y0, x1 - x0, y1 - y0);
            }
        }
        return true;
    }
}
