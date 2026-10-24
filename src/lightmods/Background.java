package lightmods;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import javax.imageio.ImageIO;

/** Свой фон главной страницы: картинка (PNG/JPG/BMP) или анимированный GIF. Копия лежит в ~/.lightmods/background.*. */
public final class Background {
    private Background() {
    }

    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".lightmods");
    private static Image img;
    private static boolean animated;
    private static boolean loaded;
    private static BufferedImage scaled;

    private static Path file() {
        for (String e : new String[]{"png", "jpg", "jpeg", "gif", "bmp"}) {
            Path p = DIR.resolve("background." + e);
            if (Files.exists(p)) return p;
        }
        return null;
    }

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (f == null) return;
        try {
            String n = f.getFileName().toString().toLowerCase(Locale.ROOT);
            animated = n.endsWith(".gif");
            img = animated ? Toolkit.getDefaultToolkit().createImage(f.toString()) : ImageIO.read(f.toFile());
        } catch (Exception e) {
            img = null;
        }
    }

    public static boolean has() {
        load();
        return img != null;
    }

    public static boolean isAnimated() {
        load();
        return img != null && animated;
    }

    public static boolean isVideo(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        return n.matches(".*\\.(mp4|webm|mkv|avi|mov|wmv|flv|m4v)$");
    }

    /** Ставит свой фон из файла. */
    public static synchronized void set(Path src) throws IOException {
        if (isVideo(src)) throw new IOException("Видео не поддерживается: в Java нет встроенных видеокодеков. Для живого фона используйте анимированный GIF.");
        String n = src.getFileName().toString().toLowerCase(Locale.ROOT);
        String ext = n.endsWith(".gif") ? "gif" : n.endsWith(".png") ? "png" : n.endsWith(".bmp") ? "bmp" : "jpg";
        if (ext.equals("jpg") && !n.matches(".*\\.jpe?g$")) throw new IOException("Выберите картинку PNG, JPG, BMP или GIF.");
        if (ImageIO.read(src.toFile()) == null) throw new IOException("Не удалось прочитать изображение.");
        reset();
        Files.createDirectories(DIR);
        Files.copy(src, DIR.resolve("background." + ext), StandardCopyOption.REPLACE_EXISTING);
        loaded = false;
        scaled = null;
    }

    public static synchronized void reset() {
        for (String e : new String[]{"png", "jpg", "jpeg", "gif", "bmp"}) {
            try {
                Files.deleteIfExists(DIR.resolve("background." + e));
            } catch (IOException ignored) {
                // ничего
            }
        }
        img = null;
        scaled = null;
        animated = false;
        loaded = true;
    }

    /** Рисует фон «по размеру» (cover). false — свой фон не задан. */
    public static synchronized boolean paint(Graphics2D g, int w, int h, ImageObserver obs) {
        load();
        if (img == null || w <= 0 || h <= 0) return false;
        int iw = img.getWidth(null);
        int ih = img.getHeight(null);
        if (iw <= 0 || ih <= 0) return true;
        double k = Math.max((double) w / iw, (double) h / ih);
        int dw = (int) Math.ceil(iw * k);
        int dh = (int) Math.ceil(ih * k);
        if (animated) {
            g.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, obs);
            return true;
        }
        if (scaled == null || scaled.getWidth() != w || scaled.getHeight() != h) {
            BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D bg = b.createGraphics();
            bg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            bg.drawImage(img, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
            bg.dispose();
            scaled = b;
        }
        g.drawImage(scaled, 0, 0, null);
        return true;
    }
}
