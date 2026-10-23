import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public class Shots2 {
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcs2");
        Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{},\"settings\":{},\"version\":3}");
        Mc.installVanilla("1.21.1", Progress.NONE);
        String fid = Mc.installFabricLike(false, "1.21.1", "0.16.9", Progress.NONE);
        Mc.selectedId = Mc.createInstance("Light Fabric 1.21.1", fid, false, "Glowstone").id;
        Path out = Paths.get(a[0]); Files.createDirectories(out);
        final MainFrame[] fr = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> { try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            fr[0] = new MainFrame(); fr[0].setLocation(0,0); fr[0].setSize(1180,760); fr[0].setVisible(true); });
        Robot r = new Robot();
        for (String k : new String[]{"home", "packs", "mods"}) {
            SwingUtilities.invokeAndWait(() -> fr[0].show(k)); Thread.sleep(2500);
            ImageIO.write(r.createScreenCapture(fr[0].getBounds()), "png", out.resolve(k + ".png").toFile());
        }
        System.exit(0);
    }
}
