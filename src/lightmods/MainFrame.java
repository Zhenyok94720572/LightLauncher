package lightmods;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/** Главное окно: боковая панель навигации, страницы и строка состояния. */
public final class MainFrame extends JFrame {
    private final CardLayout cards = new CardLayout();
    private final JPanel pages = new JPanel(cards);
    private final Map<String, Ui.NavButton> navs = new LinkedHashMap<>();
    private final JLabel title = Ui.label("", 22f, Color.WHITE, true);
    private final JPanel topBar;
    private final BrowsePanel mods = new BrowsePanel("mod", "Поиск модов…", "моды");
    private final BrowsePanel packs = new BrowsePanel("modpack", "Поиск сборок…", "сборки");
    private final BrowsePanel textures = new BrowsePanel("resourcepack", "Поиск текстур-паков…", "текстур-паки");
    private final BrowsePanel shaders = new BrowsePanel("shader", "Поиск шейдеров…", "шейдеры");
    private final VersionsPanel versions = new VersionsPanel();
    private final Tasks.StatusBar status = new Tasks.StatusBar();

    public MainFrame() {
        super("Light Launcher");
        setIconImages(Ui.iconImages());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 660));
        setSize(1180, 760);
        setLocationRelativeTo(null);
        Tasks.bar = status;

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(Ui.BG);
        setContentPane(root);

        root.add(buildSidebar(), BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout());
        right.setBackground(Ui.BG);
        topBar = buildTopBar();
        right.add(topBar, BorderLayout.NORTH);
        pages.setOpaque(false);
        pages.add(new HomePanel(this::show), "home");
        pages.add(mods, "mods");
        pages.add(packs, "packs");
        pages.add(textures, "textures");
        pages.add(shaders, "shaders");
        pages.add(versions, "versions");
        pages.add(new SkinsPanel(), "skins");
        pages.add(new InstancesPanel(), "instances");
        pages.add(new SettingsPanel(), "settings");
        right.add(pages, BorderLayout.CENTER);
        root.add(right, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);

        show("home");
        Toast.install(getRootPane());
        installShortcuts();
        State.refresh();
        Tasks.IO.submit(() -> {
            try {
                Mc.manifest();
                Mc.latestRelease();
            } catch (Exception ignored) {
                // прогрев кэша
            }
        });
    }

    private JPanel buildSidebar() {
        JPanel side = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Ui.SIDEBAR);
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Ui.LINE);
                g.fillRect(getWidth() - 1, 0, 1, getHeight());
            }
        };
        side.setOpaque(false);
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setPreferredSize(new Dimension(224, 100));

        JPanel brand = new JPanel() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.paintAvatar(g, 20, 20, 48);
                g.setFont(Ui.bold(16.5f));
                FontMetrics fm = g.getFontMetrics();
                g.setColor(Color.WHITE);
                g.drawString("Light Launcher", 80, 41);
                g.setFont(Ui.font(11.5f));
                g.setColor(Ui.MUTED);
                g.drawString("Minecraft: Java Edition", 80, 59);
                g.dispose();
            }
        };
        brand.setOpaque(false);
        brand.setPreferredSize(new Dimension(224, 92));
        brand.setMaximumSize(new Dimension(Integer.MAX_VALUE, 92));
        side.add(brand);

        addNav(side, "home", "Главная", Ui.HOME);
        side.add(section("КАТАЛОГ"));
        addNav(side, "mods", "Моды", Ui.MODS);
        addNav(side, "packs", "Сборки", Ui.PACKS);
        addNav(side, "textures", "Текстуры", Ui.TEXTURES);
        addNav(side, "shaders", "Шейдеры", Ui.SHADERS);
        addNav(side, "skins", "Скины", Ui.SKIN);
        side.add(section("ИГРА"));
        addNav(side, "versions", "Версии", Ui.VERSIONS);
        addNav(side, "instances", "Установки", Ui.FOLDER);
        side.add(javax.swing.Box.createVerticalGlue());
        side.add(buildAccountChip());
        side.add(Box(6));
        addNav(side, "settings", "Настройки", Ui.GEAR);
        side.add(Box(14));
        return side;
    }

    private JPanel buildAccountChip() {
        JPanel chip = new JPanel() {
            private boolean hover;

            {
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
                        AccountDialog.show(MainFrame.this);
                    }
                });
                setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
                Account.addListener(this::repaint);
                Skins.addListener(() -> javax.swing.SwingUtilities.invokeLater(this::repaint));
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                if (hover) {
                    g.setColor(new Color(255, 255, 255, 16));
                    g.fillRoundRect(8, 0, getWidth() - 16, getHeight(), 10, 10);
                }
                Account a = Account.current();
                int s = 36;
                int y = (getHeight() - s) / 2;
                String nm = a == null ? I18n.t("Войти") : a.name;
                Heads.paint(g, 20, y, s, a, this);
                g.setFont(Ui.bold(13.5f));
                g.setColor(Color.WHITE);
                g.drawString(Ui.ellipsize(nm, g.getFontMetrics(), 140), 66, y + 15);
                g.setFont(Ui.font(11.5f));
                g.setColor(Ui.MUTED);
                g.drawString(I18n.t(a == null ? "Добавить аккаунт" : (a.isMs() ? "Microsoft" : a.isEly() ? "Ely.by" : "Офлайн")), 66, y + 31);
                g.dispose();
            }
        };
        chip.setOpaque(false);
        chip.setPreferredSize(new Dimension(224, 54));
        chip.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));
        return chip;
    }

    private static java.awt.Component section(String text) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 28, 6, 10));
        p.add(Ui.label(text, 10.5f, new Color(0x6F7480), true), BorderLayout.WEST);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static java.awt.Component Box(int h) {
        return javax.swing.Box.createVerticalStrut(h);
    }

    private void addNav(JPanel side, String key, String text, Ui.Glyph g) {
        Ui.NavButton b = new Ui.NavButton(text, g);
        b.addActionListener(e -> show(key));
        navs.put(key, b);
        side.add(b);
    }

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setOpaque(false);
        bar.setBorder(new EmptyBorder(18, 26, 12, 26));
        bar.add(title, BorderLayout.WEST);
        JPanel r = new JPanel(new GridBagLayout());
        r.setOpaque(false);
        JPanel box = new JPanel(new BorderLayout(10, 0));
        box.setOpaque(false);
        box.add(Ui.label("Установка:", 13.5f, Ui.MUTED, false), BorderLayout.WEST);
        InstanceCombo combo = new InstanceCombo();
        combo.setPreferredSize(new Dimension(310, 46));
        box.add(combo, BorderLayout.CENTER);
        r.add(box);
        bar.add(r, BorderLayout.EAST);
        return bar;
    }

    /** Ctrl+1…9 — разделы по порядку, Ctrl+F — поиск на текущей вкладке каталога. */
    private void installShortcuts() {
        String[] order = {"home", "mods", "packs", "textures", "shaders", "skins", "versions", "instances", "settings"};
        javax.swing.JComponent rp = getRootPane();
        int mask = java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        for (int i = 0; i < order.length; i++) {
            String k = order[i];
            rp.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_1 + i, mask), "nav-" + k);
            rp.getActionMap().put("nav-" + k, new javax.swing.AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    show(k);
                }
            });
        }
        rp.getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F, mask), "find");
        rp.getActionMap().put("find", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                BrowsePanel b = current == null ? null : browse(current);
                if (b == null) {
                    show("mods");
                    b = mods;
                }
                b.focusSearch();
            }
        });
    }

    private String current;

    private BrowsePanel browse(String key) {
        switch (key) {
            case "mods":
                return mods;
            case "packs":
                return packs;
            case "textures":
                return textures;
            case "shaders":
                return shaders;
            default:
                return null;
        }
    }

    public void show(String key) {
        current = key;
        for (Map.Entry<String, Ui.NavButton> e : navs.entrySet()) e.getValue().setSelectedNav(e.getKey().equals(key));
        boolean home = key.equals("home");
        topBar.setVisible(!home);
        String t;
        switch (key) {
            case "mods":
                t = "Моды";
                break;
            case "packs":
                t = "Сборки";
                break;
            case "textures":
                t = "Текстуры";
                break;
            case "shaders":
                t = "Шейдеры";
                break;
            case "skins":
                t = "Скины";
                break;
            case "versions":
                t = "Версии и загрузчики";
                break;
            case "instances":
                t = "Установки";
                break;
            case "settings":
                t = "Настройки";
                break;
            default:
                t = "";
        }
        title.setText(t);
        cards.show(pages, key);
        switch (key) {
            case "mods":
                mods.onShow();
                break;
            case "packs":
                packs.onShow();
                break;
            case "textures":
                textures.onShow();
                break;
            case "shaders":
                shaders.onShow();
                break;
            case "versions":
                versions.onShow();
                break;
            default:
                break;
        }
    }
}
