import lightmods.*;
import java.awt.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public class Shots5 {
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcs5"); Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"a\":{\"name\":\"Моя сборка\",\"lastVersionId\":\"1.21.1\",\"lastUsed\":\"2026-02-01T00:00:00Z\"}},\"settings\":{},\"version\":3}");
        Mc.agreed = Agreement.VERSION;
        Account.load(); Account.addOffline("Steve_1");
        Path out = Paths.get(a[0]); Files.createDirectories(out);
        final MainFrame[] fr = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> { try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            fr[0] = new MainFrame(); fr[0].setLocation(0,0); fr[0].setSize(1180,760); fr[0].setVisible(true); });
        Robot r = new Robot(); Thread.sleep(1500);
        ImageIO.write(r.createScreenCapture(fr[0].getBounds()), "png", out.resolve("home.png").toFile());
        fr[0].show("settings"); Thread.sleep(600);
        ImageIO.write(r.createScreenCapture(fr[0].getBounds()), "png", out.resolve("settings.png").toFile());
        SwingUtilities.invokeLater(() -> AccountDialog.show(fr[0]));
        Thread.sleep(1000);
        ImageIO.write(r.createScreenCapture(new Rectangle(0,0,1180,760)), "png", out.resolve("accounts.png").toFile());
        System.exit(0);
    }
}
