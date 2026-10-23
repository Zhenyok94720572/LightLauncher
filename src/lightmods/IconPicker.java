package lightmods;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Диалог выбора значка установки: встроенные пиксельные значки, свой файл или значок по умолчанию. */
public final class IconPicker {
    private IconPicker() {
    }

    private static final class Tile extends JComponent {
        final String name;
        boolean sel;

        Tile(String name) {
            this.name = name;
            setPreferredSize(new Dimension(64, 64));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(sel ? Ui.GREEN : Ui.CARD);
            g.fillRect(0, 0, getWidth(), getHeight());
            int s = Math.min(getWidth(), getHeight()) - 16;
            Icons.paintBuiltin(g, (getWidth() - s) / 2, (getHeight() - s) / 2, s, name);
            g.dispose();
        }
    }

    public static void pick(java.awt.Component parent, Mc.Profile p) {
        JPanel body = new JPanel(new java.awt.BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(Ui.label("Выберите значок для «" + p.name + "». Можно взять готовый или свою картинку (PNG/JPG).", 13f, Ui.MUTED, false), java.awt.BorderLayout.NORTH);
        JPanel grid = new JPanel(new GridLayout(2, 5, 8, 8));
        grid.setPreferredSize(new Dimension(5 * 64 + 4 * 8, 2 * 64 + 8 + 40));
        grid.setOpaque(false);
        grid.setBorder(new EmptyBorder(4, 0, 4, 0));
        List<Tile> tiles = new ArrayList<>();
        String cur = Icons.get(p.id);
        String[] chosen = {cur != null && cur.startsWith("b:") ? cur.substring(2) : null};
        for (String n : Icons.BUILTIN.keySet()) {
            Tile t = new Tile(n);
            t.sel = n.equals(chosen[0]);
            t.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    chosen[0] = n;
                    for (Tile o : tiles) {
                        o.sel = o == t;
                        o.repaint();
                    }
                }
            });
            tiles.add(t);
            grid.add(t);
        }
        body.add(grid, java.awt.BorderLayout.CENTER);
        int r = Dlg.show(parent, "Значок установки", body, new String[]{"Применить", "Свой файл…", "По умолчанию", "Отмена"}, 0);
        try {
            if (r == 0 && chosen[0] != null) {
                Icons.setBuiltin(p.id, chosen[0]);
            } else if (r == 1) {
                Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
                FileDialog fd = new FileDialog(w instanceof Frame ? (Frame) w : null, "Выберите картинку", FileDialog.LOAD);
                fd.setFilenameFilter((d, n) -> n.toLowerCase().matches(".*\\.(png|jpe?g|gif|bmp)$"));
                fd.setVisible(true);
                if (fd.getFile() == null) return;
                Icons.setFile(p.id, new File(fd.getDirectory(), fd.getFile()).toPath());
            } else if (r == 2) {
                Icons.reset(p.id);
            } else {
                return;
            }
        } catch (Exception ex) {
            Dlg.message(parent, "Не удалось поставить значок", String.valueOf(ex.getMessage()));
            return;
        }
        State.refresh();
    }
}
