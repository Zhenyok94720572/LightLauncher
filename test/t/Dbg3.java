import lightmods.*;
import java.awt.*;
import javax.swing.*;
public class Dbg3 {
    static void find(Component c, String path) {
        if (c instanceof JLabel && ((JLabel) c).getText() != null && ((JLabel) c).getText().startsWith("<html>")) {
            JLabel l = (JLabel) c;
            Container p = l.getParent();
            System.out.println(path + " label " + l.getWidth() + "x" + l.getHeight() + " pref=" + l.getPreferredSize() + " min=" + l.getMinimumSize() + " parent=" + p.getClass().getSimpleName() + " " + p.getWidth() + "x" + p.getHeight() + " layout=" + p.getLayout().getClass().getSimpleName() + " vis=" + l.isShowing() + " :: " + l.getText().replaceAll("<[^>]*>", " ").trim().substring(0, Math.min(40, l.getText().length())));
        }
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) find(k, path);
    }
    public static void main(String[] a) throws Exception {
        final MainFrame[] fr = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            fr[0] = new MainFrame(); fr[0].setLocation(0,0); fr[0].setSize(1180, 760); fr[0].setVisible(true);
        });
        Thread.sleep(800);
        for (String k : new String[]{"home", "versions"}) {
            SwingUtilities.invokeAndWait(() -> fr[0].show(k));
            Thread.sleep(2000);
            SwingUtilities.invokeAndWait(() -> find(fr[0].getContentPane(), k));
        }
        System.exit(0);
    }
}
