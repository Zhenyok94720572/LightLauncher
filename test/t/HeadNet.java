import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import javax.swing.*;
public class HeadNet {
    public static void main(String[] a) throws Exception {
        Account.load();
        Account ely = Account.loginEly("u", "secret", "123456");
        JPanel p = new JPanel() { protected void paintComponent(Graphics g) { Heads.paint((Graphics2D) g, 4, 4, 40, ely, this); } };
        BufferedImage im = new BufferedImage(48, 48, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < 20; i++) {
            Graphics2D g = im.createGraphics(); p.setSize(48, 48); p.paint(g); g.dispose();
            int c = im.getRGB(24, 24) & 0xFFFFFF;
            if (c == 0x1E90FF) { System.out.println("  ok   ely head from profile (blue)"); System.exit(0); }
            Thread.sleep(150);
        }
        System.out.println("  FAIL ely head, color=" + Integer.toHexString(im.getRGB(24, 24)));
        System.exit(1);
    }
}
