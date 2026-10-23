import lightmods.*;
import java.awt.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public class Shots4 {
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcs4"); Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"a\":{\"name\":\"Моя сборка\",\"lastVersionId\":\"1.21.1\",\"lastUsed\":\"2026-02-01T00:00:00Z\"},\"b\":{\"name\":\"Fabric 1.20.1\",\"lastVersionId\":\"fabric-loader-0.15-1.20.1\",\"gameDir\":\"/x/y\",\"lastUsed\":\"2026-01-01T00:00:00Z\"}},\"settings\":{},\"version\":3}");
        Path out = Paths.get(a[0]); Files.createDirectories(out);
        final MainFrame[] fr = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> { try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            fr[0] = new MainFrame(); fr[0].setLocation(0,0); fr[0].setSize(1180,760); fr[0].setVisible(true); fr[0].show("instances"); });
        Robot r = new Robot(); Thread.sleep(1500);
        ImageIO.write(r.createScreenCapture(fr[0].getBounds()), "png", out.resolve("instances.png").toFile());
        SwingUtilities.invokeAndWait(() -> Agreement.show(fr[0], () -> System.out.println("accepted"), () -> System.out.println("declined")));
        Thread.sleep(250); ImageIO.write(r.createScreenCapture(fr[0].getBounds()), "png", out.resolve("agr_fade.png").toFile());
        Thread.sleep(1200); ImageIO.write(r.createScreenCapture(fr[0].getBounds()), "png", out.resolve("agr.png").toFile());
        // принять: найти чекбокс и кнопку
        java.util.List<Component> l = new java.util.ArrayList<>();
        SwingUtilities.invokeAndWait(() -> { collect(fr[0].getGlassPane(), l); });
        for (Component c : l) if (c instanceof JCheckBox) SwingUtilities.invokeAndWait(((JCheckBox) c)::doClick);
        for (Component c : l) if (c instanceof Ui.McButton && ((Ui.McButton) c).getText().equals("Принять")) { System.out.println("accept enabled=" + c.isEnabled()); SwingUtilities.invokeAndWait(((Ui.McButton) c)::doClick); }
        Thread.sleep(500);
        System.out.println("agreed=" + Mc.agreed + " glassVisible=" + fr[0].getGlassPane().isVisible());
        System.exit(0);
    }
    static void collect(Component c, java.util.List<Component> out) { out.add(c); if (c instanceof Container) for (Component k : ((Container) c).getComponents()) collect(k, out); }
}
