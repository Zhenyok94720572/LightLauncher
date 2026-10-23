package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Прямая установка версий Minecraft: Vanilla, Fabric, Forge, NeoForge, Quilt. */
public final class VersionsPanel extends JPanel {
    private static final String[][] LOADERS = {
            {"vanilla", "Vanilla"}, {"fabric", "Fabric"}, {"forge", "Forge"}, {"neoforge", "NeoForge"}, {"quilt", "Quilt"}};

    private String loader = "fabric";
    private final JComboBox<Mc.McVer> mcBox = new JComboBox<>();
    private final JComboBox<Mc.LV> lvBox = new JComboBox<>();
    private final JCheckBox snapshots = Ui.check("Показывать снапшоты и старые версии", false);
    private final JCheckBox isolate = Ui.check("Создать изолированную установку (отдельные моды, миры и настройки)", true);
    private final JCheckBox copySettings = Ui.check("Скопировать options.txt и servers.dat из основной папки", true);
    private final Ui.Field nameField = new Ui.Field("Название установки");
    private final Ui.McButton installBtn = new Ui.McButton("СКАЧАТЬ И УСТАНОВИТЬ", Ui.McButton.Kind.GREEN).size(16f);
    private final Ui.Note hint = new Ui.Note(560, 2, 13f, Ui.MUTED);

    private void setHint(String text, boolean error) {
        hint.setForeground(error ? new Color(0xFF7B72) : Ui.MUTED);
        hint.setText(text);
    }
    private final JLabel lvLabel = Ui.label("Версия загрузчика", 13f, Ui.MUTED, false);
    private List<Mc.McVer> all = new ArrayList<>();
    private boolean loadedOnce;
    private boolean nameEdited;
    private int lvGen;

