package lightmods;

import java.awt.AlphaComposite;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

/**
 * Пользовательское соглашение: затемняет окно и «всплывает» карточкой поверх него (через glass pane).
 * Кнопка «Принять» становится активной только после установки галочки.
 */
public final class Agreement extends JComponent {
    public static final String VERSION = "4";

    static final String TEXT =
            "1. О программе\n"
            + "Light Launcher — неофициальный бесплатный лаунчер Minecraft: Java Edition. Он устанавливает версии игры, моды, сборки, текстуры и шейдеры "
            + "и запускает игру. Программа не связана с Mojang, Microsoft, Modrinth, Fabric, Forge, NeoForge и Quilt и не одобрена ими. "
            + "Minecraft является собственностью Mojang AB / Microsoft.\n\n"
            + "2. Отказ от ответственности\n"
            + "Программа предоставляется «как есть», без каких-либо гарантий. Авторы Light Launcher не несут никакой ответственности "
            + "за любой прямой или косвенный вред, в том числе за вред вашему устройству, потерю или повреждение файлов, миров, "
            + "сохранений и настроек, сбои в работе системы или игры, блокировку аккаунта или доступа к серверам, а также за любые другие убытки, "
            + "возникшие в результате использования или невозможности использования программы.\n\n"
            + "3. Сторонний контент\n"
            + "Моды, сборки, текстуры, шейдеры и установщики загрузчиков создаются третьими лицами и скачиваются с их сайтов. "
            + "Мы не проверяем их безопасность, работоспособность и законность. Вы устанавливаете их на свой страх и риск и сами "
            + "соблюдаете лицензии авторов и правила игровых серверов.\n\n"
            + "4. Что делает программа\n"
            + "Она скачивает файлы игры, библиотеки, ресурсы и среду Java с серверов Mojang, Modrinth и разработчиков загрузчиков, создаёт папки установок, "
            + "запускает установщики Forge/NeoForge и запускает игру. Все файлы хранятся в собственной папке .lightlauncher; "
            + "оригинальный каталог .minecraft лаунчер не изменяет, пока вы сами не выберете его в Настройках (тогда перед первым изменением launcher_profiles.json сохраняется резервная копия). Рекомендуем делать резервные копии своих миров.\n\n"
            + "5. Аккаунты\n"
            + "Вход через Microsoft выполняется на сайте Microsoft; пароль лаунчеру не передаётся. Для повторного входа токены хранятся на вашем компьютере "
            + "в файле accounts.json в папке .lightmods — никому его не передавайте. Вход через Ely.by отправляет логин и пароль только на authserver.ely.by. Офлайн-режим не требует аккаунта, но не даёт доступа к серверам "
            + "с проверкой лицензии. Игру следует приобретать у Mojang; лаунчер не предназначен для обхода проверки лицензии.\n\n"
            + "7. Изменение соглашения\n"
            + "Мы вправе изменять это соглашение в любое время — так же, как развивается сам лаунчер. Новая редакция вступает в силу с момента выхода "
            + "версии программы, в которой она появилась. Если текст изменился, лаунчер один раз покажет его снова и попросит подтвердить; "
            + "пока вы его не примете, пользоваться программой нельзя. Продолжая пользоваться лаунчером, вы соглашаетесь с актуальной редакцией.\n\n"
            + "8. Принятие условий\n"
            + "Нажимая «Принять», вы подтверждаете, что прочитали это соглашение, согласны с ним и используете программу добровольно, "
            + "на собственную ответственность. Если вы не согласны — нажмите «Отказаться» и не используйте программу.";

    static final String TEXT_EN =
            "1. About the program\n"
            + "Light Launcher is an unofficial free launcher for Minecraft: Java Edition. It installs game versions, mods, modpacks, textures and shaders "
            + "and starts the game. The program is not affiliated with, or endorsed by, Mojang, Microsoft, Modrinth, Fabric, Forge, NeoForge or Quilt. "
            + "Minecraft is the property of Mojang AB / Microsoft.\n\n"
            + "2. Disclaimer\n"
            + "The program is provided \"as is\", without warranty of any kind. The authors of Light Launcher accept no responsibility "
            + "for any direct or indirect damage, including damage to your device, loss or corruption of files, worlds, "
            + "saves and settings, system or game failures, suspension of an account or of access to servers, or any other loss "
            + "resulting from the use of, or inability to use, the program.\n\n"
            + "3. Third-party content\n"
            + "Mods, modpacks, textures, shaders and loader installers are created by third parties and downloaded from their sites. "
            + "We do not verify their safety, functionality or legality. You install them at your own risk and are yourself responsible "
            + "for complying with the authors' licenses and server rules.\n\n"
            + "4. What the program does\n"
            + "It downloads game files, libraries, assets and the Java runtime from the servers of Mojang, Modrinth and loader developers, creates instance folders, "
            + "runs the Forge/NeoForge installers and starts the game. All files are kept in its own .lightlauncher folder; "
            + "the original .minecraft directory is not modified by the launcher unless you choose it in Settings (in that case a backup of launcher_profiles.json is made before the first change). We recommend backing up your worlds.\n\n"
            + "5. Accounts\n"
            + "Microsoft sign-in is performed on the Microsoft website; the launcher never receives your password. For repeat sign-in the tokens are stored on your computer "
            + "in accounts.json in the .lightmods folder — do not share this file. Ely.by sign-in sends your login and password only to authserver.ely.by. "
            + "Offline mode needs no account but does not give access to servers that verify licenses. "
            + "The game should be purchased from Mojang; the launcher is not intended to bypass license checks.\n\n"
            + "7. Changes to this agreement\n"
            + "We may change this agreement at any time — just as the launcher itself keeps evolving. A new edition takes effect with the release "
            + "of the program version that contains it. If the text changes, the launcher will show it once more and ask you to confirm it; "
            + "until you accept it, the program cannot be used. By continuing to use the launcher you agree to the current edition.\n\n"
            + "8. Acceptance\n"
            + "By pressing \"Accept\" you confirm that you have read this agreement, agree to it and use the program voluntarily "
            + "and at your own risk. If you do not agree, press \"Decline\" and do not use the program.";

