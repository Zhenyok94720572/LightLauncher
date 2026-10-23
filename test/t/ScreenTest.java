import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

public class ScreenTest {
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcshots");
        Mc.dir = tmp.resolve(".minecraft");
        Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"latest\":{\"type\":\"latest-release\",\"lastUsed\":\"2026-01-01T00:00:00.000Z\",\"icon\":\"Grass\"}},\"settings\":{},\"version\":3}");
        Mc.installVanilla("1.21.1", Progress.NONE);
        Mc.installVanilla("1.20.1", Progress.NONE);
        String fid = Mc.installFabricLike(false, "1.21.1", "0.16.9", Progress.NONE);
        Mc.Profile p = Mc.createInstance("Light Fabric 1.21.1", fid, false, "Glowstone");
        Mc.selectedId = p.id;
        Path out = Paths.get(a[0]);
        Files.createDirectories(out);
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
        });
        final MainFrame[] fr = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> { fr[0] = new MainFrame(); fr[0].setLocation(0, 0); fr[0].setSize(1180, 760); fr[0].setVisible(true); });
        Robot r = new Robot();
        String[] pages = {"home", "mods", "packs", "textures", "shaders", "versions", "instances", "settings"};
        for (String k : pages) {
            SwingUtilities.invokeAndWait(() -> fr[0].show(k));
            Thread.sleep(k.equals("versions") ? 2500 : 1800);
            Rectangle b = fr[0].getBounds();
            BufferedImage im = r.createScreenCapture(b);
            ImageIO.write(im, "png", out.resolve(k + ".png").toFile());
            System.out.println("shot " + k);
        }
        System.exit(0);
    }
}
