import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public class Shots8 {
    static void shot(Component c, String f) throws Exception {
        BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics(); c.paint(g); g.dispose();
        ImageIO.write(im, "png", new java.io.File(f));
    }
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcs7"); Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"a\":{\"name\":\"My build\",\"lastVersionId\":\"1.16.5\"}},\"settings\":{},\"version\":3}");
        Mc.agreed = Agreement.VERSION;
        I18n.set(a[0]);
        Stats.started("a"); Account.load(); Account.addOffline("Steve_1");
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            MainFrame f = new MainFrame(); f.setVisible(true);
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    SwingUtilities.invokeAndWait(() -> { try { shot(f.getContentPane(), "/tmp/s8_home_" + a[0] + ".png"); } catch (Exception e) { e.printStackTrace(); } });
                    java.lang.reflect.Method m = MainFrame.class.getDeclaredMethod("show", String.class); m.setAccessible(true);
                    SwingUtilities.invokeAndWait(() -> { try { m.invoke(f, "settings"); } catch (Exception e) { e.printStackTrace(); } });
                    Thread.sleep(800);
                    SwingUtilities.invokeAndWait(() -> { try { shot(f.getContentPane(), "/tmp/s8_set_" + a[0] + ".png"); } catch (Exception e) { e.printStackTrace(); } });
                    SwingUtilities.invokeAndWait(() -> { try { m.invoke(f, "instances"); } catch (Exception e) { e.printStackTrace(); } });
                    Thread.sleep(2500);
                    SwingUtilities.invokeAndWait(() -> { try { shot(f.getContentPane(), "/tmp/s8_mods_" + a[0] + ".png"); } catch (Exception e) { e.printStackTrace(); } });
                } catch (Exception e) { e.printStackTrace(); }
                SwingUtilities.invokeLater(() -> { try { Mc.Profile p = Mc.readProfiles().get(0); new Thread(() -> System.out.print("")).start(); } catch (Exception e) { e.printStackTrace(); } });
                try { Thread.sleep(1500); java.awt.Robot r = new java.awt.Robot(); BufferedImage im = r.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize())); ImageIO.write(im, "png", new java.io.File("/tmp/s8_picker.png")); } catch (Exception e) { e.printStackTrace(); }
                System.exit(0);
            }).start();
        });
    }
}
