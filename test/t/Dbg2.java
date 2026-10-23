import lightmods.*;
import java.awt.*;
import javax.swing.*;
public class Dbg2 {
    static void dump(Component c, int d, int max) {
        if (d > max) return;
        System.out.println("  ".repeat(d) + c.getClass().getSimpleName() + " " + c.getWidth() + "x" + c.getHeight() + " pref=" + c.getPreferredSize().width + "x" + c.getPreferredSize().height + " min=" + c.getMinimumSize().width + "x" + c.getMinimumSize().height + " vis=" + c.isVisible());
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) dump(k, d + 1, max);
    }
    public static void main(String[] a) throws Exception {
        final MainFrame[] fr = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            fr[0] = new MainFrame(); fr[0].setLocation(0,0); fr[0].setSize(1180, 760); fr[0].setVisible(true);
            fr[0].show("settings");
        });
        Thread.sleep(1500);
        SwingUtilities.invokeAndWait(() -> dump(fr[0].getContentPane(), 0, 9));
        System.exit(0);
    }
}
