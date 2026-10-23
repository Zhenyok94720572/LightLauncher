package lightmods;

import java.awt.Color;
import java.awt.EventQueue;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/** Точка входа Light Launcher. */
public final class App {
    private static MainFrame current;

    private App() {
    }

    /** Пересоздаёт окно (после смены языка), сохраняя положение и размер. */
    public static void restartUi() {
        MainFrame old = current;
        MainFrame f = new MainFrame();
        current = f;
        if (old != null) {
            f.setBounds(old.getBounds());
            f.setExtendedState(old.getExtendedState());
        }
        f.setVisible(true);
        if (old != null) old.dispose();
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        Mc.loadConfig();
        I18n.init(Mc.lang);
        Account.load();
        for (String a : args) {
            if (a.startsWith("--mcdir=")) Mc.dir = java.nio.file.Paths.get(a.substring(8));
        }
        EventQueue.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // стандартный вид
            }
            UIManager.put("ToolTip.background", new Color(0x1B1B1C));
            UIManager.put("ToolTip.foreground", Color.WHITE);
            UIManager.put("Panel.background", Ui.BG);
            try {
                MainFrame f = new MainFrame();
                current = f;
                f.setVisible(true);
                Agreement.show(f, () -> {
                }, () -> System.exit(0));
                Log.i("Light Launcher запущен. Папка Minecraft: " + Mc.dir);
            } catch (Throwable t) {
                t.printStackTrace();
                JOptionPane.showMessageDialog(null, I18n.t("Не удалось запустить интерфейс:\n") + t, "Light Launcher", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
