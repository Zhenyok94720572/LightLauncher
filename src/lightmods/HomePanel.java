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

/** Главная: живой пейзаж, приветствие, быстрые разделы, недавние установки и панель «Установка + ИГРАТЬ». */
public final class HomePanel extends JPanel {
    private final JLabel playedVal = Ui.label("—", 12.5f, Color.WHITE, true);
    private final JLabel lastVal = Ui.label("—", 12.5f, Color.WHITE, true);
    private final JPanel recentRow = new JPanel();
    private final Consumer<String> navigate;

    public HomePanel(Consumer<String> navigate) {
        super(new BorderLayout());
        this.navigate = navigate;
        setOpaque(false);

        JPanel hero = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                int w = getWidth();
                int h = getHeight();
                Ui.paintHero(g, w, h, System.currentTimeMillis());
                // затемнение снизу, чтобы карточки читались на любом фоне
                g.setPaint(new java.awt.GradientPaint(0, h * 0.30f, new Color(10, 12, 16, 0), 0, h, new Color(10, 12, 16, 215)));
                g.fillRect(0, 0, w, h);
                g.setPaint(new java.awt.GradientPaint(0, 0, new Color(10, 12, 16, 120), w * 0.55f, 0, new Color(10, 12, 16, 0)));
                g.fillRect(0, 0, w, h);
                int pad = 48;
                int s = 76;
                int y = 40;
                Ui.paintAvatar(g, pad, y, s);
                Account a = Account.current();
                g.setFont(Ui.font(15f));
                g.setColor(new Color(255, 255, 255, 215));
                String hi = a == null ? I18n.t("Добро пожаловать!") : I18n.t("Привет, ") + a.name + "!";
                g.drawString(hi, pad + s + 22, y + 24);
                g.setFont(Ui.bold(38f));
                g.setColor(new Color(0, 0, 0, 90));
                g.drawString("Light Launcher", pad + s + 23, y + 64);
                g.setColor(Color.WHITE);
                g.drawString("Light Launcher", pad + s + 22, y + 62);
                g.dispose();
            }
        };
        hero.setOpaque(false);
        javax.swing.Timer anim = new javax.swing.Timer(40, e -> hero.repaint());
        hero.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0) {
                if (hero.isShowing()) anim.start();
                else anim.stop();
            }
        });
        Account.addListener(hero::repaint);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new javax.swing.BoxLayout(content, javax.swing.BoxLayout.Y_AXIS));

        JLabel qh = Ui.label("РАЗДЕЛЫ", 11.5f, new Color(255, 255, 255, 170), true);
        qh.setAlignmentX(LEFT_ALIGNMENT);
        content.add(qh);
        content.add(javax.swing.Box.createVerticalStrut(8));
        JPanel quick = new JPanel();
        quick.setLayout(new javax.swing.BoxLayout(quick, javax.swing.BoxLayout.X_AXIS));
        quick.setOpaque(false);
        quick.setAlignmentX(LEFT_ALIGNMENT);
        Object[][] q = {{"Моды", "Тысячи модов", "mods", Ui.MODS}, {"Сборки", "Готовые наборы", "packs", Ui.PACKS},
            {"Текстуры", "Ресурс-паки", "textures", Ui.TEXTURES}, {"Шейдеры", "Свет и тени", "shaders", Ui.SHADERS},
            {"Версии", "Fabric, Forge…", "versions", Ui.VERSIONS}};
        for (Object[] a : q) {
            if (quick.getComponentCount() > 0) quick.add(javax.swing.Box.createHorizontalStrut(12));
            quick.add(new Tile((String) a[0], (String) a[1], (Ui.Glyph) a[3], () -> navigate.accept((String) a[2])));
        }
        content.add(quick);
        content.add(javax.swing.Box.createVerticalStrut(20));

        JLabel rh = Ui.label("НЕДАВНИЕ УСТАНОВКИ", 11.5f, new Color(255, 255, 255, 170), true);
        rh.setAlignmentX(LEFT_ALIGNMENT);
        content.add(rh);
        content.add(javax.swing.Box.createVerticalStrut(8));
        recentRow.setOpaque(false);
        recentRow.setLayout(new javax.swing.BoxLayout(recentRow, javax.swing.BoxLayout.X_AXIS));
        recentRow.setAlignmentX(LEFT_ALIGNMENT);
        content.add(recentRow);

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.weighty = 1;
        c.anchor = GridBagConstraints.SOUTHWEST;
        c.insets = new Insets(0, 36, 26, 24);
        hero.add(content, c);
        add(hero, BorderLayout.CENTER);

        JPanel bar = new JPanel(new BorderLayout(24, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Ui.BAR);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Ui.LINE);
                g.fillRect(0, 0, getWidth(), 1);
            }
        };
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(14, 24, 14, 22));
        bar.setPreferredSize(new Dimension(10, 96));

        JPanel left = new JPanel(new BorderLayout(0, 4));
        left.setOpaque(false);
        left.add(Ui.label("УСТАНОВКА", 11f, Ui.MUTED, true), BorderLayout.NORTH);
        InstanceCombo combo = new InstanceCombo();
        combo.setPreferredSize(new Dimension(270, 46));
        left.add(combo, BorderLayout.CENTER);
        bar.add(left, BorderLayout.WEST);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        Ui.McButton play = new Ui.McButton("ИГРАТЬ", Ui.McButton.Kind.GREEN).size(24f);
        play.setPreferredSize(new Dimension(226, 58));
        playBtn = play;
        play.addActionListener(e -> {
            Mc.Profile p = State.current();
            if (p != null && Game.isRunning(p.id)) {
                if (Dlg.confirm(this, "Игра запущена", "Закрыть Minecraft («" + p.name + "») принудительно? Несохранённый прогресс может пропасть.", "Закрыть игру")) Game.kill(p.id);
                return;
            }
            launch(play);
        });
        Game.addListener(this::gameChanged);
        center.add(play);
        bar.add(center, BorderLayout.CENTER);

        JPanel right = new JPanel(new BorderLayout(14, 0));
        right.setOpaque(false);
        JPanel stats = new JPanel(new java.awt.GridLayout(2, 1, 0, 4));
        stats.setOpaque(false);
        stats.add(statLine("Наиграно:", playedVal));
        stats.add(statLine("Последний запуск:", lastVal));
        right.add(stats, BorderLayout.CENTER);
        Ui.McButton folder = new Ui.McButton("", Ui.McButton.Kind.DARK) {
            @Override
            protected void paintComponent(Graphics g0) {
                super.paintComponent(g0);
                Graphics2D g = Ui.aa(g0);
                g.setColor(new Color(0xD6D9E0));
                Ui.FOLDER.paint(g, (getWidth() - 20) / 2, (getHeight() - 20) / 2 - 1, 20);
                g.dispose();
            }
        };
        folder.setPreferredSize(new Dimension(44, 44));
        folder.setToolTipText(I18n.t("Открыть папку игры"));
        folder.addActionListener(e -> {
            Mc.Profile p = State.current();
            if (p == null) return;
            try {
                java.nio.file.Files.createDirectories(p.gameDir);
                java.awt.Desktop.getDesktop().open(p.gameDir.toFile());
            } catch (Exception ex) {
                Log.i("Не удалось открыть папку: " + ex.getMessage());
            }
        });
        JPanel fw = new JPanel(new GridBagLayout());
        fw.setOpaque(false);
        fw.add(folder);
        right.add(fw, BorderLayout.EAST);
        bar.add(right, BorderLayout.EAST);
        add(bar, BorderLayout.SOUTH);

        State.addListener(() -> {
            update();
            updatePlay();
        });
        new javax.swing.Timer(1000, e -> {
            Mc.Profile p = State.current();
            if (p != null && Game.isRunning(p.id)) updatePlay();
        }).start();
        update();
    }

    private static JPanel statLine(String head, JLabel val) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        p.setOpaque(false);
        p.add(Ui.label(head, 12.5f, Ui.MUTED, false));
        p.add(val);
        return p;
    }

    private void update() {
        Mc.Profile p = State.current();
        if (p == null) {
            playedVal.setText("—");
            lastVal.setText("—");
        } else {
            Stats.Entry e = Stats.get(p.id);
            playedVal.setText(e.seconds == 0 ? "—" : Stats.duration(e.seconds));
            lastVal.setText(e.lastPlayed <= 0 ? "—" : Stats.ago(e.lastPlayed));
        }
        rebuildRecent();
    }

    private void rebuildRecent() {
        recentRow.removeAll();
        java.util.List<Mc.Profile> ps = new java.util.ArrayList<>(State.profiles());
        ps.sort((a, b) -> Long.compare(Stats.get(b.id).lastPlayed, Stats.get(a.id).lastPlayed));
        int n = 0;
        for (Mc.Profile p : ps) {
            if (n++ >= 4) break;
            if (recentRow.getComponentCount() > 0) recentRow.add(javax.swing.Box.createHorizontalStrut(12));
            recentRow.add(new RecentCard(p));
        }
        if (ps.isEmpty()) {
            Ui.McButton mk = new Ui.McButton("Создать первую установку", Ui.McButton.Kind.GREEN).size(14f);
            mk.addActionListener(e -> navigate.accept("versions"));
            recentRow.add(mk);
        }
        recentRow.revalidate();
        recentRow.repaint();
    }

    /** Полупрозрачная плитка раздела. */
    private static final class Tile extends JPanel {
        private boolean hover;

        Tile(String title, String sub, Ui.Glyph glyph, Runnable action) {
            setOpaque(false);
            setPreferredSize(new Dimension(150, 84));
            setMaximumSize(new Dimension(150, 84));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            String t = I18n.t(title);
            String st = I18n.t(sub);
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    action.run();
                }
            });
            putClientProperty("t", t);
            putClientProperty("st", st);
            putClientProperty("g", glyph);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.aa(g0);
            int w = getWidth();
            int h = getHeight();
            Ui.card(g, 0, 0, w, h, new Color(20, 22, 28, hover ? 225 : 175), new Color(255, 255, 255, hover ? 70 : 34));
            g.setColor(hover ? Ui.GREEN_HI : new Color(0xDCE7D6));
            ((Ui.Glyph) getClientProperty("g")).paint(g, 14, 14, 22);
            g.setColor(Color.WHITE);
            g.setFont(Ui.bold(14.5f));
            g.drawString((String) getClientProperty("t"), 14, 58);
            g.setFont(Ui.font(11.5f));
            g.setColor(new Color(255, 255, 255, 150));
            g.drawString(Ui.ellipsize((String) getClientProperty("st"), g.getFontMetrics(), w - 24), 14, 74);
            g.dispose();
        }
    }

    /** Карточка недавней установки: клик — выбрать, двойной клик — сразу играть. */
    private final class RecentCard extends JPanel {
        private final Mc.Profile p;
        private boolean hover;

        RecentCard(Mc.Profile p) {
            this.p = p;
            setOpaque(false);
            setPreferredSize(new Dimension(212, 62));
            setMaximumSize(new Dimension(212, 62));
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            setToolTipText(I18n.t("Клик — выбрать, двойной клик — играть"));
            addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    State.select(p.id);
                    if (e.getClickCount() >= 2 && playBtn != null && playBtn.isEnabled() && !Game.isRunning(p.id)) launch(playBtn);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.aa(g0);
            int w = getWidth();
            int h = getHeight();
            Mc.Profile cur = State.current();
            boolean sel = cur != null && cur.id.equals(p.id);
            Ui.card(g, 0, 0, w, h, new Color(20, 22, 28, hover ? 225 : 175),
                    sel ? new Color(Ui.GREEN_HI.getRed(), Ui.GREEN_HI.getGreen(), Ui.GREEN_HI.getBlue(), 200) : new Color(255, 255, 255, hover ? 70 : 34));
            InstanceCombo.InstanceIcon.paint(g, 12, (h - 36) / 2, 36, p);
            g.setFont(Ui.bold(13.5f));
            g.setColor(Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(Ui.ellipsize(p.name, fm, w - 70), 58, 27);
            g.setFont(Ui.font(11.5f));
            g.setColor(new Color(255, 255, 255, 160));
            Stats.Entry e = Stats.get(p.id);
            String sub = Game.isRunning(p.id) ? I18n.t("● в игре") : (e.lastPlayed > 0 ? p.subtitle() + " · " + Stats.ago(e.lastPlayed) : p.subtitle());
            g.drawString(Ui.ellipsize(sub, g.getFontMetrics(), w - 70), 58, 45);
            g.dispose();
        }
    }

    private Ui.McButton playBtn;
    private final java.util.Set<String> preparing = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void updatePlay() {
        if (playBtn == null) return;
        Mc.Profile p = State.current();
        if (p != null && Game.isRunning(p.id)) {
            Long t0 = Game.STARTED.get(p.id);
            long sec = t0 == null ? 0 : (System.currentTimeMillis() - t0) / 1000;
            playBtn.setText(I18n.t("В ИГРЕ") + String.format("  %d:%02d", sec / 60, sec % 60));
            playBtn.setToolTipText(I18n.t("Нажмите, чтобы закрыть игру"));
            playBtn.setEnabled(true);
        } else if (p != null && preparing.contains(p.id)) {
            playBtn.setText("ЗАПУСК…");
            playBtn.setEnabled(false);
        } else {
            playBtn.setText("ИГРАТЬ");
            playBtn.setToolTipText(null);
            playBtn.setEnabled(true);
        }
        recentRow.repaint();
    }

    private void gameChanged() {
        javax.swing.SwingUtilities.invokeLater(() -> {
            for (Mc.Profile p : State.profiles()) {
                Integer code = Game.LAST_EXIT.get(p.id);
                if (code != null && !Game.isRunning(p.id)) {
                    Game.LAST_EXIT.remove(p.id);
                    boolean killed = Game.KILLED.remove(p.id);
                    if (code != 0 && !killed) CrashDialog.show(this, p, code);
                    else Toast.show(I18n.t("Игра закрыта — наиграно за сеанс: ") + Stats.duration(sessionSeconds(p)), Toast.Kind.INFO);
                }
            }
            update();
            updatePlay();
        });
    }

    private final java.util.Map<String, Long> lastTotal = new java.util.HashMap<>();

    private long sessionSeconds(Mc.Profile p) {
        long now = Stats.get(p.id).seconds;
        Long before = lastTotal.put(p.id, now);
        return before == null ? 0 : Math.max(0, now - before);
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
        lastTotal.put(p.id, Stats.get(p.id).seconds);
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
