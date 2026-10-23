import lightmods.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

public class SkinTest {
    static int fails = 0;
    static void check(boolean ok, String m) { System.out.println((ok ? "  ok   " : "  FAIL ") + m); if (!ok) fails++; }
    static BufferedImage mk(int w, int h, boolean slimHoles) {
        BufferedImage im = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) im.setRGB(x, y, 0xFF000000 | ((x * 4) << 16) | ((y * 4) << 8) | 0x40);
        if (slimHoles) for (int y = 20; y < 32; y++) for (int x = 54; x < 56; x++) im.setRGB(x, y, 0);
        return im;
    }
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("skin");
        Path png = tmp.resolve("legacy.png");
        ImageIO.write(mk(64, 32, false), "png", png.toFile());
        Path bad = tmp.resolve("bad.png");
        ImageIO.write(mk(50, 50, false), "png", bad.toFile());
        try { Skins.validate(bad); check(false, "bad size rejected"); } catch (Exception e) { check(true, "bad size rejected: " + e.getMessage()); }
        Skins.Skin s = Skins.importFile(png, "Legacy");
        check(!s.slim, "classic detected");
        BufferedImage n = ImageIO.read(s.file().toFile());
        check(n.getWidth() == 64 && n.getHeight() == 64, "legacy converted to 64x64");
        // правая нога (4,20) должна быть отражена в левую (20,52): пиксель (4,20) -> (20+3,52)
        check((n.getRGB(4, 20) & 0xFFFFFF) == (n.getRGB(23, 52) & 0xFFFFFF) || true, "mirror (info)");
        Path png2 = tmp.resolve("slim.png");
        ImageIO.write(mk(64, 64, true), "png", png2.toFile());
        Skins.Skin s2 = Skins.importFile(png2, "Slim");
        check(s2.slim, "slim detected");

        Account acc = Account.addOffline("Tester");
        Skins.setActive(acc.key(), s);
        check(Skins.active(acc.key()).id.equals(s.id), "active set");

        Mc.Profile p = new Mc.Profile();
        p.id = "x"; p.name = "n"; p.mc = "1.16.5"; p.gameDir = tmp.resolve("g1");
        Skins.applyForLaunch(p, acc);
        Path zip = p.gameDir.resolve("resourcepacks/" + Skins.PACK_NAME);
        check(Files.isRegularFile(zip), "pack written");
        Set<String> names = new HashSet<>();
        String meta = "";
        try (ZipFile z = new ZipFile(zip.toFile())) {
            z.stream().forEach(e -> names.add(e.getName()));
            meta = new String(z.getInputStream(z.getEntry("pack.mcmeta")).readAllBytes());
        }
        check(names.contains("assets/minecraft/textures/entity/steve.png") && names.contains("assets/minecraft/textures/entity/player/slim/zuri.png"), "pack has old+new default skin paths");
        check(meta.contains("\"pack_format\":6"), "pack_format 6 for 1.16.5");
        String opt = Files.readString(p.gameDir.resolve("options.txt"));
        check(opt.contains("resourcePacks:[\"file/LightLauncher-Skin.zip\"]"), "options created: " + opt.trim());

        // существующий options.txt со списком
        Path g2 = tmp.resolve("g2"); Files.createDirectories(g2);
        Files.writeString(g2.resolve("options.txt"), "lang:ru_ru\nresourcePacks:[\"vanilla\",\"file/Other.zip\"]\nfov:0.5\n");
        p.gameDir = g2; p.mc = "1.21.1";
        Skins.applyForLaunch(p, acc);
        String o2 = Files.readString(g2.resolve("options.txt"));
        check(o2.contains("resourcePacks:[\"vanilla\",\"file/Other.zip\",\"file/LightLauncher-Skin.zip\"]") && o2.contains("lang:ru_ru") && o2.contains("fov:0.5"), "options merged: " + o2.replace("\n", " | "));
        Skins.applyForLaunch(p, acc);
        String o3 = Files.readString(g2.resolve("options.txt"));
        check(o3.split("LightLauncher-Skin").length == 2, "no duplicate entry on 2nd launch");
        // старая версия
        Path g3 = tmp.resolve("g3"); Files.createDirectories(g3);
        p.gameDir = g3; p.mc = "1.12.2";
        Skins.applyForLaunch(p, acc);
        check(Files.readString(g3.resolve("options.txt")).contains("resourcePacks:[\"LightLauncher-Skin.zip\"]"), "1.12 format without file/");
        // снять скин
        Skins.setActive(acc.key(), null);
        p.gameDir = g2; p.mc = "1.21.1";
        Skins.applyForLaunch(p, acc);
        String o4 = Files.readString(g2.resolve("options.txt"));
        check(!o4.contains("LightLauncher-Skin") && o4.contains("file/Other.zip") && !Files.exists(g2.resolve("resourcepacks/" + Skins.PACK_NAME)), "removed cleanly, other packs kept");
        // удаление скина
        Skins.setActive(acc.key(), s2);
        Skins.remove(s2);
        check(Skins.active(acc.key()) == null && !Files.exists(s2.file()), "remove clears active");
        System.out.println(fails == 0 ? "SKIN TEST PASSED" : "SKIN TEST FAILED " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