    public VersionsPanel() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(8, 26, 16, 26));

        Ui.styleCombo(mcBox);
        Ui.styleCombo(lvBox);
        mcBox.setPreferredSize(new Dimension(260, 40));
        lvBox.setPreferredSize(new Dimension(260, 40));

        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.card(g, 0, 0, getWidth(), getHeight(), Ui.CARD, Ui.BORDER);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(22, 24, 22, 24));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.gridwidth = 2;

        card.add(Ui.label("Тип версии", 13f, Ui.MUTED, false), c);
        c.gridy++;
        c.insets = new Insets(6, 0, 16, 0);
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        chips.setOpaque(false);
        ButtonGroup grp = new ButtonGroup();
        for (String[] l : LOADERS) {
            Ui.Chip ch = new Ui.Chip(l[1]);
            ch.setSelected(l[0].equals(loader));
            ch.addActionListener(e -> {
                loader = l[0];
                onLoaderChanged();
            });
            grp.add(ch);
            chips.add(ch);
        }
        card.add(chips, c);

        c.gridy++;
        c.gridwidth = 1;
        c.weightx = 0.5;
        c.insets = new Insets(0, 0, 0, 12);
        card.add(Ui.label("Версия Minecraft", 13f, Ui.MUTED, false), c);
        c.gridx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        card.add(lvLabel, c);
        c.gridx = 0;
        c.gridy++;
        c.insets = new Insets(6, 0, 10, 12);
        card.add(mcBox, c);
        c.gridx = 1;
        c.insets = new Insets(6, 0, 10, 0);
        card.add(lvBox, c);

        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 18, 0);
        card.add(snapshots, c);

        c.gridy++;
        c.insets = new Insets(0, 0, 8, 0);
        card.add(isolate, c);
        c.gridy++;
        card.add(copySettings, c);
        c.gridy++;
        c.insets = new Insets(8, 0, 4, 0);
        card.add(Ui.label("Название установки в лаунчере", 13f, Ui.MUTED, false), c);
        c.gridy++;
        c.insets = new Insets(0, 0, 20, 0);
        card.add(nameField, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 10, 0);
        c.fill = GridBagConstraints.NONE;
        installBtn.setPreferredSize(new Dimension(300, 52));
        card.add(installBtn, c);
        c.gridy++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 0, 0);
        card.add(hint, c);

        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.add(card, BorderLayout.NORTH);
        left.setPreferredSize(new Dimension(640, 100));
        wrap.add(left, BorderLayout.WEST);
        add(wrap, BorderLayout.CENTER);

        mcBox.addActionListener(e -> {
            if (mcBox.getSelectedItem() != null) {
                refreshLoaderVersions();
                autoName();
            }
        });
        lvBox.addActionListener(e -> autoName());
        snapshots.addActionListener(e -> fillMc());
        isolate.addActionListener(e -> {
            copySettings.setEnabled(isolate.isSelected());
            autoName();
        });
        nameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                if (nameField.hasFocus()) nameEdited = true;
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                if (nameField.hasFocus()) nameEdited = true;
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
            }
        });
        installBtn.addActionListener(e -> install());
        installBtn.setEnabled(false);
        setHint("Загрузка списка версий Minecraft…", false);
        onLoaderChanged();
    }

    public void onShow() {
        if (loadedOnce) return;
        loadedOnce = true;
        Tasks.IO.submit(() -> {
            try {
                List<Mc.McVer> list = Mc.manifest();
                SwingUtilities.invokeLater(() -> {
                    all = list;
                    fillMc();
                });
            } catch (IOException e) {
                Log.i("Манифест версий: " + e);
                SwingUtilities.invokeLater(() -> {
                    loadedOnce = false;
                    setHint("Не удалось загрузить список версий: " + Tasks.friendly(e) + ". Откройте вкладку ещё раз.", true);
                });
            }
        });
    }

    private void fillMc() {
        Mc.McVer prev = (Mc.McVer) mcBox.getSelectedItem();
        DefaultComboBoxModel<Mc.McVer> m = new DefaultComboBoxModel<>();
        for (Mc.McVer v : all) if (snapshots.isSelected() || "release".equals(v.type)) m.addElement(v);
        mcBox.setModel(m);
        if (prev != null) {
            for (int i = 0; i < m.getSize(); i++) if (m.getElementAt(i).id.equals(prev.id)) mcBox.setSelectedIndex(i);
        } else {
            Mc.Profile cur = State.current();
            if (cur != null && cur.mc != null) {
                for (int i = 0; i < m.getSize(); i++) if (m.getElementAt(i).id.equals(cur.mc)) mcBox.setSelectedIndex(i);
            }
        }
        setHint(" ", false);
        refreshLoaderVersions();
        autoName();
    }

    private void onLoaderChanged() {
        boolean van = loader.equals("vanilla");
        lvBox.setVisible(!van);
        lvLabel.setVisible(!van);
        refreshLoaderVersions();
        autoName();
    }

    private void refreshLoaderVersions() {
        Mc.McVer mc = (Mc.McVer) mcBox.getSelectedItem();
        if (loader.equals("vanilla")) {
            installBtn.setEnabled(mc != null);
            setHint(mc == null ? " " : "Будет скачан клиент Minecraft " + mc.id + ". Библиотеки, ресурсы и Java докачаются при первом запуске.", false);
            return;
        }
        if (mc == null) {
            installBtn.setEnabled(false);
            return;
        }
        int my = ++lvGen;
        installBtn.setEnabled(false);
        lvBox.setModel(new DefaultComboBoxModel<>());
        setHint("Загрузка версий загрузчика…", false);
        String ld = loader;
        Tasks.IO.submit(() -> {
            List<Mc.LV> res;
            String err = null;
            try {
                res = Mc.loaderVersions(ld, mc.id);
            } catch (Exception e) {
                res = new ArrayList<>();
                err = Tasks.friendly(e);
            }
            List<Mc.LV> fin = res;
            String fe = err;
            SwingUtilities.invokeLater(() -> {
                if (my != lvGen) return;
                DefaultComboBoxModel<Mc.LV> m = new DefaultComboBoxModel<>();
                for (Mc.LV v : fin) m.addElement(v);
                lvBox.setModel(m);
                if (fin.isEmpty()) {
                    installBtn.setEnabled(false);
                    setHint(fe != null ? "Не удалось загрузить: " + fe : titleOf(ld) + " для Minecraft " + mc.id + " недоступен — выберите другую версию Minecraft.", fe != null);
                } else {
                    installBtn.setEnabled(true);
                    setHint(loader.equals("forge") || loader.equals("neoforge")
                            ? "Установщик " + titleOf(ld) + " запустится автоматически (нужна Java и интернет), это займёт 1–3 минуты."
                            : "Профиль " + titleOf(ld) + " будет добавлен в лаунчер; нужные библиотеки докачаются при первом запуске.", false);
                }
                autoName();
            });
        });
    }

    private static String titleOf(String l) {
        for (String[] x : LOADERS) if (x[0].equals(l)) return x[1];
        return l;
    }

    private void autoName() {
        if (nameEdited) return;
        Mc.McVer mc = (Mc.McVer) mcBox.getSelectedItem();
        if (mc == null) return;
        nameField.setText(titleOf(loader) + " " + mc.id);
    }

    private void install() {
        Mc.McVer mc = (Mc.McVer) mcBox.getSelectedItem();
        Mc.LV lv = (Mc.LV) lvBox.getSelectedItem();
        if (mc == null) return;
        if (!loader.equals("vanilla") && lv == null) return;
        String name = nameField.getText().trim();
        if (name.isEmpty()) name = titleOf(loader) + " " + mc.id;
        String fname = name;
        String ld = loader;
        boolean iso = isolate.isSelected();
        boolean copy = copySettings.isSelected();
        installBtn.setEnabled(false);
        String[] created = new String[1];
        Tasks.run("Установка " + titleOf(ld) + " " + mc.id, p -> {
            String vid = Mc.installLoader(ld, mc.id, lv == null ? null : lv.v, p);
            if (iso) {
                created[0] = Mc.createInstance(fname, vid, copy, "Glowstone").id;
            } else {
                String id = "lightmods-" + Mc.sanitize(vid).toLowerCase().replace(' ', '-');
                Mc.upsertProfile(id, fname, vid, null, "Grass");
                created[0] = id;
            }
        }, err -> {
            installBtn.setEnabled(true);
            if (err == null) {
                State.refreshAndSelect(created[0]);
                nameEdited = false;
                Dlg.message(this, "Готово", "«" + fname + "» установлена и выбрана в списке установок.\n\nНажмите «ИГРАТЬ» на главной — недостающие файлы докачаются при первом запуске.");
            } else {
                Dlg.message(this, "Не удалось установить", Tasks.friendly(err));
            }
        });
    }
}
