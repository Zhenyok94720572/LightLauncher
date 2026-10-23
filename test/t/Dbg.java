import lightmods.*;
import java.awt.*;
import javax.swing.*;
public class Dbg {
    static void dump(Component c, int d) {
        if (d > 5) return;
        String n = c.getClass().getSimpleName();
        System.out.println("  ".repeat(d) + n + " size=" + c.getWidth() + "x" + c.getHeight() + " pref=" + c.getPreferredSize().width + "x" + c.getPreferredSize().height + " min=" + c.getMinimumSize().height);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) dump(k, d + 1);
    }
    public static void main(String[] a) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            JFrame f = new JFrame(); f.setSize(1180, 760);
            VersionsPanel v = new VersionsPanel();
            f.add(v); f.setVisible(true);
            f.validate();
            dump(v, 0);
            System.exit(0);
        });
    }
}
