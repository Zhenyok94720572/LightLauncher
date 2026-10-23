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
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

/** Настройки: папка Minecraft и журнал. */
public final class SettingsPanel extends JPanel {
    private final Ui.Field dirField = new Ui.Field("Папка .minecraft");
    private final JTextArea log = new JTextArea();

    public SettingsPanel() {
        super(new BorderLayout(0, 14));
        setOpaque(false);
        setBorder(new EmptyBorder(8, 26, 16, 26));

        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.card(g, 0, 0, getWidth(), getHeight(), Ui.CARD, Ui.BORDER);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 4;
        c.weightx = 1;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        card.add(Ui.label("Папка Light Launcher (версии, установки, Java)", 13f, Ui.MUTED, false), c);
        c.gridy++;
        c.gridwidth = 1;
        c.insets = new Insets(6, 0, 0, 8);
        dirField.setText(Mc.dir.toString());
        card.add(dirField, c);
        c.gridx++;
        c.weightx = 0;
        c.insets = new Insets(6, 0, 0, 8);
        Ui.McButton browse = new Ui.McButton("Выбрать…", Ui.McButton.Kind.DARK).size(13f);
        browse.setPreferredSize(new Dimension(110, 38));
        browse.addActionListener(e -> browse());
        card.add(browse, c);
        c.gridx++;
        Ui.McButton apply = new Ui.McButton("Применить", Ui.McButton.Kind.GREEN).size(13f);
        apply.setPreferredSize(new Dimension(110, 38));
        apply.addActionListener(e -> apply());
        card.add(apply, c);
        c.gridx++;
        c.insets = new Insets(6, 0, 0, 0);
        Ui.McButton open = new Ui.McButton("Открыть", Ui.McButton.Kind.DARK).size(13f);
        open.setPreferredSize(new Dimension(100, 38));
        open.addActionListener(e -> Mc.openFolder(Mc.dir));
        card.add(open, c);
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 4;
        c.insets = new Insets(14, 0, 0, 0);
        Ui.Note info = new Ui.Note(640, 3, 12.5f, Ui.MUTED);
        info.setText("Light Launcher 1.1 (эксперимент) · Java лаунчера: " + System.getProperty("java.version") + "\nДанные модов берутся с Modrinth (modrinth.com). По умолчанию лаунчер работает в своей папке и не трогает оригинальный .minecraft.");
        card.add(info, c);
        JPanel north = new JPanel();
        north.setOpaque(false);
        north.setLayout(new javax.swing.BoxLayout(north, javax.swing.BoxLayout.Y_AXIS));
        north.add(card);
        north.add(javax.swing.Box.createVerticalStrut(12));
        north.add(buildLangCard());
        north.add(javax.swing.Box.createVerticalStrut(12));
        north.add(buildSupportCard());
        north.add(javax.swing.Box.createVerticalStrut(12));
        north.add(buildLaunchCard());

        JPanel logCard = new JPanel(new BorderLayout(0, 8));
        logCard.setOpaque(false);
        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(Ui.label("Журнал", 16f, Color.WHITE, true), BorderLayout.WEST);
        JPanel hb = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        hb.setOpaque(false);
        Ui.McButton copy = new Ui.McButton("Копировать", Ui.McButton.Kind.DARK).size(12.5f);
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(log.getText()), null));
        hb.add(copy);
        head.add(hb, BorderLayout.EAST);
        logCard.add(head, BorderLayout.NORTH);
        log.setEditable(false);
        log.setBackground(Ui.FIELD);
        log.setForeground(new Color(0xCFCFD3));
        log.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12));
        log.setBorder(new EmptyBorder(8, 10, 8, 10));
        log.setLineWrap(true);
        JScrollPane sp = new JScrollPane(log);
        Ui.styleScroll(sp);
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(Ui.FIELD);
        sp.setBorder(new LineBorder(Ui.BORDER));
        logCard.add(sp, BorderLayout.CENTER);
        logCard.setPreferredSize(new Dimension(100, 300));
        logCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 300));
        north.add(javax.swing.Box.createVerticalStrut(14));
        north.add(logCard);
        JScrollPane page = new JScrollPane(new ScrollBody(north), JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Ui.styleScroll(page);
        page.setBorder(null);
        page.setOpaque(false);
        page.getViewport().setOpaque(false);
        page.getVerticalScrollBar().setUnitIncrement(24);
        add(page, BorderLayout.CENTER);

        for (String l : Log.history()) log.append(l + "\n");
        Log.setSink(line -> SwingUtilities.invokeLater(() -> {
            log.append(line + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        }));
    }

    private JPanel buildSupportCard() {
        JPanel card = new JPanel(new BorderLayout(14, 0)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.card(g, 0, 0, getWidth(), getHeight(), Ui.CARD, Ui.BORDER);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 20, 14, 20));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        JPanel t = new JPanel(new java.awt.GridLayout(2, 1));
        t.setOpaque(false);
        t.add(Ui.label("Поддержать разработчика", 14.5f, Color.WHITE, true));
        t.add(Ui.label("Light Launcher бесплатный. Если он вам нравится — можно поддержать автора.", 12.5f, Ui.MUTED, false));
        card.add(t, BorderLayout.CENTER);
        Ui.McButton b = new Ui.McButton("Поддержать", Ui.McButton.Kind.GREEN).size(13f);
        b.setPreferredSize(new Dimension(170, 38));
        b.addActionListener(e -> {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create("https://dalink.to/lightlauncher"));
            } catch (Exception ex) {
                Dlg.message(this, "Не удалось открыть браузер", "Откройте ссылку вручную: https://dalink.to/lightlauncher");
            }
        });
        card.add(b, BorderLayout.EAST);
        return card;
    }

    private JPanel buildLangCard() {
        JPanel card = new JPanel(new BorderLayout(14, 0)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.card(g, 0, 0, getWidth(), getHeight(), Ui.CARD, Ui.BORDER);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(14, 20, 14, 20));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 76));
        javax.swing.JComponent flag = new javax.swing.JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                LangPicker.paintFlag(g, I18n.lang(), 0, 0, 48, 32);
                g.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(48, 32);
            }
        };
        card.add(flag, BorderLayout.WEST);
        JPanel t = new JPanel(new java.awt.GridLayout(2, 1));
        t.setOpaque(false);
        t.add(Ui.label("Язык интерфейса", 14.5f, Color.WHITE, true));
        t.add(Ui.label(I18n.isEn() ? "English" : "Русский", 12.5f, Ui.MUTED, false));
        card.add(t, BorderLayout.CENTER);
        Ui.McButton pick = new Ui.McButton("Выбрать язык…", Ui.McButton.Kind.DARK).size(13f);
        pick.setPreferredSize(new Dimension(170, 38));
        pick.addActionListener(e -> {
            String l = LangPicker.choose(this, I18n.lang(), false);
            if (l != null && !l.equals(I18n.lang())) {
                LangPicker.apply(l);
                App.restartUi();
            }
        });
        card.add(pick, BorderLayout.EAST);
        return card;
    }

    /** Контейнер, который тянется по ширине окна и прокручивается по высоте. */
    private static final class ScrollBody extends JPanel implements javax.swing.Scrollable {
        ScrollBody(JPanel inner) {
            super(new BorderLayout());
            setOpaque(false);
            add(inner, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) {
            return 24;
        }

        @Override
        public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) {
            return 160;
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

    private final Ui.Field javaField = new Ui.Field("Автоматически (скачается нужная версия)");
    private final Ui.Field ramField = new Ui.Field("3072");
    private final Ui.Field argsField = new Ui.Field("Доп. аргументы JVM, например -XX:+UseG1GC");
    private final Ui.Field cidField = new Ui.Field("Azure Client ID (для входа Microsoft)");

    private JPanel buildLaunchCard() {
        JPanel card = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.card(g, 0, 0, getWidth(), getHeight(), Ui.CARD, Ui.BORDER);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(18, 20, 18, 20));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 1;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        card.add(Ui.label("Запуск игры", 16f, Color.WHITE, true), c);
        c.gridy++;
        c.insets = new Insets(10, 0, 0, 0);
        card.add(Ui.label("Путь к Java (пусто — автоматически)", 13f, Ui.MUTED, false), c);
        c.gridy++;
        c.gridwidth = 1;
        c.insets = new Insets(6, 0, 0, 8);
        javaField.setText(Mc.javaPath == null ? "" : Mc.javaPath);
        card.add(javaField, c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(6, 0, 0, 0);
        Ui.McButton jb = new Ui.McButton("Выбрать…", Ui.McButton.Kind.DARK).size(13f);
        jb.setPreferredSize(new Dimension(110, 38));
        jb.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) javaField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        card.add(jb, c);
        c.gridx = 0;
        c.gridy++;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new Insets(12, 0, 0, 0);
        card.add(Ui.label("Память для игры", 13f, Ui.MUTED, false), c);
        c.gridy++;
        c.insets = new Insets(6, 0, 0, 0);
        ramField.setText(String.valueOf(Mc.ramMb));
        RamSlider rs = new RamSlider(Mc.ramMb, v -> ramField.setText(String.valueOf(v)));
        card.add(rs, c);
        c.gridy++;
        c.insets = new Insets(12, 0, 0, 0);
        card.add(Ui.label("Дополнительные аргументы JVM", 13f, Ui.MUTED, false), c);
        c.gridy++;
        c.insets = new Insets(6, 0, 0, 0);
        argsField.setText(Mc.jvmArgs == null ? "" : Mc.jvmArgs);
        card.add(argsField, c);
        c.gridy++;
        c.insets = new Insets(16, 0, 0, 0);
        card.add(Ui.label("Вход Microsoft", 14.5f, Color.WHITE, true), c);
        c.gridy++;
        c.insets = new Insets(6, 0, 0, 0);
        Ui.Note n = new Ui.Note(640, 4, 12.5f, Ui.MUTED);
        n.setText("Нужен бесплатный Azure Client ID: portal.azure.com → Microsoft Entra ID → Регистрация приложений → Создать (тип «Личные учётные записи Microsoft»), затем Проверка подлинности → «Разрешить общедоступные потоки клиента» = Да. Скопируйте «Идентификатор приложения (клиент)» сюда. Без него доступен офлайн-ник.");
        card.add(n, c);
        c.gridy++;
        c.insets = new Insets(8, 0, 0, 0);
        cidField.setText(Mc.msClientId == null ? "" : Mc.msClientId);
        card.add(cidField, c);
        c.gridy++;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(14, 0, 0, 0);
        Ui.McButton save = new Ui.McButton("Сохранить", Ui.McButton.Kind.GREEN).size(13f);
        save.setPreferredSize(new Dimension(130, 38));
        save.addActionListener(e -> {
            int ram = 3072;
            try {
                ram = Integer.parseInt(ramField.getText().trim());
            } catch (NumberFormatException ignored) {
                // оставим по умолчанию
            }
            Mc.ramMb = Math.max(512, Math.min(65536, ram));
            ramField.setText(String.valueOf(Mc.ramMb));
            Mc.javaPath = javaField.getText().trim();
            Mc.jvmArgs = argsField.getText().trim();
            Mc.msClientId = cidField.getText().trim();
            Mc.saveConfig();
            Tasks.bar.info("Настройки запуска сохранены");
        });
        card.add(save, c);
        return card;
    }

    private void browse() {
        JFileChooser fc = new JFileChooser(Mc.dir.toFile());
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) dirField.setText(fc.getSelectedFile().getAbsolutePath());
    }

    private void apply() {
        String t = dirField.getText().trim();
        if (t.isEmpty()) return;
        Path p = new File(t).toPath();
        Mc.dir = p;
        Mc.customDir = true;
        Mc.selectedId = null;
        Mc.saveConfig();
        State.refresh();
        Tasks.bar.info("Папка Minecraft: " + p);
    }
}
