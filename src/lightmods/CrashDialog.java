package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.EmptyBorder;

/** Окно «Игра вылетела»: понятная подсказка по типичным причинам и хвост журнала игры. */
public final class CrashDialog {
    private CrashDialog() {
    }

    /** Подсказка по последним строкам вывода игры (null — причина не распознана). */
    static String diagnose(List<String> lines) {
        String all = String.join("\n", lines).toLowerCase(Locale.ROOT);
        if (all.contains("outofmemoryerror") || all.contains("java heap space")) {
            return "Игре не хватило памяти. Увеличьте «Память для игры» в Настройках (для сборок с модами — от 4096 МБ).";
        }
        if (all.contains("unsupportedclassversionerror") || all.contains("has been compiled by a more recent version of the java runtime")) {
            return "Мод или игра требуют более новую Java. Очистите поле «Путь к Java» в Настройках — лаунчер сам скачает подходящую.";
        }
        if (all.contains("incompatible mods found") || all.contains("mod resolution failed") || all.contains("requires") && all.contains("fabric") && all.contains("which is missing")) {
            return "Моды несовместимы или не хватает зависимостей (часто — Fabric API). Откройте «Установки» → «Содержимое» и уберите лишний мод или поставьте недостающий.";
        }
        if (all.contains("duplicatemodsfoundexception") || all.contains("duplicate mods") || all.contains("found duplicate mods")) {
            return "Один и тот же мод установлен дважды. Удалите дубликат в «Установки» → «Содержимое».";
        }
        if (all.contains("mixin apply failed") || all.contains("mixinapplyerror") || all.contains("mixin transformation")) {
            return "Один из модов конфликтует с другими (ошибка Mixin). Попробуйте отключать недавно добавленные моды по одному.";
        }
        if (all.contains("glfw error") || all.contains("pixel format not accelerated") || all.contains("opengl")) {
            return "Проблема с видеодрайвером или OpenGL. Обновите драйвер видеокарты; если стоит шейдер-мод — попробуйте без него.";
        }
        if (all.contains("could not reserve enough space") || all.contains("could not create the java virtual machine")) {
            return "Java не смогла выделить столько памяти. Уменьшите «Память для игры» в Настройках.";
        }
        return null;
    }

    public static void show(Component parent, Mc.Profile p, int code) {
        List<String> tail = Game.tail(p.id);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout(0, 6));
        top.setOpaque(false);
        Ui.Note head = new Ui.Note(640, 2, 14f, new Color(0xE6E7EB));
        head.setText(I18n.t("Minecraft («") + p.name + I18n.t("») закрылась с ошибкой, код ") + code + ".");
        top.add(head, BorderLayout.NORTH);
        String hint = diagnose(tail);
        if (hint != null) {
            JPanel hb = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(java.awt.Graphics g0) {
                    java.awt.Graphics2D g = Ui.aa(g0);
                    Ui.card(g, 0, 0, getWidth(), getHeight(), new Color(0x2A2516), new Color(0x5C4A1E));
                    g.dispose();
                }
            };
            hb.setOpaque(false);
            hb.setBorder(new EmptyBorder(10, 14, 10, 14));
            Ui.Note n = new Ui.Note(610, 2, 13.5f, new Color(0xF5D98A));
            n.setText(I18n.t("Вероятная причина: ") + I18n.t(hint));
            hb.add(n, BorderLayout.CENTER);
            top.add(hb, BorderLayout.CENTER);
        }
        body.add(top, BorderLayout.NORTH);

        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, tail.size() - 160);
        for (int i = from; i < tail.size(); i++) sb.append(tail.get(i)).append('\n');
        if (sb.length() == 0) sb.append(I18n.t("Игра ничего не вывела в журнал."));
        JTextArea ta = new JTextArea(sb.toString());
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ta.setBackground(Ui.FIELD);
        ta.setForeground(new Color(0xC9CCD4));
        ta.setCaretColor(Color.WHITE);
        ta.setBorder(new EmptyBorder(8, 10, 8, 10));
        ta.setCaretPosition(ta.getDocument().getLength());
        JScrollPane sp = new JScrollPane(ta);
        Ui.styleScroll(sp);
        sp.setBorder(new Ui.RoundBorder(Ui.BORDER, 10));
        sp.setPreferredSize(new Dimension(660, 300));
        body.add(sp, BorderLayout.CENTER);

        while (true) {
            int r = Dlg.show(parent, I18n.t("Игра вылетела"), body, new String[]{"Копировать журнал", "Папка отчётов", "Закрыть"}, 2);
            if (r == 0) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
                Toast.show("Журнал скопирован в буфер обмена");
                continue;
            }
            if (r == 1) {
                Path dir = p.gameDir.resolve("crash-reports");
                try {
                    Files.createDirectories(dir);
                    java.awt.Desktop.getDesktop().open(dir.toFile());
                } catch (Exception e) {
                    Log.i("Не удалось открыть папку: " + e.getMessage());
                }
                continue;
            }
            return;
        }
    }
}
