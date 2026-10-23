import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public class HeadShot {
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mchd"); Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"a\":{\"name\":\"My build\",\"lastVersionId\":\"1.16.5\"}},\"settings\":{},\"version\":3}");
        Mc.agreed = Agreement.VERSION; I18n.set("ru"); Account.load();
        Account.addOffline("Steve_1"); Account.addOffline("Alexa"); Account.addOffline("Notch");
        // свой скин: цветное лицо 64x64
        BufferedImage sk = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) sk.setRGB(x, y, 0xFF2E7D32);
        for (int y = 8; y < 16; y++) for (int x = 8; x < 16; x++) sk.setRGB(x, y, (x + y) % 2 == 0 ? 0xFFFFC107 : 0xFFE91E63);
        for (int y = 8; y < 16; y++) for (int x = 40; x < 48; x++) sk.setRGB(x, y, y == 8 ? 0xFF1A237E : 0);
        Path f = tmp.resolve("sk.png"); ImageIO.write(sk, "png", f.toFile());
        Skins.Skin s = Skins.importFile(f, "Тест");
        Account notch = null; for (Account x : Account.all()) if (x.name.equals("Notch")) notch = x;
        Skins.setActive(notch.key(), s);
        final Account fn = notch;
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            MainFrame fr = new MainFrame(); fr.setVisible(true);
            new Thread(() -> {
                try {
                    Thread.sleep(1200);
                    Robot r = new Robot();
                    for (String n : new String[]{"Steve_1", "Alexa", "Notch"}) {
                        for (Account x : Account.all()) if (x.name.equals(n)) Account.select(x);
                        Thread.sleep(500);
                        Rectangle rc = new Rectangle(fr.getLocationOnScreen().x, fr.getLocationOnScreen().y + 600, 230, 120);
                        ImageIO.write(r.createScreenCapture(rc), "png", new java.io.File("/tmp/exp/head_" + n + ".png"));
                    }
                    SwingUtilities.invokeLater(() -> AccountDialog.show(fr));
                    Thread.sleep(1200);
                    ImageIO.write(r.createScreenCapture(new Rectangle(fr.getLocationOnScreen(), fr.getSize())), "png", new java.io.File("/tmp/exp/head_dlg.png"));
                } catch (Exception e) { e.printStackTrace(); }
                System.exit(0);
            }).start();
        });
    }
}
