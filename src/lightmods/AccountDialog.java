package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/** Окно «Аккаунты»: офлайн-ник и вход через Microsoft. */
public final class AccountDialog {
    private AccountDialog() {
    }

    public static void show(Component parent) {
        java.awt.Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(w, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        d.setUndecorated(true);
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Ui.BG);
        root.setBorder(new LineBorder(Ui.BORDER, 1));
        JPanel head = new JPanel(new BorderLayout());
        head.setBackground(Ui.SIDEBAR);
        head.setBorder(new EmptyBorder(12, 20, 12, 20));
        head.add(Ui.label("Аккаунты", 15f, Color.WHITE, true), BorderLayout.WEST);
        root.add(head, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(16, 22, 6, 22));
        DefaultListModel<Account> model = new DefaultListModel<>();
        JList<Account> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(Ui.FIELD);
        list.setFixedCellHeight(50);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean sel, boolean f) {
                Account a = (Account) v;
                boolean cur = a == Account.current() || (Account.current() != null && a.key().equals(Account.current().key()));
                JPanel p = new JPanel(new BorderLayout(10, 0)) {
                    @Override
                    protected void paintComponent(java.awt.Graphics g0) {
                        Graphics2D g = Ui.aa(g0);
                        g.setColor(sel ? Ui.CARD_HI : Ui.FIELD);
                        g.fillRect(0, 0, getWidth(), getHeight());
                        g.dispose();
                    }
                };
                p.setBorder(new EmptyBorder(6, 12, 6, 12));
                JPanel t = new JPanel(new java.awt.GridLayout(2, 1));
                t.setOpaque(false);
                t.add(Ui.label(a.name + (cur ? "   (выбран)" : ""), 14.5f, Color.WHITE, true));
                t.add(Ui.label(a.isMs() ? "Microsoft · лицензия" : a.isEly() ? "Ely.by" : "Офлайн", 12f, Ui.MUTED, false));
                p.add(t, BorderLayout.CENTER);
                return p;
            }
        });
        Runnable reload = () -> {
            model.clear();
            for (Account a : Account.all()) model.addElement(a);
            Account c = Account.current();
            if (c != null) for (int i = 0; i < model.size(); i++) if (model.get(i).key().equals(c.key())) list.setSelectedIndex(i);
        };
        reload.run();
        JScrollPane sp = new JScrollPane(list);
        Ui.styleScroll(sp);
        sp.setBorder(BorderFactory.createLineBorder(Ui.BORDER));
        sp.setPreferredSize(new Dimension(520, 200));
        sp.getViewport().setBackground(Ui.FIELD);
        body.add(sp, BorderLayout.CENTER);

        JPanel info = new JPanel(new BorderLayout(0, 8));
        info.setOpaque(false);
        Ui.Note note = new Ui.Note(520, 3, 12.5f, Ui.MUTED);
        note.setText("Офлайн-ник работает без аккаунта (одиночная игра и серверы без проверки лицензии). Для лицензионных серверов войдите через Microsoft, для серверов Ely.by — через Ely.by.");
        info.add(note, BorderLayout.NORTH);
        JPanel msBox = new JPanel(new BorderLayout(0, 8));
        msBox.setOpaque(false);
        msBox.setVisible(false);
        info.add(msBox, BorderLayout.CENTER);
        body.add(info, BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);

        AtomicBoolean cancel = new AtomicBoolean();
        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        foot.setOpaque(false);
        foot.setBorder(new EmptyBorder(8, 12, 16, 22));
        Ui.McButton offline = btn("Офлайн-ник", Ui.McButton.Kind.DARK, 120);
        Ui.McButton ms = btn("Войти через Microsoft", Ui.McButton.Kind.GREEN, 200);
        Ui.McButton ely = btn("Ely.by", Ui.McButton.Kind.DARK, 90);
        Ui.McButton use = btn("Выбрать", Ui.McButton.Kind.DARK, 100);
        Ui.McButton del = btn("Удалить", Ui.McButton.Kind.DARK, 100);
        Ui.McButton close = btn("Закрыть", Ui.McButton.Kind.DARK, 100);
        foot.add(offline);
        foot.add(ms);
        foot.add(ely);
        foot.add(use);
        foot.add(del);
        foot.add(close);
        root.add(foot, BorderLayout.SOUTH);

        offline.addActionListener(e -> {
            String n = Dlg.input(d, "Офлайн-ник", "Ник в игре (3–16 символов: латиница, цифры, _)", Account.current() == null ? "" : Account.current().name);
            if (n == null) return;
            if (!Account.validName(n)) {
                Dlg.message(d, "Неверный ник", "Ник должен состоять из 3–16 символов: латинские буквы, цифры и «_».");
                return;
            }
            Account.addOffline(n);
            reload.run();
        });
        ely.addActionListener(e -> {
            JPanel p = new JPanel(new java.awt.GridLayout(4, 1, 0, 6));
            p.setOpaque(false);
            Ui.Field u = new Ui.Field(null);
            u.setPreferredSize(new Dimension(440, 36));
            javax.swing.JPasswordField pw = new javax.swing.JPasswordField();
            pw.setBackground(Ui.FIELD);
            pw.setForeground(Color.WHITE);
            pw.setCaretColor(Color.WHITE);
            pw.setBorder(BorderFactory.createCompoundBorder(new LineBorder(Ui.BORDER), new EmptyBorder(0, 8, 0, 8)));
            pw.setPreferredSize(new Dimension(440, 36));
            p.add(Ui.label("Ник или e-mail Ely.by", 13f, Ui.MUTED, false));
            p.add(u);
            p.add(Ui.label("Пароль (при 2FA код спросим отдельно)", 13f, Ui.MUTED, false));
            p.add(pw);
            if (Dlg.show(d, "Вход через Ely.by", p, new String[]{"Войти", "Отмена"}, 0) != 0) return;
            String user = u.getText().trim();
            String pass = new String(pw.getPassword());
            if (user.isEmpty() || pass.isEmpty()) return;
            ely.setEnabled(false);
            Tasks.IO.submit(() -> {
                String code = null;
                for (int attempt = 0; attempt < 2; attempt++) {
                    try {
                        Account a = Account.loginEly(user, pass, code);
                        SwingUtilities.invokeLater(() -> {
                            ely.setEnabled(true);
                            reload.run();
                            Tasks.bar.info("Вход выполнен: " + a.name);
                        });
                        return;
                    } catch (Account.TwoFactor t) {
                        String[] box = new String[1];
                        try {
                            SwingUtilities.invokeAndWait(() -> box[0] = Dlg.input(d, "Двухфакторная защита", "Код из приложения-аутентификатора", ""));
                        } catch (Exception ignored) {
                            // окно закрыто
                        }
                        if (box[0] == null || box[0].isBlank()) break;
                        code = box[0];
                    } catch (Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            ely.setEnabled(true);
                            Dlg.message(d, "Не удалось войти", Tasks.friendly(ex));
                        });
                        return;
                    }
                }
                SwingUtilities.invokeLater(() -> ely.setEnabled(true));
            });
        });
        use.addActionListener(e -> {
            Account a = list.getSelectedValue();
            if (a != null) {
                Account.select(a);
                reload.run();
                list.repaint();
            }
        });
        del.addActionListener(e -> {
            Account a = list.getSelectedValue();
            if (a != null && Dlg.confirm(d, "Удалить аккаунт", "Удалить аккаунт «" + a.name + "» из лаунчера?", "Удалить")) {
                Account.remove(a);
                reload.run();
            }
        });
        ms.addActionListener(e -> {
            if (Mc.msClientId == null || Mc.msClientId.isBlank()) {
                Dlg.message(d, "Нужен Client ID", "Для входа через Microsoft укажите Azure Client ID в Настройках (раздел «Вход Microsoft»). Там же краткая инструкция, как его получить бесплатно.");
                return;
            }
            ms.setEnabled(false);
            msBox.removeAll();
            msBox.add(Ui.label("Получаем код…", 13.5f, Ui.MUTED, false), BorderLayout.NORTH);
            msBox.setVisible(true);
            d.pack();
            cancel.set(false);
            Account.loginAsync(code -> SwingUtilities.invokeLater(() -> {
                msBox.removeAll();
                Ui.Note n = new Ui.Note(520, 3, 13.5f, new Color(0xE6E6E8));
                n.setText("Откройте " + code.url + " и введите код. После подтверждения окно обновится само.");
                msBox.add(n, BorderLayout.NORTH);
                JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
                row.setOpaque(false);
                JComponent codeLbl = Ui.label(code.userCode, 24f, Ui.GREEN_HI, true);
                row.add(codeLbl);
                Ui.McButton copy = btn("Копировать", Ui.McButton.Kind.DARK, 120);
                copy.addActionListener(x -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code.userCode), null));
                Ui.McButton open = btn("Открыть сайт", Ui.McButton.Kind.DARK, 130);
                open.addActionListener(x -> {
                    try {
                        java.awt.Desktop.getDesktop().browse(java.net.URI.create(code.url));
                    } catch (Exception ex) {
                        Log.i("Не удалось открыть браузер: " + ex.getMessage());
                    }
                });
                row.add(copy);
                row.add(open);
                msBox.add(row, BorderLayout.CENTER);
                d.pack();
            }), cancel::get, a -> SwingUtilities.invokeLater(() -> {
                msBox.setVisible(false);
                ms.setEnabled(true);
                reload.run();
                d.pack();
                Tasks.bar.info("Вход выполнен: " + a.name);
            }), ex -> SwingUtilities.invokeLater(() -> {
                msBox.setVisible(false);
                ms.setEnabled(true);
                d.pack();
                if (!cancel.get()) Dlg.message(d, "Не удалось войти", Tasks.friendly(ex));
            }));
        });
        close.addActionListener(e -> {
            cancel.set(true);
            d.dispose();
        });
        d.setContentPane(root);
        d.pack();
        d.setLocationRelativeTo(w);
        d.setVisible(true);
    }

    private static Ui.McButton btn(String t, Ui.McButton.Kind k, int w) {
        Ui.McButton b = new Ui.McButton(t, k).size(13f);
        b.setPreferredSize(new Dimension(w, 38));
        return b;
    }
}
