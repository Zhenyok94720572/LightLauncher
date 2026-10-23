import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public class Shots6 {
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcs6"); Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"a\":{\"name\":\"Моя сборка\",\"lastVersionId\":\"1.16.5\"}},\"settings\":{},\"version\":3}");
        Mc.agreed = Agreement.VERSION;
        Account.load(); Account.addOffline("Steve_1");
        BufferedImage sk = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = sk.createGraphics();
        g.setColor(new Color(0xC69A6B)); g.fillRect(8, 8, 8, 8); g.fillRect(44, 20, 4, 12); g.fillRect(36, 52, 4, 12);
        g.setColor(new Color(0x3F2A1A)); g.fillRect(8, 8, 8, 2);
        g.setColor(Color.WHITE); g.fillRect(9, 12, 2, 1); g.fillRect(13, 12, 2, 1);
        g.setColor(new Color(0x00A8A8)); g.fillRect(20, 20, 8, 12);
        g.setColor(new Color(0x3B3B9E)); g.fillRect(4, 20, 4, 12); g.fillRect(20, 52, 4, 12);
        g.setColor(new Color(255,255,255,120)); g.fillRect(40, 8, 8, 2);
        g.dispose();
        Path f = tmp.resolve("hero.png"); ImageIO.write(sk, "png", f.toFile());
        Skins.Skin s1 = Skins.importFile(f, "Герой");
        Skins.Skin s2 = Skins.importFile(f, "Герой (копия)"); Skins.setSlim(s2, true);
        Account.Session x = null;
        Skins.setActive(Account.current().key(), s1);
        Path out = Paths.get(a[0]); Files.createDirectories(out);
        final MainFrame[] fr = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> { try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            fr[0] = new MainFrame(); fr[0].setLocation(0,0); fr[0].setSize(1180,760); fr[0].setVisible(true); fr[0].show("skins"); });
        Robot r = new Robot(); Thread.sleep(1500);
        ImageIO.write(r.createScreenCapture(fr[0].getBounds()), "png", out.resolve("skins.png").toFile());
        System.exit(0);
    }
}
