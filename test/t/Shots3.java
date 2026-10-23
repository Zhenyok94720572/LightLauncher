import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public class Shots3 {
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcs3"); Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Path out = Paths.get(a[0]); Files.createDirectories(out);
        final MainFrame[] fr = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> { try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            fr[0] = new MainFrame(); fr[0].setLocation(0,0); fr[0].setSize(1180,760); fr[0].setVisible(true); fr[0].show("home"); });
        Robot r = new Robot(); Thread.sleep(1500);
        BufferedImage s1 = r.createScreenCapture(fr[0].getBounds()); Thread.sleep(3000);
        BufferedImage s2 = r.createScreenCapture(fr[0].getBounds());
        ImageIO.write(s1, "png", out.resolve("home1.png").toFile()); ImageIO.write(s2, "png", out.resolve("home2.png").toFile());
        // увеличенный аватар: 4x
        Rectangle c = new Rectangle(270, 150, 140, 140);
        BufferedImage z = new BufferedImage(560, 560, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = z.createGraphics(); g.drawImage(s2.getSubimage(c.x, c.y, c.width, c.height), 0, 0, 560, 560, null); g.dispose();
        ImageIO.write(z, "png", out.resolve("avatar_zoom.png").toFile());
        int diff = 0; for (int x = 224; x < 1180; x += 3) for (int y = 0; y < 120; y += 3) if (s1.getRGB(x, y) != s2.getRGB(x, y)) diff++;
        System.out.println("pixels changed in cloud band: " + diff);
        System.exit(0);
    }
}
