package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;

/** Локальные скины: библиотека PNG, превью персонажа, выбор скина для текущего аккаунта. */
public final class SkinsPanel extends JPanel {
    private final JPanel list = new JPanel();
    private final Ui.Note info = new Ui.Note(820, 3, 13f, Ui.MUTED);

    public SkinsPanel() {
        super(new BorderLayout(0, 12));
        setOpaque(false);
        setBorder(new EmptyBorder(6, 26, 14, 26));

        JPanel top = new JPanel(new BorderLayout(14, 0));
        top.setOpaque(false);
        top.add(info, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btns.setOpaque(false);
        Ui.McButton add = new Ui.McButton("Добавить скин…", Ui.McButton.Kind.GREEN).size(13.5f);
        add.setPreferredSize(new Dimension(170, 40));
        add.addActionListener(e -> addSkin());
        Ui.McButton none = new Ui.McButton("Без скина", Ui.McButton.Kind.DARK).size(13.5f);
        none.setPreferredSize(new Dimension(120, 40));
        none.addActionListener(e -> {
            Account a = Account.current();
            if (a != null) Skins.setActive(a.key(), null);
        });
        btns.add(none);
        btns.add(add);
        top.add(btns, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(list, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(wrap, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Ui.styleScroll(sp);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(24);
        add(sp, BorderLayout.CENTER);

        Skins.addListener(() -> javax.swing.SwingUtilities.invokeLater(this::rebuild));
        Account.addListener(() -> javax.swing.SwingUtilities.invokeLater(this::rebuild));
        rebuild();
    }

    private void addSkin() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("Скины (PNG)", "png"));
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File f = fc.getSelectedFile();
        String base = f.getName().replaceFirst("(?i)\\.png$", "");
        try {
            Skins.validate(f.toPath());
        } catch (Exception e) {
            Dlg.message(this, "Не удалось добавить скин", e.getMessage());
            return;
        }
        String name = Dlg.input(this, "Название скина", "Как назвать этот скин?", base);
        if (name == null) return;
        try {
            Skins.Skin s = Skins.importFile(f.toPath(), name.isEmpty() ? base : name);
            Account a = Account.current();
            if (a != null && !a.isOnline()) Skins.setActive(a.key(), s);
            Tasks.bar.info("Скин «" + s.name + "» добавлен");
        } catch (Exception e) {
            Dlg.message(this, "Не удалось добавить скин", Tasks.friendly(e));
        }
    }

    private void rebuild() {
        Account acc = Account.current();
        if (acc == null) info.setText("Сначала добавьте аккаунт (слева внизу). Скины назначаются каждому аккаунту отдельно.");
        else if (acc.isOnline()) info.setText("Аккаунт «" + acc.name + "» — онлайн-аккаунт: в игре будет ваш скин с сервера Mojang / Ely.by. Локальные скины работают для офлайн-аккаунтов.");
        else info.setText("Скин для офлайн-аккаунта «" + acc.name + "». Он виден только вам, на этом компьютере; при запуске игры подключается ресурспаком " + Skins.PACK_NAME + ".");
        list.removeAll();
        List<Skins.Skin> all = Skins.list();
        Skins.Skin act = acc == null ? null : Skins.active(acc.key());
        if (all.isEmpty()) {
            list.add(Ui.label("Скинов пока нет. Нажмите «Добавить скин…» и выберите PNG-файл 64×64 (или 64×32).", 14f, Ui.MUTED, false));
        }
        for (Skins.Skin s : all) {
            list.add(new Row(s, act != null && act.id.equals(s.id), acc));
            list.add(Box.createVerticalStrut(8));
        }
        list.revalidate();
        list.repaint();
    }

    private final class Row extends JPanel {
        private final boolean active;

        Row(Skins.Skin s, boolean active, Account acc) {
            super(new BorderLayout(16, 0));
            this.active = active;
            setOpaque(false);
            setBorder(new EmptyBorder(10, 14, 10, 16));
            setPreferredSize(new Dimension(100, 132));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 132));
            BufferedImage img = null;
            try {
                img = ImageIO.read(s.file().toFile());
            } catch (Exception ignored) {
                // покажем пустое превью
            }
            add(new Figure(img, s.slim), BorderLayout.WEST);

            JPanel mid = new JPanel(new BorderLayout(0, 4));
            mid.setOpaque(false);
            JPanel t = new JPanel(new java.awt.GridLayout(3, 1, 0, 2));
            t.setOpaque(false);
            t.add(Ui.label(s.name, 16f, Color.WHITE, true));
            t.add(Ui.label((s.slim ? "Тонкие руки (Alex)" : "Классические руки (Steve)") + (img == null ? "" : " · " + img.getWidth() + "×" + img.getHeight()), 12.5f, Ui.MUTED, false));
            t.add(Ui.label(active ? "Используется" : " ", 13f, Ui.GREEN_HI, true));
            mid.add(t, BorderLayout.NORTH);
            add(mid, BorderLayout.CENTER);

            JPanel b = new JPanel(new java.awt.GridLayout(2, 2, 8, 8));
            b.setOpaque(false);
            Ui.McButton use = new Ui.McButton(active ? "Выбран" : "Использовать", active ? Ui.McButton.Kind.DARK : Ui.McButton.Kind.GREEN).size(13f);
            use.setEnabled(!active && acc != null);
            use.addActionListener(e -> Skins.setActive(acc.key(), s));
            Ui.McButton model = new Ui.McButton(s.slim ? "Руки: тонкие" : "Руки: обычные", Ui.McButton.Kind.DARK).size(13f);
            model.addActionListener(e -> Skins.setSlim(s, !s.slim));
            Ui.McButton ren = new Ui.McButton("Переименовать", Ui.McButton.Kind.DARK).size(13f);
            ren.addActionListener(e -> {
                String n = Dlg.input(this, "Переименовать скин", "Новое название", s.name);
                if (n != null && !n.isEmpty()) Skins.rename(s, n);
            });
            Ui.McButton del = new Ui.McButton("Удалить", Ui.McButton.Kind.DARK).size(13f);
            del.addActionListener(e -> {
                if (Dlg.confirm(this, "Удалить скин", "Удалить скин «" + s.name + "» из библиотеки?", "Удалить")) Skins.remove(s);
            });
            b.add(use);
            b.add(model);
            b.add(ren);
            b.add(del);
            b.setPreferredSize(new Dimension(340, 88));
            JPanel east = new JPanel(new java.awt.GridBagLayout());
            east.setOpaque(false);
            east.add(b);
            add(east, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.aa(g0);
            Ui.card(g, 0, 0, getWidth(), getHeight(), Ui.CARD, active ? Ui.GREEN_HI : Ui.BORDER);
            g.dispose();
        }
    }

    /** Вид персонажа спереди, собранный из текстуры скина. */
    static final class Figure extends JComponent {
        private final BufferedImage skin;
        private final boolean slim;

        Figure(BufferedImage skin, boolean slim) {
            this.skin = skin == null ? null : Skins.normalize(skin);
            this.slim = slim;
            setPreferredSize(new Dimension(72, 112));
        }

        private void part(Graphics2D g, int k, int sx, int sy, int sw, int sh, int dx, int dy, int u) {
            g.drawImage(skin, dx, dy, dx + sw * u, dy + sh * u, sx * k, sy * k, (sx + sw) * k, (sy + sh) * k, null);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            if (skin == null) return;
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            int k = skin.getWidth() / 64;
            int u = 3;
            int aw = slim ? 3 : 4;
            int ox = (getWidth() - 16 * u) / 2;
            int oy = (getHeight() - 32 * u) / 2;
            // базовый слой
            part(g, k, 8, 8, 8, 8, ox + 4 * u, oy, u);
            part(g, k, 20, 20, 8, 12, ox + 4 * u, oy + 8 * u, u);
            part(g, k, 44, 20, aw, 12, ox + (4 - aw) * u, oy + 8 * u, u);
            part(g, k, 36, 52, aw, 12, ox + 12 * u, oy + 8 * u, u);
            part(g, k, 4, 20, 4, 12, ox + 4 * u, oy + 20 * u, u);
            part(g, k, 20, 52, 4, 12, ox + 8 * u, oy + 20 * u, u);
            // второй слой
            part(g, k, 40, 8, 8, 8, ox + 4 * u, oy, u);
            part(g, k, 20, 36, 8, 12, ox + 4 * u, oy + 8 * u, u);
            part(g, k, 44, 36, aw, 12, ox + (4 - aw) * u, oy + 8 * u, u);
            part(g, k, 52, 52, aw, 12, ox + 12 * u, oy + 8 * u, u);
            part(g, k, 4, 36, 4, 12, ox + 4 * u, oy + 20 * u, u);
            part(g, k, 4, 52, 4, 12, ox + 8 * u, oy + 20 * u, u);
            g.dispose();
        }
    }
}
