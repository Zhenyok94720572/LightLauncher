package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/** Установки лаунчера и изоляция уже скачанных версий. */
public final class InstancesPanel extends JPanel {
    private final DefaultListModel<Mc.Profile> profModel = new DefaultListModel<>();
    private final JList<Mc.Profile> profList = new JList<>(profModel);

    public InstancesPanel() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(8, 26, 16, 26));

        setupList(profList);
        profList.setCellRenderer(new ProfileCell());

        Ui.McButton select = new Ui.McButton("Выбрать", Ui.McButton.Kind.GREEN).size(13f);
        Ui.McButton content = new Ui.McButton("Содержимое", Ui.McButton.Kind.DARK).size(13f);
        Ui.McButton folder = new Ui.McButton("Папка", Ui.McButton.Kind.DARK).size(13f);
        Ui.McButton icon = new Ui.McButton("Значок…", Ui.McButton.Kind.DARK).size(13f);
        icon.addActionListener(e -> {
            Mc.Profile p = profList.getSelectedValue();
            if (p == null) Dlg.message(this, "Выберите установку", "Выберите в списке установку, для которой нужен значок.");
            else IconPicker.pick(this, p);
        });
        Ui.McButton ram = new Ui.McButton("Память…", Ui.McButton.Kind.DARK).size(13f);
        ram.addActionListener(e -> {
            Mc.Profile p = profList.getSelectedValue();
            if (p == null) Dlg.message(this, "Выберите установку", "Выберите в списке установку, для которой нужно настроить память.");
            else InstRam.edit(this, p);
        });
        Ui.McButton remove = new Ui.McButton("Удалить", Ui.McButton.Kind.DARK).size(13f);
        select.addActionListener(e -> {
            Mc.Profile p = profList.getSelectedValue();
            if (p != null) State.select(p.id);
        });
        content.addActionListener(e -> {
            Mc.Profile p = profList.getSelectedValue();
            if (p != null) new ContentDialog(this, p).setVisible(true);
        });
        folder.addActionListener(e -> {
            Mc.Profile p = profList.getSelectedValue();
            if (p != null) Mc.openFolder(p.gameDir);
        });
        remove.addActionListener(e -> removeProfile());
        profList.getInputMap(JComponent.WHEN_FOCUSED).put(javax.swing.KeyStroke.getKeyStroke("DELETE"), "del");
        profList.getActionMap().put("del", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                removeProfile();
            }
        });
        Ui.McButton isolate = new Ui.McButton("Изолировать…", Ui.McButton.Kind.GREEN).size(13f);
        Ui.McButton fromVer = new Ui.McButton("Из скачанной версии…", Ui.McButton.Kind.DARK).size(13f);
        isolate.addActionListener(e -> {
            Mc.Profile p = profList.getSelectedValue();
            if (p == null || p.versionId == null) {
                Dlg.message(this, "Выберите установку", "Выберите в списке установку, версию которой нужно изолировать.");
                return;
            }
            isolateVersion(p.versionId, p.name);
        });
        fromVer.addActionListener(e -> {
            List<Mc.Installed> vs = Mc.installedVersions();
            if (vs.isEmpty()) {
                Dlg.message(this, "Нет скачанных версий", "В папке versions пока ничего нет. Скачайте версию во вкладке «Версии».");
                return;
            }
            Mc.Installed v = Dlg.choose(this, "Скачанные версии", "Выберите уже скачанную версию — для неё будет создана отдельная изолированная установка.", vs);
            if (v != null) isolateVersion(v.id, v.id);
        });
        add(section("Установки", "Все установки лаунчера. «Изолировать» создаёт для версии отдельную папку игры: свои моды, миры и настройки.", profList, select, isolate, fromVer, content, icon, ram, folder, remove));

        State.addListener(this::reloadProfiles);
        reloadProfiles();
    }

    private void setupList(JList<?> l) {
        l.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        l.setOpaque(false);
        l.setFixedCellHeight(62);
        l.setBackground(new Color(0, 0, 0, 0));
    }

    private JPanel section(String title, String sub, JList<?> list, Ui.McButton... btns) {
        JPanel p = new JPanel(new BorderLayout(0, 12)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.card(g, 0, 0, getWidth(), getHeight(), Ui.CARD, Ui.BORDER);
                g.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(18, 18, 16, 18));
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.label(title, 18f, Color.WHITE, true), BorderLayout.NORTH);
        Ui.Note s = new Ui.Note(760, 1, 12.5f, Ui.MUTED);
        s.setText(sub);
        s.setBorder(new EmptyBorder(3, 0, 6, 0));
        head.add(s, BorderLayout.CENTER);
        p.add(head, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(list);
        Ui.styleScroll(sp);
        JPanel inner = new JPanel(new BorderLayout());
        inner.setBackground(Ui.FIELD);
        inner.setBorder(new LineBorder(Ui.BORDER));
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(Ui.FIELD);
        inner.add(sp, BorderLayout.CENTER);
        p.add(inner, BorderLayout.CENTER);
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        bar.setOpaque(false);
        for (Ui.McButton b : btns) {
            b.setPreferredSize(new Dimension(Math.max(96, b.getPreferredSize().width), 36));
            bar.add(b);
        }
        p.add(bar, BorderLayout.SOUTH);
        return p;
    }

    private void reloadProfiles() {
        Mc.Profile sel = profList.getSelectedValue();
        profModel.clear();
        for (Mc.Profile p : State.profiles()) profModel.addElement(p);
        Mc.Profile cur = State.current();
        String want = sel != null ? sel.id : (cur != null ? cur.id : null);
        for (int i = 0; i < profModel.size(); i++) if (profModel.get(i).id.equals(want)) profList.setSelectedIndex(i);
    }

    private void isolateVersion(String versionId, String baseName) {
        Dlg.NameCopy r = Dlg.nameAndCopy(this, "Изолировать версию", "Название новой установки", baseName + " (изолированная)", "Скопировать options.txt и servers.dat из основной папки", true);
        if (r == null || r.name.isEmpty()) return;
        try {
            Mc.Profile p = Mc.createInstance(r.name, versionId, r.copy, "Glowstone");
            State.refreshAndSelect(p.id);
            Dlg.message(this, "Готово", "Создана изолированная установка «" + r.name + "».\n\nУ неё своя папка игры: свои моды, миры, ресурспаки и настройки. Сама версия " + versionId + " не копируется — используется уже скачанная.");
        } catch (IOException e) {
            Dlg.message(this, "Ошибка", Tasks.friendly(e));
        }
    }

    private void removeProfile() {
        removeProfile(this, profList.getSelectedValue());
    }

    /** Удаляет установку с подтверждением (используется и на главной). */
    public static void removeProfile(java.awt.Component parent, Mc.Profile p) {
        if (p == null) return;
        Path inst = Mc.instancesDir().toAbsolutePath().normalize();
        boolean ours = p.gameDir.toAbsolutePath().normalize().startsWith(inst);
        String msg = "Удалить установку «" + p.name + "» из лаунчера?" + (ours ? "\n\nПапка с модами и мирами этой установки тоже будет удалена:\n" + p.gameDir : "\n\nФайлы игры останутся на диске.");
        if (!Dlg.confirm(parent, "Удаление установки", msg, "Удалить")) return;
        try {
            Mc.removeProfile(p.id);
            if (ours) Mc.deleteInstanceFiles(p.gameDir);
            State.refresh();
        } catch (IOException e) {
            Dlg.message(parent, "Ошибка", Tasks.friendly(e));
        }
    }

    // ------------------------------------------------------------------ ячейки

    private static final class ProfileCell extends JComponent implements ListCellRenderer<Mc.Profile> {
        private Mc.Profile p;
        private boolean sel;
        private boolean current;

        @Override
        public Component getListCellRendererComponent(JList<? extends Mc.Profile> list, Mc.Profile value, int index, boolean isSelected, boolean focus) {
            p = value;
            sel = isSelected;
            Mc.Profile cur = State.current();
            current = cur != null && cur.id.equals(value.id);
            return this;
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.aa(g0);
            int w = getWidth();
            int h = getHeight();
            g.setColor(sel ? new Color(0x34503A) : Ui.FIELD);
            g.fillRect(0, 0, w, h);
            if (sel) {
                g.setColor(Ui.GREEN_HI);
                g.fillRect(0, 0, 3, h);
            }
            InstanceCombo.InstanceIcon.paint(g, 14, (h - 38) / 2, 38, p);
            int tx = w - 12;
            if (current) tx = tag(g, "Выбрана", tx, 22, Ui.GREEN);
            if (p.isolated) tx = tag(g, "Изолирована", tx, 22, new Color(0x4A6FA5));
            g.setFont(Ui.bold(14.5f));
            g.setColor(Color.WHITE);
            g.drawString(Ui.ellipsize(p.name, g.getFontMetrics(), Math.max(60, tx - 64 - 6)), 64, 25);
            g.setFont(Ui.font(12.5f));
            g.setColor(Ui.MUTED);
            String path = p.gameDir == null ? "" : p.gameDir.toString();
            Stats.Entry st = Stats.get(p.id);
            String played = st.seconds > 0 ? "   ·   " + I18n.t("наиграно ") + Stats.duration(st.seconds) : "";
            g.drawString(Ui.ellipsize(p.subtitle() + played + "   ·   " + path, g.getFontMetrics(), w - 64 - 12), 64, 45);
            g.dispose();
        }

        private int tag(Graphics2D g, String t, int right, int cy, Color c) {
            t = I18n.t(t);
            g.setFont(Ui.bold(11f));
            int tw = g.getFontMetrics().stringWidth(t) + 14;
            int x = right - tw;
            g.setColor(c);
            g.fillRoundRect(x, cy - 10, tw, 20, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(t, x + 7, cy + 4);
            return x - 6;
        }
    }

    // ------------------------------------------------------------------ содержимое установки

    private static final class ContentDialog extends JDialog {
        private final Mc.Profile p;
        private final DefaultListModel<Path> model = new DefaultListModel<>();
        private final JList<Path> list = new JList<>(model);
        private String sub = "mods";

        ContentDialog(Component parent, Mc.Profile p) {
            super(javax.swing.SwingUtilities.getWindowAncestor(parent), java.awt.Dialog.ModalityType.APPLICATION_MODAL);
            this.p = p;
            setUndecorated(true);
            JPanel root = new JPanel(new BorderLayout(0, 12));
            root.setBackground(Ui.BG);
            root.setBorder(new javax.swing.border.CompoundBorder(new LineBorder(Ui.BORDER), new EmptyBorder(16, 20, 16, 20)));
            root.add(Ui.label("Содержимое: " + p.name, 17f, Color.WHITE, true), BorderLayout.NORTH);

            JPanel mid = new JPanel(new BorderLayout(0, 10));
            mid.setOpaque(false);
            JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            chips.setOpaque(false);
            ButtonGroup g = new ButtonGroup();
            String[][] tabs = {{"mods", "Моды"}, {"resourcepacks", "Текстуры"}, {"shaderpacks", "Шейдеры"}};
            for (String[] t : tabs) {
                Ui.Chip c = new Ui.Chip(t[1]);
                c.setSelected(t[0].equals(sub));
                c.addActionListener(e -> {
                    sub = t[0];
                    load();
                });
                g.add(c);
                chips.add(c);
            }
            mid.add(chips, BorderLayout.NORTH);
            list.setBackground(Ui.FIELD);
            list.setFixedCellHeight(34);
            list.setCellRenderer((l, value, index, sel, focus) -> new JComponent() {
                @Override
                protected void paintComponent(Graphics g0) {
                    Graphics2D g2 = Ui.aa(g0);
                    g2.setColor(sel ? new Color(0x34503A) : Ui.FIELD);
                    g2.fillRect(0, 0, getWidth(), getHeight());
                    String n = value.getFileName().toString();
                    boolean off = n.endsWith(".disabled");
                    g2.setFont(Ui.font(13.5f));
                    g2.setColor(off ? Ui.MUTED : Color.WHITE);
                    g2.drawString(Ui.ellipsize(n, g2.getFontMetrics(), getWidth() - 130), 12, 22);
                    if (off) {
                        g2.setFont(Ui.bold(11f));
                        g2.setColor(new Color(0xFF9B71));
                        g2.drawString(I18n.t("ВЫКЛЮЧЕН"), getWidth() - 92, 21);
                    }
                    g2.dispose();
                }
            });
            JScrollPane sp = new JScrollPane(list);
            Ui.styleScroll(sp);
            sp.getViewport().setOpaque(true);
            sp.getViewport().setBackground(Ui.FIELD);
            sp.setBorder(new LineBorder(Ui.BORDER));
            sp.setPreferredSize(new Dimension(620, 320));
            mid.add(sp, BorderLayout.CENTER);
            root.add(mid, BorderLayout.CENTER);

            JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
            bar.setOpaque(false);
            Ui.McButton toggle = new Ui.McButton("Вкл / выкл", Ui.McButton.Kind.DARK).size(13f);
            Ui.McButton del = new Ui.McButton("Удалить", Ui.McButton.Kind.DARK).size(13f);
            Ui.McButton open = new Ui.McButton("Открыть папку", Ui.McButton.Kind.DARK).size(13f);
            Ui.McButton close = new Ui.McButton("Закрыть", Ui.McButton.Kind.GREEN).size(13f);
            toggle.addActionListener(e -> toggleSel());
            del.addActionListener(e -> deleteSel());
            open.addActionListener(e -> Mc.openFolder(p.gameDir.resolve(sub)));
            close.addActionListener(e -> dispose());
            bar.add(toggle);
            bar.add(del);
            bar.add(open);
            bar.add(close);
            root.add(bar, BorderLayout.SOUTH);
            setContentPane(root);
            load();
            pack();
            setLocationRelativeTo(parent);
        }

        private void load() {
            model.clear();
            Path d = p.gameDir.resolve(sub);
            if (!Files.isDirectory(d)) return;
            try (Stream<Path> s = Files.list(d)) {
                List<Path> l = new ArrayList<>();
                s.filter(x -> !x.getFileName().toString().startsWith(".")).forEach(l::add);
                l.sort(Comparator.comparing(x -> x.getFileName().toString().toLowerCase()));
                for (Path x : l) model.addElement(x);
            } catch (IOException ignored) {
                // пусто
            }
        }

        private void toggleSel() {
            Path f = list.getSelectedValue();
            if (f == null) return;
            String n = f.getFileName().toString();
            try {
                Path t = n.endsWith(".disabled") ? f.resolveSibling(n.substring(0, n.length() - 9)) : f.resolveSibling(n + ".disabled");
                if (!Files.exists(t)) Files.move(f, t);
                load();
            } catch (IOException e) {
                Dlg.message(this, "Ошибка", Tasks.friendly(e));
            }
        }

        private void deleteSel() {
            Path f = list.getSelectedValue();
            if (f == null) return;
            if (!Dlg.confirm(this, "Удаление", "Удалить файл «" + f.getFileName() + "»?", "Удалить")) return;
            try {
                if (Files.isDirectory(f)) Mc.deleteTree(f);
                else Files.deleteIfExists(f);
                load();
                State.refresh();
            } catch (IOException e) {
                Dlg.message(this, "Ошибка", Tasks.friendly(e));
            }
        }
    }
}
