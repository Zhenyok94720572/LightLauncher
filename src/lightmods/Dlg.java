package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/** Тёмные диалоги в стиле лаунчера (вместо светлых JOptionPane). */
public final class Dlg {
    private Dlg() {
    }

    public static final class NameCopy {
        public String name;
        public boolean copy;
    }

    private static JTextArea text(String s, int width) {
        JTextArea ta = new JTextArea(I18n.t(s));
        ta.setEditable(false);
        ta.setOpaque(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setForeground(new Color(0xE6E6E8));
        ta.setFont(Ui.font(14f));
        ta.setBorder(null);
        ta.setFocusable(false);
        ta.setSize(new Dimension(width, 10));
        ta.setPreferredSize(new Dimension(width, ta.getPreferredSize().height));
        return ta;
    }

    /** Показывает диалог; возвращает индекс нажатой кнопки или -1. */
    static int show(Component parent, String title, JComponent body, String[] buttons, int primary) {
        Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(w, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        int[] result = {-1};
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Ui.BG);
        root.setBorder(new LineBorder(Ui.BORDER, 1));
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Ui.SIDEBAR);
        head.setBorder(new EmptyBorder(12, 20, 12, 20));
        head.add(Ui.label(title, 15f, Color.WHITE, true), BorderLayout.WEST);
        root.add(head, BorderLayout.NORTH);
        JPanel bodyWrap = new JPanel(new BorderLayout());
        bodyWrap.setOpaque(false);
        bodyWrap.setBorder(new EmptyBorder(18, 22, 8, 22));
        bodyWrap.add(body, BorderLayout.CENTER);
        root.add(bodyWrap, BorderLayout.CENTER);
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        foot.setOpaque(false);
        foot.setBorder(new EmptyBorder(8, 12, 16, 22));
        for (int i = 0; i < buttons.length; i++) {
            int idx = i;
            Ui.McButton b = new Ui.McButton(buttons[i], i == primary ? Ui.McButton.Kind.GREEN : Ui.McButton.Kind.DARK);
            b.setPreferredSize(new Dimension(Math.max(110, b.getPreferredSize().width), 38));
            b.addActionListener(e -> {
                result[0] = idx;
                d.dispose();
            });
            foot.add(b);
        }
        root.add(foot, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.getRootPane().registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.pack();
        d.setLocationRelativeTo(w);
        d.setVisible(true);
        return result[0];
    }

    public static void message(Component parent, String title, String msg) {
        show(parent, title, text(msg, 460), new String[]{"OK"}, 0);
    }

    public static boolean confirm(Component parent, String title, String msg, String okText) {
        return show(parent, title, text(msg, 460), new String[]{okText, "Отмена"}, 0) == 0;
    }

    public static String input(Component parent, String title, String label, String initial) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setOpaque(false);
        p.add(Ui.label(label, 13.5f, Ui.MUTED, false), BorderLayout.NORTH);
        Ui.Field f = new Ui.Field(null);
        f.setText(initial == null ? "" : initial);
        f.setPreferredSize(new Dimension(440, 38));
        p.add(f, BorderLayout.CENTER);
        f.addAncestorListener(new javax.swing.event.AncestorListener() {
            @Override
            public void ancestorAdded(javax.swing.event.AncestorEvent e) {
                f.requestFocusInWindow();
                f.selectAll();
            }

            @Override
            public void ancestorRemoved(javax.swing.event.AncestorEvent e) {
            }

            @Override
            public void ancestorMoved(javax.swing.event.AncestorEvent e) {
            }
        });
        int r = show(parent, title, p, new String[]{"OK", "Отмена"}, 0);
        return r == 0 ? f.getText().trim() : null;
    }

    public static NameCopy nameAndCopy(Component parent, String title, String label, String initial, String checkText, boolean checkDefault) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setOpaque(false);
        p.add(Ui.label(label, 13.5f, Ui.MUTED, false), BorderLayout.NORTH);
        Ui.Field f = new Ui.Field(null);
        f.setText(initial == null ? "" : initial);
        f.setPreferredSize(new Dimension(440, 38));
        p.add(f, BorderLayout.CENTER);
        JCheckBox c = Ui.check(checkText, checkDefault);
        p.add(c, BorderLayout.SOUTH);
        int r = show(parent, title, p, new String[]{"Создать", "Отмена"}, 0);
        if (r != 0) return null;
        NameCopy n = new NameCopy();
        n.name = f.getText().trim();
        n.copy = c.isSelected();
        return n;
    }

    public static <T> T choose(Component parent, String title, String msg, List<T> items) {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setOpaque(false);
        p.add(text(msg, 520), BorderLayout.NORTH);
        DefaultListModel<T> m = new DefaultListModel<>();
        for (T t : items) m.addElement(t);
        JList<T> l = new JList<>(m);
        l.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        l.setBackground(Ui.FIELD);
        l.setForeground(Color.WHITE);
        l.setSelectionBackground(Ui.GREEN);
        l.setSelectionForeground(Color.WHITE);
        l.setFont(Ui.font(13.5f));
        l.setSelectedIndex(0);
        l.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                JComponent c = (JComponent) super.getListCellRendererComponent(list, value, index, sel, false);
                c.setBorder(new EmptyBorder(6, 10, 6, 10));
                return c;
            }
        });
        JScrollPane sp = new JScrollPane(l);
        Ui.styleScroll(sp);
        sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        sp.setPreferredSize(new Dimension(520, 240));
        p.add(sp, BorderLayout.CENTER);
        int r = show(parent, title, p, new String[]{"Выбрать", "Отмена"}, 0);
        return r == 0 ? l.getSelectedValue() : null;
    }
}
