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
        setMinimumSize(new Dimension(1000, 660));
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
                g.setColor(new Color(0x2C2C2E));
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
        side.add(Box(10));
        addNav(side, "mods", "Моды", Ui.MODS);
        addNav(side, "packs", "Сборки", Ui.PACKS);
        addNav(side, "textures", "Текстуры", Ui.TEXTURES);
        addNav(side, "shaders", "Шейдеры", Ui.SHADERS);
        addNav(side, "skins", "Скины", Ui.SKIN);
        side.add(Box(10));
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
                int hv = Math.abs(nm.hashCode());
                g.setColor(Color.getHSBColor((hv % 360) / 360f, 0.45f, 0.55f));
                g.fillRoundRect(20, y, s, s, 8, 8);
                g.setColor(Color.WHITE);
                g.setFont(Ui.bold(18f));
                FontMetrics fm = g.getFontMetrics();
                String l = nm.substring(0, 1).toUpperCase();
                g.drawString(l, 20 + (s - fm.stringWidth(l)) / 2, y + (s - fm.getHeight()) / 2 + fm.getAscent());
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

    public void show(String key) {
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