    private float alpha;
    private final JPanel card;
    private final Runnable onAccept;
    private final Runnable onDecline;
    private final JFrame frame;
    private final Component oldGlass;

    private Agreement(JFrame frame, Runnable onAccept, Runnable onDecline) {
        this.frame = frame;
        this.onAccept = onAccept;
        this.onDecline = onDecline;
        this.oldGlass = frame.getGlassPane();
        setLayout(null);
        setOpaque(false);
        addMouseListener(new MouseAdapter() {
        });
        addMouseMotionListener(new MouseAdapter() {
        });
        addMouseWheelListener(e -> {
        });

        card = new JPanel(new BorderLayout(0, 14)) {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                g.setColor(new Color(0, 0, 0, 90));
                g.fillRoundRect(3, 6, getWidth() - 6, getHeight() - 6, 22, 22);
                Ui.card(g, 0, 0, getWidth() - 1, getHeight() - 4, Ui.BG, Ui.BORDER);
                g.setColor(Ui.GREEN_HI);
                g.fillRoundRect(0, 0, getWidth() - 1, 5, 5, 5);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(26, 28, 22, 28));

        JPanel head = new JPanel(new BorderLayout(16, 0)) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(10, 64);
            }
        };
        head.setOpaque(false);
        JComponent av = new JComponent() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.paintAvatar(g, 0, 4, 56);
                g.dispose();
            }

            @Override
            public Dimension getPreferredSize() {
                return new Dimension(56, 64);
            }
        };
        head.add(av, BorderLayout.WEST);
        JPanel titles = new JPanel(new BorderLayout());
        titles.setOpaque(false);
        titles.add(Ui.label("Пользовательское соглашение", 22f, Color.WHITE, true), BorderLayout.CENTER);
        titles.add(Ui.label("Light Launcher · прочитайте перед началом работы", 13f, Ui.MUTED, false), BorderLayout.SOUTH);
        head.add(titles, BorderLayout.CENTER);
        card.add(head, BorderLayout.NORTH);

        JTextArea ta = new JTextArea(I18n.isEn() ? TEXT_EN : TEXT);
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setOpaque(true);
        ta.setBackground(Ui.FIELD);
        ta.setForeground(new Color(0xDADADD));
        ta.setFont(Ui.font(13.5f));
        ta.setBorder(new EmptyBorder(14, 16, 14, 16));
        ta.setCaretPosition(0);
        JScrollPane sp = new JScrollPane(ta, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        Ui.styleScroll(sp);
        sp.getViewport().setOpaque(true);
        sp.getViewport().setBackground(Ui.FIELD);
        sp.setBorder(new javax.swing.border.LineBorder(Ui.BORDER));
        card.add(sp, BorderLayout.CENTER);

        JPanel foot = new JPanel(new BorderLayout(0, 14));
        foot.setOpaque(false);
        JCheckBox cb = Ui.check("Я прочитал(а) соглашение и принимаю его условия", false);
        foot.add(cb, BorderLayout.NORTH);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btns.setOpaque(false);
        Ui.McButton decline = new Ui.McButton("Отказаться", Ui.McButton.Kind.DARK).size(14f);
        Ui.McButton accept = new Ui.McButton("Принять", Ui.McButton.Kind.GREEN).size(14f);
        accept.setEnabled(false);
        decline.setPreferredSize(new Dimension(150, 42));
        accept.setPreferredSize(new Dimension(170, 42));
        cb.addActionListener(e -> accept.setEnabled(cb.isSelected()));
        decline.addActionListener(e -> close(false));
        accept.addActionListener(e -> close(true));
        btns.add(decline);
        btns.add(accept);
        foot.add(btns, BorderLayout.SOUTH);
        card.add(foot, BorderLayout.SOUTH);
        card.setVisible(false);
        add(card);
    }

    @Override
    public void doLayout() {
        int w = Math.min(680, getWidth() - 50);
        int h = Math.min(560, getHeight() - 50);
        card.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2, Math.max(300, w), Math.max(300, h));
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setComposite(AlphaComposite.SrcOver.derive(Math.min(1f, alpha)));
        g.setPaint(new java.awt.GradientPaint(0, 0, new Color(8, 14, 28, 235), 0, getHeight(), new Color(0, 0, 0, 245)));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.dispose();
    }

    private void start() {
        frame.setGlassPane(this);
        setVisible(true);
        revalidate();
        Timer t = new Timer(16, null);
        t.addActionListener(e -> {
            alpha += 0.08f;
            if (alpha >= 1f) {
                alpha = 1f;
                t.stop();
                card.setVisible(true);
                card.requestFocusInWindow();
            }
            repaint();
        });
        t.start();
    }

    private void close(boolean ok) {
        setVisible(false);
        frame.setGlassPane(oldGlass);
        if (ok) onAccept.run();
        else onDecline.run();
    }

    /** Показывает соглашение поверх окна. Если оно уже принято — сразу вызывает onAccept. */
    public static void show(JFrame frame, Runnable onAccept, Runnable onDecline) {
        if (VERSION.equals(Mc.agreed)) {
            onAccept.run();
            return;
        }
        new Agreement(frame, () -> {
            Mc.agreed = VERSION;
            Mc.saveConfig();
            onAccept.run();
        }, onDecline).start();
    }
}
