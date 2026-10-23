package lightmods;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

/** Выпадающий список «Установка» — как в лаунчере. Все экземпляры синхронизированы через State. */
public class InstanceCombo extends JComboBox<Mc.Profile> {
    private boolean syncing;

    public InstanceCombo() {
        Ui.styleCombo(this);
        setRenderer(new Cell());
        setPreferredSize(new Dimension(300, 46));
        addActionListener(e -> {
            if (syncing) return;
            Object o = getSelectedItem();
            if (o instanceof Mc.Profile) State.select(((Mc.Profile) o).id);
        });
        State.addListener(this::sync);
        sync();
    }

    private void sync() {
        syncing = true;
        try {
            DefaultComboBoxModel<Mc.Profile> m = new DefaultComboBoxModel<>();
            for (Mc.Profile p : State.profiles()) m.addElement(p);
            setModel(m);
            Mc.Profile cur = State.current();
            if (cur != null) {
                for (int i = 0; i < m.getSize(); i++) if (m.getElementAt(i).id.equals(cur.id)) setSelectedIndex(i);
            }
            setEnabled(m.getSize() > 0);
        } finally {
            syncing = false;
        }
    }

    private static final class Cell extends JPanel implements ListCellRenderer<Mc.Profile> {
        private Mc.Profile p;
        private boolean sel;
        private boolean inList;

        Cell() {
            setOpaque(false);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Mc.Profile> list, Mc.Profile value, int index, boolean isSelected, boolean cellHasFocus) {
            p = value;
            sel = isSelected && index >= 0;
            inList = index >= 0;
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(260, 46);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.aa(g0);
            g.setColor(sel ? Ui.GREEN : Ui.FIELD);
            g.fillRect(0, 0, getWidth(), getHeight());
            if (p == null) {
                g.setFont(Ui.font(13.5f));
                g.setColor(Ui.MUTED);
                g.drawString(I18n.t("Нет установок — создайте во вкладке «Версии»"), 12, 28);
                g.dispose();
                return;
            }
            InstanceIcon.paint(g, 8, 7, 32, p);
            g.setFont(Ui.bold(14f));
            g.setColor(Color.WHITE);
            g.drawString(Ui.ellipsize(p.name, g.getFontMetrics(), getWidth() - 60), 50, 20);
            g.setFont(Ui.font(12f));
            g.setColor(sel ? new Color(0xE8F5E2) : Ui.MUTED);
            g.drawString(I18n.t(p.subtitle() + (p.isolated ? " · изолирована" : "")), 50, 37);
            g.dispose();
        }
    }

    /** Квадратная иконка установки: цвет зависит от загрузчика. */
    public static final class InstanceIcon {
        private InstanceIcon() {
        }

        public static void paint(Graphics2D g, int x, int y, int s, Mc.Profile p) {
            Color c;
            switch (p.loader) {
                case "fabric":
                    c = new Color(0xB8A27B);
                    break;
                case "forge":
                    c = new Color(0x6F86B8);
                    break;
                case "neoforge":
                    c = new Color(0xE08A3C);
                    break;
                case "quilt":
                    c = new Color(0x9B6FD0);
                    break;
                default:
                    c = new Color(0x5BAF3C);
            }
            g.setColor(c.darker().darker());
            g.fillRect(x, y, s, s);
            g.setColor(c);
            g.fillRect(x + 2, y + 2, s - 4, s - 4);
            g.setColor(new Color(255, 255, 255, 60));
            g.fillRect(x + 2, y + 2, s - 4, 3);
            g.setColor(new Color(0, 0, 0, 40));
            int q = Math.max(4, s / 4);
            g.fillRect(x + 2 + q, y + 2 + q, q, q);
            g.fillRect(x + s - 2 - 2 * q, y + s - 2 - 2 * q, q, q);
        }
    }
}
