package lightmods;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import javax.imageio.ImageIO;

/** Голова скина аккаунта: свой скин из библиотеки или скин с сервера (Microsoft / Ely.by). Нет скина — плитка с буквой ника. */
public final class Heads {
    private Heads() {
    }

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();
    private static final Set<String> LOADING = ConcurrentHashMap.newKeySet();
    private static final Set<String> MISSING = ConcurrentHashMap.newKeySet();

    // ------------------------------------------------------------------ вырезание головы

    /** Лицо (8×8) вместе со вторым слоем (шляпа/волосы). */
    static BufferedImage headOf(BufferedImage skin) {
        int k = Math.max(1, skin.getWidth() / 64);
        BufferedImage out = new BufferedImage(8 * k, 8 * k, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(skin, 0, 0, 8 * k, 8 * k, 8 * k, 8 * k, 16 * k, 16 * k, null);
        g.drawImage(skin, 0, 0, 8 * k, 8 * k, 40 * k, 8 * k, 48 * k, 16 * k, null);
        g.dispose();
        // лицо непрозрачное, как в игре
        for (int y = 0; y < out.getHeight(); y++) {
            for (int x = 0; x < out.getWidth(); x++) {
                int base = skin.getRGB(8 * k + x, 8 * k + y);
                if (((out.getRGB(x, y) >>> 24) & 0xFF) == 0) out.setRGB(x, y, base | 0xFF000000);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ получение

    /** Картинка головы для аккаунта; null — пока грузится (будет вызван repaint компонента). */
    private static BufferedImage image(Account a, Component repaint) {
        if (a == null) return null;
        if (!a.isOnline()) {
            Skins.Skin s = Skins.active(a.key());
            if (s == null) return null;
            String key = "local:" + s.id;
            BufferedImage c = CACHE.get(key);
            if (c != null) return c;
            try {
                BufferedImage im = headOf(ImageIO.read(s.file().toFile()));
                CACHE.put(key, im);
                return im;
            } catch (Exception e) {
                return null;
            }
        }
        String key = a.key();
        BufferedImage c = CACHE.get(key);
        if (c != null) return c;
        Long failedAt = RETRY.get(key);
        if (failedAt != null && System.currentTimeMillis() - failedAt > 60_000) {
            RETRY.remove(key);
            MISSING.remove(key);
        }
        if (!MISSING.contains(key) && LOADING.add(key)) {
            Tasks.IO.submit(() -> {
                try {
                    BufferedImage skin = fetch(a);
                    if (skin != null) CACHE.put(key, headOf(skin));
                    else {
                        Log.i("У аккаунта «" + a.name + "» нет скина на сервере — показываем букву ника");
                        MISSING.add(key);
                        RETRY.put(key, System.currentTimeMillis());
                    }
                } catch (Exception e) {
                    Log.i("Не удалось загрузить скин аккаунта «" + a.name + "»: " + e);
                    MISSING.add(key);
                    RETRY.put(key, System.currentTimeMillis());
                } finally {
                    LOADING.remove(key);
                    if (repaint != null) javax.swing.SwingUtilities.invokeLater(repaint::repaint);
                }
            });
        }
        return null;
    }

    /** Адрес скина из ответа сервера профилей (свойство textures, base64). */
    static String skinUrl(Object profile) {
        for (Object p : Json.list(Json.map(profile).get("properties"))) {
            Map<String, Object> pm = Json.map(p);
            if (!"textures".equals(Json.str(pm, "name"))) continue;
            String json = new String(Base64.getDecoder().decode(Json.str(pm, "value")), StandardCharsets.UTF_8);
            Map<String, Object> tex = Json.map(Json.map(Json.map(Json.parse(json)).get("textures")).get("SKIN"));
            String url = Json.str(tex, "url");
            if (url != null) return url;
        }
        return null;
    }

    private static BufferedImage read(String url) throws Exception {
        BufferedImage im = ImageIO.read(new ByteArrayInputStream(Net.getBytes(url)));
        if (im == null) throw new java.io.IOException("не картинка: " + url);
        return im;
    }

    private static BufferedImage fetch(Account a) throws Exception {
        String uuid = a.uuid == null ? null : a.uuid.replace("-", "");
        if (a.isEly()) {
            // тот же источник, что и у игры: профиль на сервере Ely.by
            if (uuid != null) {
                try {
                    String url = skinUrl(Net.getJson(Account.ELY_API + "/sessionserver/session/minecraft/profile/" + uuid));
                    if (url != null) return read(url);
                } catch (Exception e) {
                    Log.i("Скин Ely.by через профиль: " + e.getMessage());
                }
            }
            for (String u : new String[]{"http://skinsystem.ely.by/skins/" + Net.enc(a.name) + ".png", "https://skinsystem.ely.by/skins/" + Net.enc(a.name) + ".png"}) {
                try {
                    return read(u);
                } catch (Exception e) {
                    Log.i("Скин Ely.by " + u + ": " + e.getMessage());
                }
            }
            return null;
        }
        if (uuid == null) return null;
        String url = skinUrl(Net.getJson(MOJANG_PROFILE + uuid));
        return url == null ? null : read(url);
    }

    static final String MOJANG_PROFILE = System.getProperty("lightmods.profile", "https://sessionserver.mojang.com/session/minecraft/profile/");
    private static final java.util.Map<String, Long> RETRY = new ConcurrentHashMap<>();

    /** Забыть загруженные с сервера скины (после смены скина в профиле). */
    public static void refresh() {
        CACHE.keySet().removeIf(k -> !k.startsWith("local:"));
        MISSING.clear();
        RETRY.clear();
    }

    // ------------------------------------------------------------------ рисование

    /** Нет своего скина — плитка с первой буквой ника. */
    private static void paintLetter(Graphics2D g0, int x, int y, int s, Account a) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        String nm = a == null || a.name == null || a.name.isEmpty() ? "?" : a.name;
        int hv = Math.abs(nm.hashCode());
        double r = Math.max(4, s * 0.2);
        g.setColor(Color.getHSBColor((hv % 360) / 360f, 0.45f, 0.55f));
        g.fill(new RoundRectangle2D.Double(x, y, s, s, r, r));
        g.setColor(Color.WHITE);
        g.setFont(Ui.bold(s * 0.5f));
        java.awt.FontMetrics fm = g.getFontMetrics();
        String l = nm.substring(0, 1).toUpperCase();
        g.drawString(l, x + (s - fm.stringWidth(l)) / 2, y + (s - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }

    public static void paint(Graphics2D g0, int x, int y, int s, Account a, Component repaint) {
        BufferedImage im = image(a, repaint);
        if (im == null) {
            paintLetter(g0, x, y, s, a);
            return;
        }
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        double r = Math.max(4, s * 0.2);
        // тень и подложка
        g.setColor(new Color(0, 0, 0, 70));
        g.fill(new RoundRectangle2D.Double(x, y + 1, s, s, r, r));
        g.clip(new RoundRectangle2D.Double(x, y, s, s, r, r));
        g.drawImage(im, x, y, s, s, null);
        g.setClip(null);
        g.setColor(new Color(255, 255, 255, 40));
        g.setStroke(new java.awt.BasicStroke(1f));
        g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, s - 1, s - 1, r, r));
        g.dispose();
    }
}
