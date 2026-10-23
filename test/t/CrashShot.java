import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;
public class CrashShot {
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mccr"); Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"a\":{\"name\":\"My build\",\"lastVersionId\":\"1.16.5\"}},\"settings\":{},\"version\":3}");
        Mc.agreed = Agreement.VERSION; I18n.set(a[0]); Account.load(); Account.addOffline("Steve_1");
        java.util.ArrayDeque<String> d = new java.util.ArrayDeque<>();
        for (int i = 0; i < 30; i++) d.add("[12:00:" + i + "] [Render thread/INFO]: Loading something " + i);
        d.add("Exception in thread \"main\" java.lang.OutOfMemoryError: Java heap space");
        d.add("\tat net.minecraft.client.Main.main(Main.java:42)");
        Game.TAIL.put("a", d);
        System.out.println("diag=" + (Game.tail("a").size()));
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            MainFrame f = new MainFrame(); f.setVisible(true);
            new Thread(() -> {
                try {
                    Thread.sleep(1200);
                    SwingUtilities.invokeLater(() -> { Toast.show("Установка: Sodium — готово", Toast.Kind.OK); Toast.show("Не удалось скачать файл", Toast.Kind.ERROR); });
                    Thread.sleep(600);
                    Robot r = new Robot();
                    ImageIO.write(r.createScreenCapture(new Rectangle(f.getLocationOnScreen(), f.getSize())), "png", new java.io.File("/tmp/exp/toast.png"));
                    SwingUtilities.invokeLater(() -> CrashDialog.show(f, State.current(), -1));
                    Thread.sleep(1500);
                    ImageIO.write(r.createScreenCapture(new Rectangle(f.getLocationOnScreen(), f.getSize())), "png", new java.io.File("/tmp/exp/crash.png"));
                } catch (Exception e) { e.printStackTrace(); }
                System.exit(0);
            }).start();
        });
    }
}
