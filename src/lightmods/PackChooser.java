package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;

/** Выбор версии сборки, версии Minecraft и загрузчика перед установкой сборки. */
final class PackChooser {
    private static final String ALL = "Все";

    private PackChooser() {
    }

    private static String loaderName(String l) {
        switch (l) {
            case "fabric":
                return "Fabric";
            case "forge":
                return "Forge";
            case "neoforge":
                return "NeoForge";
            case "quilt":
                return "Quilt";
            default:
                return l;
        }
    }

    static Modrinth.Ver choose(Component parent, String packTitle, List<Modrinth.Ver> all, String preMc, String preLoader) {
        Set<String> mcs = new LinkedHashSet<>();
        Set<String> loaders = new LinkedHashSet<>();
        for (Modrinth.Ver v : all) {
            mcs.addAll(v.gameVersions);
            for (String l : v.loaders) if (!l.equals("mrpack")) loaders.add(l);
        }
        JComboBox<String> mcBox = new JComboBox<>();
        mcBox.addItem(I18n.t(ALL));
        for (String m : mcs) mcBox.addItem(m);
        JComboBox<String> ldBox = new JComboBox<>();
        ldBox.addItem(I18n.t(ALL));
        for (String l : loaders) ldBox.addItem(loaderName(l));
        mcBox.setPreferredSize(new Dimension(270, 38));
        ldBox.setPreferredSize(new Dimension(270, 38));
        Ui.styleCombo(mcBox);
        Ui.styleCombo(ldBox);

        DefaultListModel<Modrinth.Ver> model = new DefaultListModel<>();
        JList<Modrinth.Ver> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Ui.FIELD);
        list.setForeground(Color.WHITE);
        list.setSelectionBackground(Ui.GREEN);
        list.setSelectionForeground(Color.WHITE);
        list.setFont(Ui.font(13.5f));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean sel, boolean focus) {
                Modrinth.Ver v = (Modrinth.Ver) value;
                List<String> ld = new ArrayList<>();
                for (String x : v.loaders) if (!x.equals("mrpack")) ld.add(loaderName(x));
                String gv = v.gameVersions.isEmpty() ? "" : String.join(", ", v.gameVersions.subList(0, Math.min(3, v.gameVersions.size()))) + (v.gameVersions.size() > 3 ? "…" : "");
                String t = (v.name == null || v.name.isBlank() ? v.number : v.name) + "   ·   " + gv + (ld.isEmpty() ? "" : "   ·   " + String.join("/", ld))
                        + ("release".equals(v.type) ? "" : "   (" + v.type + ")");
                JComponent c = (JComponent) super.getListCellRendererComponent(l, I18n.t(t), index, sel, false);
                c.setBorder(new EmptyBorder(6, 10, 6, 10));
                return c;
            }
        });
        Runnable refill = () -> {
            String mc = mcBox.getSelectedIndex() <= 0 ? null : (String) mcBox.getSelectedItem();
            String ld = ldBox.getSelectedIndex() <= 0 ? null : ((String) ldBox.getSelectedItem()).toLowerCase();
            List<Modrinth.Ver> fil = new ArrayList<>();
            for (Modrinth.Ver v : all) {
                if (mc != null && !v.gameVersions.contains(mc)) continue;
                if (ld != null && !v.loaders.contains(ld)) continue;
                fil.add(v);
            }
            model.clear();
            for (Modrinth.Ver v : fil) model.addElement(v);
            Modrinth.Ver best = Modrinth.pickBest(fil);
            if (best != null) list.setSelectedValue(best, true);
            else if (!fil.isEmpty()) list.setSelectedIndex(0);
        };
        if (preMc != null) mcBox.setSelectedItem(preMc);
        if (preLoader != null) ldBox.setSelectedItem(loaderName(preLoader));
        mcBox.addActionListener(e -> refill.run());
        ldBox.addActionListener(e -> refill.run());
        refill.run();

        JPanel top = new JPanel(new GridLayout(2, 2, 10, 4));
        top.setOpaque(false);
        top.add(Ui.label("Версия Minecraft", 13f, Ui.MUTED, false));
        top.add(Ui.label("Загрузчик", 13f, Ui.MUTED, false));
        top.add(mcBox);
        top.add(ldBox);

        JScrollPane sp = new JScrollPane(list);
        Ui.styleScroll(sp);
        sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        sp.setPreferredSize(new Dimension(560, 230));

        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.add(top, BorderLayout.NORTH);
        p.add(sp, BorderLayout.CENTER);
        Ui.Note note = new Ui.Note(560, 3, 12.5f, Ui.MUTED);
        note.setText("Сборка «" + packTitle + "» будет скачана в новую изолированную установку вместе со всеми модами и нужным загрузчиком. Это может занять несколько минут.");
        p.add(note, BorderLayout.SOUTH);
        int r = Dlg.show(parent, "Выберите версию сборки", p, new String[]{"Установить", "Отмена"}, 0);
        return r == 0 ? list.getSelectedValue() : null;
    }
}
