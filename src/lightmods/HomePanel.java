package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Главная: пиксельный пейзаж, название и нижняя панель «Установка + ИГРАТЬ», как в лаунчере. */
public final class HomePanel extends JPanel {
    private final Ui.EllipsisLabel pathHead = new Ui.EllipsisLabel("Папка игры:", 12f, Ui.MUTED, true);
    private final Ui.EllipsisLabel pathLabel = new Ui.EllipsisLabel(" ", 12f, new Color(0xD0D0D4), true);

    public HomePanel(Consumer<String> navigate) {
        super(new BorderLayout());
        setOpaque(false);

        JPanel hero = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.paintHero(g, getWidth(), getHeight(), System.currentTimeMillis());
                int pad = 54;
                int s = 112;
                int y = getHeight() / 2 - 150;
                Ui.paintAvatar(g, pad, y, s);
                g.setFont(Ui.bold(46f));
                FontMetrics fm = g.getFontMetrics();
                String t = "Light Launcher";
                g.setColor(new Color(0, 0, 0, 120));
                g.drawString(t, pad + s + 26 + 2, y + 54 + 2);
                g.setColor(Color.WHITE);
                g.drawString(t, pad + s + 26, y + 54);
                g.setFont(Ui.font(17f));
                g.setColor(new Color(0xE6F0E2));
                g.drawString(I18n.t("Моды, сборки и текстуры из Modrinth — и запуск игры в одном лаунчере"), pad + s + 28, y + 90);
                g.setFont(Ui.font(14f));
                g.setColor(new Color(255, 255, 255, 190));
                g.drawString(I18n.t("Fabric · Forge · NeoForge · Quilt · изолированные установки"), pad + s + 28, y + 114);
                g.dispose();
            }
        };
        hero.setOpaque(false);
        // облака плывут, пока вкладка видна
        javax.swing.Timer anim = new javax.swing.Timer(33, e -> hero.repaint());
        hero.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (hero.isShowing()) anim.start();
                else anim.stop();
            }
        });
        JPanel quick = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        quick.setOpaque(false);
        String[][] q = {{"Моды", "mods"}, {"Сборки", "packs"}, {"Текстуры", "textures"}, {"Шейдеры", "shaders"}, {"Версии", "versions"}};
        for (String[] a : q) {
            Ui.McButton b = new Ui.McButton(a[0], Ui.McButton.Kind.GRAY).size(14f);
            b.setPreferredSize(new Dimension(120, 42));
            b.addActionListener(e -> navigate.accept(a[1]));
            quick.add(b);
        }
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 1;
        c.anchor = GridBagConstraints.SOUTHWEST;
        c.insets = new Insets(0, 54, 40, 0);
        hero.add(quick, c);
        add(hero, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(20, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Ui.BAR);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(new Color(0x2C2C2E));
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(14, 28, 14, 28));
        bar.setPreferredSize(new Dimension(10, 96));

        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.setOpaque(false);
        left.add(Ui.label("УСТАНОВКА", 11f, Ui.MUTED, true), BorderLayout.NORTH);
        InstanceCombo combo = new InstanceCombo();
        combo.setPreferredSize(new Dimension(290, 46));
        left.add(combo, BorderLayout.CENTER);
        bar.add(left, BorderLayout.WEST);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        Ui.McButton play = new Ui.McButton("ИГРАТЬ", Ui.McButton.Kind.GREEN).size(26f);
        play.setPreferredSize(new Dimension(230, 62));
        playBtn = play;
        play.addActionListener(e -> launch(play));
        Game.addListener(this::gameChanged);
        center.add(play);
        bar.add(center, BorderLayout.CENTER);

        JPanel right = new JPanel(new java.awt.GridLayout(2, 1, 0, 2));
        right.setOpaque(false);
        right.add(pathHead);
        right.add(pathLabel);
        right.setPreferredSize(new Dimension(230, 44));
        bar.add(right, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);

        State.addListener(() -> {
            update();
            updatePlay();
        });
        update();
    }

    private void update() {
        Mc.Profile p = State.current();
        pathHead.setText(p == null ? "Установок нет" : "Папка игры:");
        pathLabel.setText(p == null ? "создайте во вкладке «Версии»" : shorten(p.gameDir.toString()));
        pathLabel.setToolTipText(p == null ? null : p.gameDir.toString());
    }

    private static String shorten(String s) {
        return s.length() <= 34 ? s : "…" + s.substring(s.length() - 33);
    }

    private Ui.McButton playBtn;
    private final java.util.Set<String> preparing = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void updatePlay() {
        if (playBtn == null) return;
        Mc.Profile p = State.current();
        if (p != null && Game.isRunning(p.id)) {
            playBtn.setText("ИГРА ИДЁТ");
            playBtn.setEnabled(false);
        } else if (p != null && preparing.contains(p.id)) {
            playBtn.setText("ЗАПУСК…");
            playBtn.setEnabled(false);
        } else {
            playBtn.setText("ИГРАТЬ");
            playBtn.setEnabled(true);
        }
    }

    private void gameChanged() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            for (Mc.Profile p : State.profiles()) {
                Integer code = Game.LAST_EXIT.get(p.id);
                if (code != null && !Game.isRunning(p.id)) {
                    Game.LAST_EXIT.remove(p.id);
                    if (code != 0) {
                        Dlg.message(this, "Игра завершилась с ошибкой", "Minecraft («" + p.name + "») закрылась с кодом " + code + ".\n\nПодробности — в Настройках, раздел «Журнал» (строки с пометкой [игра]).");
                    }
                }
            }
            updatePlay();
        });
    }

    private static boolean warned116;

    private void warnVanilla116(Mc.Profile p) {
        if (warned116) return;
        warned116 = true;
        javax.swing.SwingUtilities.invokeLater(() -> Dlg.message(this, "Сетевая игра в " + p.mc,
                "В Minecraft " + p.mc + " с офлайн-ником игра сама отключает «Сетевую игру».\n\nЧтобы она работала, установите Fabric для этой версии во вкладке «Версии» — лаунчер автоматически добавит мод, который это исправляет. Либо войдите через Microsoft."));
    }

    private void launch(Ui.McButton play) {
        Mc.Profile p = State.current();
        if (p == null) {
            Dlg.message(this, "Нет установки", "Сначала создайте установку во вкладке «Версии».");
            return;
        }
        Account acc = Account.current();
        if (acc == null) {
            AccountDialog.show(this);
            acc = Account.current();
            if (acc == null) return;
        }
        if (Game.isRunning(p.id) || !preparing.add(p.id)) return;
        updatePlay();
        Account fa = acc;
        Tasks.run("Запуск «" + p.name + "»", prog -> {
            Account.Session s = fa.session();
            if (MultiplayerFix.applies(p, fa) && !MultiplayerFix.ensure(p, fa, prog)) warnVanilla116(p);
            Game.Plan plan = Game.prepare(p, s, prog);
            Skins.applyForLaunch(p, fa);
            Game.start(p, plan, s);
        }, err -> {
            preparing.remove(p.id);
            if (err != null) Dlg.message(this, "Не удалось запустить игру", Tasks.friendly(err));
            else Tasks.bar.info("Игра запущена — установка «" + p.name + "», игрок " + fa.name);
            updatePlay();
        });
    }
}
