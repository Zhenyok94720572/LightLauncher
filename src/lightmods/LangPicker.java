package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/** Выбор языка: Eng (флаг США) / Rus (флаг России). Флаги нарисованы в коде. */
public final class LangPicker {
    private LangPicker() {
    }

    /** Рисует флаг в прямоугольнике со скруглёнными углами. */
    public static void paintFlag(Graphics2D g0, String lang, int x, int y, int w, int h) {
        Graphics2D g = (Graphics2D) g0.create();
        Shape clip = new RoundRectangle2D.Float(x, y, w, h, 8, 8);
        g.clip(clip);
        if (I18n.RU.equals(lang)) {
            g.setColor(Color.WHITE);
            g.fillRect(x, y, w, h / 3 + 1);
            g.setColor(new Color(0x0039A6));
            g.fillRect(x, y + h / 3, w, h / 3 + 1);
            g.setColor(new Color(0xD52B1E));
            g.fillRect(x, y + 2 * h / 3, w, h - 2 * h / 3);
        } else {
            for (int i = 0; i < 13; i++) {
                g.setColor(i % 2 == 0 ? new Color(0xB22234) : Color.WHITE);
                int y0 = y + Math.round(i * h / 13f);
                int y1 = y + Math.round((i + 1) * h / 13f);
                g.fillRect(x, y0, w, y1 - y0);
            }
            int cw = Math.round(w * 0.4f);
            int ch = Math.round(h * 7 / 13f);
            g.setColor(new Color(0x3C3B6E));
            g.fillRect(x, y, cw, ch);
            g.setColor(Color.WHITE);
            int r = Math.max(1, w / 60);
            for (int row = 0; row < 9; row++) {
                int n = row % 2 == 0 ? 6 : 5;
                for (int i = 0; i < n; i++) {
                    float px = x + cw * ((row % 2 == 0 ? 0.5f + i : 1f + i) / 6f);
                    float py = y + ch * (row + 0.5f) / 9f;
                    g.fillOval(Math.round(px - r), Math.round(py - r), r * 2, r * 2);
                }
            }
        }
        g.dispose();
        g0.setColor(new Color(0, 0, 0, 90));
        g0.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1, 8, 8));
    }

    private static final class Card extends JComponent {
        final String lang;
        final String title;
        boolean hover;
        boolean current;

        Card(String lang, String title, boolean current, Runnable pick) {
            this.lang = lang;
            this.title = title;
            this.current = current;
            setPreferredSize(new Dimension(190, 150));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    pick.run();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.aa(g0);
            Ui.card(g, 0, 0, getWidth() - 1, getHeight() - 1, hover ? Ui.CARD_HI : Ui.CARD, current || hover ? Ui.GREEN_HI : Ui.BORDER);
            paintFlag(g, lang, (getWidth() - 96) / 2, 18, 96, 64);
            g.setFont(Ui.bold(16f));
            g.setColor(Color.WHITE);
            int tw = g.getFontMetrics().stringWidth(title);
            g.drawString(title, (getWidth() - tw) / 2, 112);
            if (current) {
                g.setColor(Ui.GREEN_HI);
                g.setStroke(new java.awt.BasicStroke(2.2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                int cx = getWidth() / 2;
                g.drawLine(cx - 6, 130, cx - 2, 134);
                g.drawLine(cx - 2, 134, cx + 6, 125);
            }
            g.dispose();
        }
    }

    /** Показывает окно выбора; возвращает выбранный язык или null, если окно закрыто без выбора. */
    public static String choose(Component parent, String current, boolean first) {
        java.awt.Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(w, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        String[] res = {null};
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Ui.BG);
        root.setBorder(new LineBorder(Ui.BORDER, 1));
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Ui.SIDEBAR);
        head.setBorder(new EmptyBorder(12, 20, 12, 20));
        head.add(Ui.label("Language / Язык", 15f, Color.WHITE, true), BorderLayout.WEST);
        root.add(head, BorderLayout.NORTH);
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 18, 0));
        row.setOpaque(false);
        row.setBorder(new EmptyBorder(22, 22, 22, 22));
        row.add(new Card(I18n.EN, "English", I18n.EN.equals(current), () -> {
            res[0] = I18n.EN;
            d.dispose();
        }));
        row.add(new Card(I18n.RU, "Русский", I18n.RU.equals(current), () -> {
            res[0] = I18n.RU;
            d.dispose();
        }));
        root.add(row, BorderLayout.CENTER);
        d.setContentPane(root);
        if (!first) {
            d.getRootPane().registerKeyboardAction(e -> d.dispose(), javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        d.pack();
        d.setLocationRelativeTo(w);
        d.setVisible(true);
        return res[0];
    }

    /** Сохраняет язык в конфиг и применяет. */
    public static void apply(String lang) {
        I18n.set(lang);
        Mc.lang = lang;
        Mc.saveConfig();
    }
}
