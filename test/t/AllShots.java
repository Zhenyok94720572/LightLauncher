import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public class AllShots {
    static void shot(Component c, String f) throws Exception {
        BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics(); c.paint(g); g.dispose();
        ImageIO.write(im, "png", new java.io.File(f));
    }
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcall"); Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"a\":{\"name\":\"My build\",\"lastVersionId\":\"1.16.5\"},\"b\":{\"name\":\"Fabric 1.20.1\",\"lastVersionId\":\"fabric-loader-0.15.0-1.20.1\"}},\"settings\":{},\"version\":3}");
        Mc.agreed = Agreement.VERSION;
        I18n.set(a.length > 0 ? a[0] : "ru");
        String pre = a.length > 1 ? a[1] : "/tmp/exp/";
        Account.load(); Account.addOffline("Steve_1");
        String[] tabs = {"home","mods","packs","textures","shaders","skins","versions","instances","settings"};
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            MainFrame f = new MainFrame(); f.setSize(Integer.getInteger("w", 1180), 760); f.setVisible(true);
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    java.lang.reflect.Method m = MainFrame.class.getDeclaredMethod("show", String.class); m.setAccessible(true);
                    for (String t : tabs) {
                        SwingUtilities.invokeAndWait(() -> { try { m.invoke(f, t); } catch (Exception e) { e.printStackTrace(); } });
                        Thread.sleep(t.equals("home") ? 300 : 2200);
                        SwingUtilities.invokeAndWait(() -> { try { shot(f.getContentPane(), pre + t + ".png"); } catch (Exception e) { e.printStackTrace(); } });
                    }
                } catch (Exception e) { e.printStackTrace(); }
                System.exit(0);
            }).start();
        });
    }
}
