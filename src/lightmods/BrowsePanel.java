package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/** Поиск и установка проектов Modrinth одного типа: моды, сборки, текстуры или шейдеры. */
public final class BrowsePanel extends JPanel {
    private static final String[][] SORTS = {
            {"Релевантность", "relevance"}, {"Загрузки", "downloads"}, {"Подписчики", "follows"},
            {"Новые", "newest"}, {"Недавно обновлённые", "updated"}};
    private static final int PAGE = 20;

    private final String type;
    private final String noun;
    private final Ui.Field search;
    private final JComboBox<String> sort = new JComboBox<>();
    private final JCheckBox compat = Ui.check("Только совместимые", true);
    private final JComboBox<String> packMc = new JComboBox<>();
    private final JComboBox<String> packLoader = new JComboBox<>();
    private final JLabel filterInfo = Ui.label(" ", 12.5f, Ui.MUTED, false);
    private final VPanel list = new VPanel();
    private final JPanel footer = new JPanel();
    private final Timer debounce;
    private int offset;
    private int gen;
    private boolean loadedOnce;
    private boolean dirty;

    private static final class NoCompat extends Exception {
        private static final long serialVersionUID = 1L;
    }

    public BrowsePanel(String type, String placeholder, String noun) {
        super(new BorderLayout(0, 12));
        this.type = type;
        this.noun = noun;
        setOpaque(false);
        setBorder(new EmptyBorder(6, 26, 10, 26));

        search = new Ui.Field(placeholder);
        for (String[] s : SORTS) sort.addItem(I18n.t(s[0]));
        Ui.styleCombo(sort);
        sort.setPreferredSize(new Dimension(210, 38));

        JPanel top = new JPanel(new GridBagLayout());
        top.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        top.add(search, c);
        c.weightx = 0;
        c.insets = new Insets(0, 10, 0, 0);
        top.add(sort, c);
        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = 2;
        c.insets = new Insets(10, 0, 0, 0);
        c.weightx = 1;
        JPanel row = new JPanel(new BorderLayout(14, 0));
        row.setOpaque(false);
        if (type.equals("modpack")) {
            packMc.addItem(I18n.t("Все"));
            for (String v : new String[]{"1.21.4", "1.21.1", "1.20.6", "1.20.4", "1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.16.5", "1.12.2"}) packMc.addItem(v);
            packLoader.addItem(I18n.t("Все"));
            for (String v : new String[]{"Fabric", "Forge", "NeoForge", "Quilt"}) packLoader.addItem(v);
            packMc.setPreferredSize(new Dimension(130, 34));
            packLoader.setPreferredSize(new Dimension(140, 34));
            Ui.styleCombo(packMc);
            Ui.styleCombo(packLoader);
            JPanel filt = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            filt.setOpaque(false);
            filt.add(Ui.label("Версия Minecraft", 13f, Ui.MUTED, false));
            filt.add(packMc);
            filt.add(Ui.label("Загрузчик", 13f, Ui.MUTED, false));
            filt.add(packLoader);
            row.add(filt, BorderLayout.WEST);
            java.awt.event.ActionListener al = e -> {
                if (isShowing()) reload();
                else dirty = true;
            };
            packMc.addActionListener(al);
            packLoader.addActionListener(al);
            loadMcVersions();
        } else {
            row.add(compat, BorderLayout.WEST);
        }
        row.add(filterInfo, BorderLayout.CENTER);
        top.add(row, c);
        add(top, BorderLayout.NORTH);

        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        footer.setOpaque(false);
        footer.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 10));
        JPanel stack = new VPanel();
        stack.setOpaque(false);
        stack.setLayout(new BorderLayout());
        stack.add(list, BorderLayout.NORTH);
        stack.add(footer, BorderLayout.CENTER);
        JPanel holder = new VPanel();
        holder.setOpaque(false);
        holder.setLayout(new BorderLayout());
        holder.add(stack, BorderLayout.NORTH);
        JScrollPane sp = new JScrollPane(holder, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Ui.styleScroll(sp);
        add(sp, BorderLayout.CENTER);

        debounce = new Timer(450, e -> reload());
        debounce.setRepeats(false);
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                debounce.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                debounce.restart();
            }
        });
        search.addActionListener(e -> {
            debounce.stop();
            reload();
        });
        sort.addActionListener(e -> {
            if (loadedOnce) reload();
        });
        compat.addActionListener(e -> {
            if (loadedOnce) reload();
        });
        State.addListener(() -> {
            updateFilterInfo();
            dirty = true;
            if (isShowing() && compat.isSelected()) {
                dirty = false;
                reload();
            }
        });
        compat.setVisible(!type.equals("modpack"));
        updateFilterInfo();
    }

    /** Подтягивает полный список релизов Minecraft из Modrinth для фильтра сборок (до этого стоит короткий список). */
    private void loadMcVersions() {
        Tasks.IO.submit(() -> {
            try {
                List<Map<String, Object>> vs = new ArrayList<>();
                for (Object o : Json.list(Net.getJson(Modrinth.BASE + "/tag/game_version"))) {
                    Map<String, Object> m = Json.map(o);
                    if ("release".equals(Json.str(m, "version_type"))) vs.add(m);
                }
                vs.sort((a, b) -> Json.str(b, "date", "").compareTo(Json.str(a, "date", "")));
                List<String> names = new ArrayList<>();
                for (Map<String, Object> m : vs) names.add(Json.str(m, "version"));
                if (names.isEmpty()) return;
                SwingUtilities.invokeLater(() -> {
                    Object keep = packMc.getSelectedItem();
                    java.awt.event.ActionListener[] ls = packMc.getActionListeners();
                    for (java.awt.event.ActionListener l : ls) packMc.removeActionListener(l);
                    packMc.removeAllItems();
                    packMc.addItem(I18n.t("Все"));
                    for (String n : names) packMc.addItem(n);
                    packMc.setSelectedItem(keep);
                    if (packMc.getSelectedItem() == null) packMc.setSelectedIndex(0);
                    for (java.awt.event.ActionListener l : ls) packMc.addActionListener(l);
                });
            } catch (Exception e) {
                Log.i("Список версий Minecraft: " + e.getMessage());
            }
        });
    }

    /** Вызывается при открытии вкладки. */
    public void onShow() {
        if (!loadedOnce || dirty) {
            loadedOnce = true;
            dirty = false;
            reload();
        }
    }

    private void updateFilterInfo() {
        Mc.Profile p = State.current();
        if (type.equals("modpack")) filterInfo.setText("Сборка ставится в новую изолированную установку");
        else if (!compat.isSelected()) filterInfo.setText("Показываются все " + noun);
        else if (p == null) filterInfo.setText("Нет установки — создайте её во вкладке «Версии»");
        else filterInfo.setText("Для установки «" + p.name + "»: Minecraft " + (p.mc == null ? "?" : p.mc) + (isLoaderType() ? " · " + p.loaderTitle() : ""));
    }

    private boolean isLoaderType() {
        return type.equals("mod");
    }

    private void reload() {
        offset = 0;
        list.removeAll();
        footer.removeAll();
        list.revalidate();
        list.repaint();
        updateFilterInfo();
        loadPage();
    }

    private void loadPage() {
        int myGen = ++gen;
        Mc.Profile inst = State.current();
        boolean f = compat.isSelected() && inst != null && !type.equals("modpack");
        boolean pack = type.equals("modpack");
        String mc = pack ? (packMc.getSelectedIndex() <= 0 ? null : (String) packMc.getSelectedItem()) : f ? inst.mc : null;
        String loader = pack ? (packLoader.getSelectedIndex() <= 0 ? null : ((String) packLoader.getSelectedItem()).toLowerCase()) : f ? inst.loader : null;
        String q = search.getText();
        String sortKey = SORTS[Math.max(0, sort.getSelectedIndex())][1];
        int off = offset;
        footer.removeAll();
        footer.add(Ui.label("Загрузка…", 14f, Ui.MUTED, false));
        footer.revalidate();
        footer.repaint();
        Tasks.IO.submit(() -> {
            try {
                List<Modrinth.Hit> hits = Modrinth.search(q, type, mc, loader, sortKey, off, PAGE);
                // обложки качаем сразу, пока строится список, и даём им чуть времени — карточки появятся уже с картинками
                List<java.util.concurrent.Future<?>> fs = new ArrayList<>();
                for (Modrinth.Hit h : hits) {
                    java.util.concurrent.Future<?> pf = prefetch(h.iconUrl, null);
                    if (pf != null) fs.add(pf);
                }
                long until = System.currentTimeMillis() + 900;
                for (java.util.concurrent.Future<?> pf : fs) {
                    long left = until - System.currentTimeMillis();
                    if (left <= 0) break;
                    try {
                        pf.get(left, java.util.concurrent.TimeUnit.MILLISECONDS);
                    } catch (Exception ignored) {
                        break;
                    }
                }
                SwingUtilities.invokeLater(() -> {
                    if (myGen == gen) append(hits, inst);
                });
            } catch (Exception e) {
                Log.i("Поиск: " + e);
                SwingUtilities.invokeLater(() -> {
                    if (myGen == gen) showError(e);
                });
            }
        });
    }

    private void append(List<Modrinth.Hit> hits, Mc.Profile inst) {
        Map<String, Object> reg = inst == null ? Map.of() : Mc.registry(inst.gameDir);
        for (Modrinth.Hit h : hits) {
            Card c = new Card(h, reg.containsKey(h.id) && !type.equals("modpack"));
            list.add(c);
            list.add(Box.createVerticalStrut(8));
        }
        offset += hits.size();
        footer.removeAll();
        if (hits.size() >= PAGE) {
            Ui.McButton more = new Ui.McButton("Показать ещё", Ui.McButton.Kind.DARK);
            more.addActionListener(e -> loadPage());
            footer.add(more);
        } else if (offset == 0) {
            footer.add(Ui.label("Ничего не найдено. Попробуйте другой запрос или выключите фильтр совместимости.", 14f, Ui.MUTED, false));
        }
        list.revalidate();
        list.repaint();
        footer.revalidate();
        footer.repaint();
    }

    private void showError(Exception e) {
        footer.removeAll();
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        p.setOpaque(false);
        p.add(Ui.label("Не удалось загрузить: " + Tasks.friendly(e), 13.5f, new Color(0xFF7B72), false));
        Ui.McButton retry = new Ui.McButton("Повторить", Ui.McButton.Kind.DARK);
        retry.addActionListener(ev -> loadPage());
        p.add(retry);
        footer.add(p);
        footer.revalidate();
        footer.repaint();
    }

    // ------------------------------------------------------------------ установка

    private void install(Modrinth.Hit hit, Ui.McButton btn) {
        Mc.Profile inst = State.current();
        if (type.equals("modpack")) {
            btn.setEnabled(false);
            btn.setText("Загрузка…");
            Tasks.IO.submit(() -> {
                try {
                    List<Modrinth.Ver> vs = Modrinth.versions(hit.id, null, null);
                    SwingUtilities.invokeLater(() -> {
                        Modrinth.Ver v = vs.isEmpty() ? null : PackChooser.choose(this, hit.title, vs, packMc.getSelectedIndex() <= 0 ? null : (String) packMc.getSelectedItem(), packLoader.getSelectedIndex() <= 0 ? null : ((String) packLoader.getSelectedItem()).toLowerCase());
                        if (v == null) {
                            btn.setText("Установить");
                            btn.setEnabled(true);
                            if (vs.isEmpty()) Dlg.message(this, "Не удалось установить сборку", "У сборки нет доступных версий");
                            return;
                        }
                        btn.setText("Скачивание…");
                        installPack(hit, btn, v);
                    });
                } catch (Exception e) {
                    SwingUtilities.invokeLater(() -> {
                        btn.setText("Установить");
                        btn.setEnabled(true);
                        Dlg.message(this, "Не удалось установить сборку", Tasks.friendly(e));
                    });
                }
            });
            return;
        }
        if (inst == null) {
            Dlg.message(this, "Нет установки", "Сначала создайте установку: откройте вкладку «Версии» и установите нужную версию Minecraft (с Fabric или Forge — для модов).");
            return;
        }
        if (type.equals("mod") && !inst.hasLoader()) {
            Dlg.message(this, "Нужен мод-загрузчик", "Выбранная установка «" + inst.name + "» — чистый Vanilla, моды в ней не работают. Для установки модов выберите версию с модлаунчером (Fabric, Forge, NeoForge или Quilt) — её можно поставить во вкладке «Версии».");
            return;
        }
        btn.setEnabled(false);
        btn.setText("Скачивание…");
        installSingle(hit, inst, btn, null);
    }

    private void installSingle(Modrinth.Hit hit, Mc.Profile inst, Ui.McButton btn, Modrinth.Ver forced) {
        List<String>[] notes = new List[1];
        Tasks.run("Установка: " + hit.title, p -> {
            Modrinth.Ver v = forced;
            if (v == null) {
                List<Modrinth.Ver> vs = Modrinth.versions(hit.id, inst.mc, type.equals("mod") ? Modrinth.loadersFor(inst.loader) : null);
                v = Modrinth.pickBest(vs);
                if (v == null) throw new NoCompat();
            }
            notes[0] = Modrinth.installVersion(v, hit.title, type, inst, p);
        }, err -> {
            if (err == null) {
                btn.setText("Установлено");
                btn.setEnabled(false);
                if (notes[0] != null && !notes[0].isEmpty()) {
                    Dlg.message(this, "Установлено с замечаниями", "«" + hit.title + "» установлен.\n\n• " + String.join("\n• ", notes[0]));
                }
            } else if (err instanceof NoCompat) {
                askAlternative(hit, inst, btn);
            } else {
                btn.setText("Установить");
                btn.setEnabled(true);
                Dlg.message(this, "Не удалось установить", Tasks.friendly(err));
            }
        });
    }

    private void askAlternative(Modrinth.Hit hit, Mc.Profile inst, Ui.McButton btn) {
        Tasks.IO.submit(() -> {
            List<Modrinth.Ver> all;
            try {
                all = Modrinth.versions(hit.id, null, type.equals("mod") ? Modrinth.loadersFor(inst.loader) : null);
            } catch (IOException e) {
                all = new ArrayList<>();
            }
            List<Modrinth.Ver> fin = all;
            SwingUtilities.invokeLater(() -> {
                if (fin.isEmpty()) {
                    btn.setText("Установить");
                    btn.setEnabled(true);
                    Dlg.message(this, "Нет подходящей версии", "Для «" + hit.title + "» нет версий под " + inst.subtitle() + ".");
                    return;
                }
                Modrinth.Ver ch = Dlg.choose(this, "Нет точной версии", "Для " + inst.subtitle() + " у «" + hit.title + "» нет версии. Можно выбрать другую — она может не заработать с этой установкой.", fin);
                if (ch == null) {
                    btn.setText("Установить");
                    btn.setEnabled(true);
                } else installSingle(hit, inst, btn, ch);
            });
        });
    }

    private void installPack(Modrinth.Hit hit, Ui.McButton btn, Modrinth.Ver chosen) {
        String[] created = new String[1];
        Mc.Profile inst = State.current();
        boolean f = false;
        Tasks.run("Сборка: " + hit.title, p -> {
            Modrinth.Ver v = chosen;
            Mc.Profile np = Modrinth.installModpack(hit.title, v, p);
            created[0] = np.id;
        }, err -> {
            if (err == null) {
                btn.setText("Установлено");
                State.refreshAndSelect(created[0]);
                Dlg.message(this, "Сборка установлена", "Создана изолированная установка «" + hit.title + "» и выбрана в списке. Нажмите «ИГРАТЬ» на главной — всё нужное докачается при первом запуске.");
            } else {
                btn.setText("Установить");
                btn.setEnabled(true);
                Dlg.message(this, "Не удалось установить сборку", Tasks.friendly(err));
            }
        });
    }

    // ------------------------------------------------------------------ карточка

    private static final Map<String, BufferedImage> ICONS = new ConcurrentHashMap<>();
    private static final Set<String> BAD = ConcurrentHashMap.newKeySet();
    private static final java.util.concurrent.ExecutorService ICON_POOL = java.util.concurrent.Executors.newFixedThreadPool(24, r -> {
        Thread t = new Thread(r, "lightmods-icons");
        t.setDaemon(true);
        return t;
    });

    /**
     * Загружает обложку по ссылке на оригинал (Modrinth.search подменяет WebP-миниатюры на raw_icon_url),
     * уменьшает и кэширует на диск.
     */
    static BufferedImage loadIcon(String url) {
        java.nio.file.Path cache = java.nio.file.Paths.get(System.getProperty("user.home"), ".lightmods", "cache", "icons", Integer.toHexString(url.hashCode()) + Integer.toHexString(url.length()) + ".png");
        try {
            if (java.nio.file.Files.isRegularFile(cache)) {
                BufferedImage c = ImageIO.read(cache.toFile());
                if (c != null) return c;
            }
        } catch (IOException ignored) {
            // перекачаем
        }
        BufferedImage im = fetchIcon(url);
        if (im != null) {
            try {
                java.nio.file.Files.createDirectories(cache.getParent());
                ImageIO.write(im, "png", cache.toFile());
            } catch (IOException ignored) {
                // кэш необязателен
            }
        }
        return im;
    }

    private static BufferedImage fetchOne(String u) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                BufferedImage im = ImageIO.read(new ByteArrayInputStream(Net.getBytes(u)));
                if (im != null) return shrink(im, 168);
                return null;
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("HTTP 4") && !e.getMessage().startsWith("HTTP 429")) return null;
            }
        }
        return null;
    }

    private static BufferedImage fetchIcon(String url) {
        return fetchOne(url);
    }

    private static final Map<String, java.util.concurrent.Future<?>> INFLIGHT = new ConcurrentHashMap<>();

    /** Запускает загрузку обложки один раз; возвращает задачу (или null, если уже готова). */
    static java.util.concurrent.Future<?> prefetch(String url, Runnable done) {
        if (url == null || ICONS.containsKey(url) || BAD.contains(url)) return null;
        return INFLIGHT.computeIfAbsent(url, u -> ICON_POOL.submit(() -> {
            BufferedImage im = null;
            for (int attempt = 0; attempt < 4 && im == null; attempt++) {
                im = loadIcon(u);
                if (im == null) {
                    try {
                        Thread.sleep(800L * (attempt + 1));
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            if (im == null) BAD.add(u);
            else ICONS.put(u, im);
            INFLIGHT.remove(u);
            if (done != null) done.run();
        }));
    }

    private static BufferedImage shrink(BufferedImage im, int max) {
        if (im.getWidth() <= max && im.getHeight() <= max) return im;
        double k = Math.min((double) max / im.getWidth(), (double) max / im.getHeight());
        int w = Math.max(1, (int) (im.getWidth() * k));
        int h = Math.max(1, (int) (im.getHeight() * k));
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(im, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private final class Card extends JPanel {
        private final Modrinth.Hit hit;
        private boolean hover;
        private final Ui.McButton btn;

        Card(Modrinth.Hit hit, boolean installed) {
            super(new BorderLayout(14, 0));
            this.hit = hit;
            setOpaque(false);
            setBorder(new EmptyBorder(12, 12, 12, 16));
            setPreferredSize(new Dimension(100, 108));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 108));
            setAlignmentX(Component.LEFT_ALIGNMENT);
            add(new IconView(), BorderLayout.WEST);
            add(new TextView(), BorderLayout.CENTER);
            btn = new Ui.McButton(installed ? "Установлено" : "Установить", installed ? Ui.McButton.Kind.DARK : Ui.McButton.Kind.GREEN).size(13.5f);
            btn.setEnabled(!installed);
            btn.setPreferredSize(new Dimension(138, 38));
            btn.addActionListener(e -> install(hit, btn));
            JPanel east = new JPanel(new GridBagLayout());
            east.setOpaque(false);
            east.add(btn);
            add(east, BorderLayout.EAST);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = Ui.aa(g0);
            Ui.card(g, 0, 0, getWidth(), getHeight(), hover ? Ui.CARD_HI : Ui.CARD, Ui.BORDER);
            g.dispose();
        }

        private final class IconView extends JComponent {
            IconView() {
                setPreferredSize(new Dimension(84, 84));
                if (hit.iconUrl != null && !ICONS.containsKey(hit.iconUrl) && !BAD.contains(hit.iconUrl)) {
                    java.util.concurrent.Future<?> f = INFLIGHT.get(hit.iconUrl);
                    if (f == null) prefetch(hit.iconUrl, null);
                    watch(hit.iconUrl);
                }
            }

            private void watch(String url) {
                Thread t = new Thread(() -> {
                    for (int i = 0; i < 600 && !ICONS.containsKey(url) && !BAD.contains(url); i++) {
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException ie) {
                            return;
                        }
                    }
                    SwingUtilities.invokeLater(this::repaint);
                }, "lightmods-icon-watch");
                t.setDaemon(true);
                t.start();
            }

            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                int s = 84;
                int y = (getHeight() - s) / 2;
                BufferedImage im = hit.iconUrl == null ? null : ICONS.get(hit.iconUrl);
                java.awt.Shape clip = new RoundRectangle2D.Double(0, y, s, s, 14, 14);
                if (im != null) {
                    // clip() пересекает с текущей областью (границы окна прокрутки); setClip() заменял бы её, и иконки
                    // наполовину прокрученных карточек рисовались поверх соседних элементов
                    Graphics2D gc = (Graphics2D) g.create();
                    gc.clip(clip);
                    gc.drawImage(im, 0, y, s, s, null);
                    gc.dispose();
                } else {
                    int hv = Math.abs(hit.title.hashCode());
                    Color base = Color.getHSBColor((hv % 360) / 360f, 0.45f, 0.55f);
                    g.setColor(base);
                    g.fill(clip);
                    g.setColor(Color.WHITE);
                    g.setFont(Ui.bold(34f));
                    FontMetrics fm = g.getFontMetrics();
                    String l = hit.title.isEmpty() ? "?" : hit.title.substring(0, 1).toUpperCase();
                    g.drawString(l, (s - fm.stringWidth(l)) / 2, y + (s - fm.getHeight()) / 2 + fm.getAscent());
                }
                g.setColor(new Color(255, 255, 255, 36));
                g.draw(new RoundRectangle2D.Double(0.5, y + 0.5, s - 1, s - 1, 14, 14));
                g.dispose();
            }
        }

        private final class TextView extends JComponent {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                int w = getWidth();
                g.setFont(Ui.bold(16f));
                FontMetrics fm = g.getFontMetrics();
                String title = Ui.ellipsize(hit.title, fm, w - 10);
                int y = 22;
                g.setColor(Color.WHITE);
                g.drawString(title, 0, y);
                if (!hit.author.isEmpty()) {
                    int tw = fm.stringWidth(title);
                    g.setFont(Ui.font(12.5f));
                    g.setColor(Ui.MUTED);
                    String a = "  от " + hit.author;
                    FontMetrics f2 = g.getFontMetrics();
                    if (tw + f2.stringWidth(a) < w) g.drawString(a, tw, y);
                }
                g.setFont(Ui.font(13.5f));
                FontMetrics f3 = g.getFontMetrics();
                g.setColor(new Color(0xD0D0D4));
                List<String> lines = Ui.wrap(hit.description, f3, w - 8, 2);
                int ly = y + 22;
                for (String l : lines) {
                    g.drawString(l, 0, ly);
                    ly += 18;
                }
                g.setFont(Ui.font(12f));
                g.setColor(Ui.MUTED);
                StringBuilder st = new StringBuilder("Загрузок: " + Modrinth.fmtCount(hit.downloads) + "   ·   В избранном: " + Modrinth.fmtCount(hit.follows));
                String cats = categories();
                if (!cats.isEmpty()) st.append("   ·   ").append(cats);
                g.drawString(Ui.ellipsize(I18n.t(st.toString()), g.getFontMetrics(), w - 6), 0, getHeight() - 8);
                g.dispose();
            }

            private String categories() {
                List<String> shown = new ArrayList<>();
                for (String c : hit.categories) {
                    if (shown.size() >= 3) break;
                    shown.add(c);
                }
                return String.join(", ", shown);
            }
        }
    }

    private static final class VPanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) {
            return 30;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) {
            return Math.max(60, r.height - 60);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
