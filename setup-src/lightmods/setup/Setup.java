package lightmods.setup;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import lightmods.I18n;
import lightmods.Ui;

/** Установщик Light Launcher: копирует программу, создаёт ярлыки и деинсталлятор. */
public final class Setup {
    private static final String NAME = "Light Launcher";
    private static final String OS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean WIN = OS.contains("win");
    private static final boolean MAC = OS.contains("mac");

    private final JFrame frame = new JFrame();
    private final Ui.Field dirField = new Ui.Field("Папка установки");
    private final JCheckBox desktop = Ui.check("Ярлык на рабочем столе", true);
    private final JCheckBox menu = Ui.check(WIN ? "Ярлык в меню «Пуск»" : "Ярлык в меню приложений", true);
    private final JCheckBox launch = Ui.check("Запустить Light Launcher после установки", true);
    /** Папка уже установленного лаунчера (тогда вместо установки предлагается обновление) или null. */
    private final Path existing = findInstalled();
    private final boolean update = existing != null;
    private final Ui.McButton go = new Ui.McButton(existing != null ? "ОБНОВИТЬ" : "УСТАНОВИТЬ", Ui.McButton.Kind.GREEN).size(16f);
    private final JProgressBar bar = Ui.progressBar();
    private final JLabel status = Ui.label(" ", 13f, Ui.MUTED, false);
    private Path installed;

    static Path defaultDir() {
        String home = System.getProperty("user.home");
        if (WIN) {
            String la = System.getenv("LOCALAPPDATA");
            return Paths.get(la != null ? la : home + "\\AppData\\Local", "Programs", NAME);
        }
        if (MAC) return Paths.get(home, "Applications", NAME);
        return Paths.get(home, ".local", "share", "light-launcher");
    }

    /** Ищет уже установленный Light Launcher: по записи в реестре (Windows) и в папке по умолчанию. */
    static Path findInstalled() {
        List<Path> cands = new ArrayList<>();
        if (WIN) {
            try {
                Process p = new ProcessBuilder("reg", "query", "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\LightLauncher", "/v", "InstallLocation")
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                p.waitFor();
                for (String line : out.split("\\R")) {
                    int i = line.indexOf("REG_SZ");
                    if (i >= 0) cands.add(Paths.get(line.substring(i + 6).trim()));
                }
            } catch (Exception ignored) {
                // записи нет
            }
        }
        cands.add(defaultDir());
        for (Path d : cands) {
            try {
                if (Files.isRegularFile(d.resolve("LightLauncher.jar")) || Files.isRegularFile(d.resolve("LightLauncher.exe"))) return d;
            } catch (RuntimeException ignored) {
                // некорректный путь
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        for (String a : args) {
            if (a.equals("--silent") || a.startsWith("--dir=")) {
                Path d = defaultDir();
                for (String b : args) if (b.startsWith("--dir=")) d = Paths.get(b.substring(6));
                Path old = findInstalled();
                if (old != null && Arrays.stream(args).noneMatch(x -> x.startsWith("--dir="))) d = old;
                install(d, true, true, msg -> System.out.println(msg));
                return;
            }
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // обычный вид
            }
            lightmods.Mc.loadConfig();
            I18n.init(lightmods.Mc.lang);
            new Setup().show();
        });
    }

    private void show() {
        lightmods.Mc.loadConfig();
        lightmods.I18n.init(lightmods.Mc.lang);
        frame.setTitle(I18n.t(update ? "Обновление " : "Установка ") + NAME);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImages(Ui.iconImages());
        JPanel root = new JPanel(new BorderLayout(26, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                g.setColor(Ui.BG);
                g.fillRect(0, 0, getWidth(), getHeight());
            }
        };
        root.setBorder(new EmptyBorder(28, 30, 24, 30));

        JPanel side = new JPanel() {
            @Override
            protected void paintComponent(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Ui.paintAvatar(g, 0, 0, 132);
                g.dispose();
            }
        };
        side.setOpaque(false);
        side.setPreferredSize(new Dimension(132, 132));
        JPanel sideWrap = new JPanel(new BorderLayout());
        sideWrap.setOpaque(false);
        sideWrap.add(side, BorderLayout.NORTH);
        root.add(sideWrap, BorderLayout.WEST);

        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.WEST;
        form.add(Ui.label((update ? "Обновление " : "Установка ") + NAME, 24f, Color.WHITE, true), c);
        c.gridy++;
        c.insets = new Insets(4, 0, 18, 0);
        Ui.Note sub = new Ui.Note(520, 3, 13.5f, Ui.MUTED);
        sub.setText(update ? "Найдена установка: " + existing + ". Её файлы будут заменены новой версией; ваши установки, миры, скины и настройки не затрагиваются."
                : "Лаунчер Minecraft: запуск игры, моды, сборки и текстуры из Modrinth");
        form.add(sub, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 4, 0);
        form.add(Ui.label("Папка установки", 13f, Ui.MUTED, false), c);
        c.gridy++;
        c.gridwidth = 1;
        c.insets = new Insets(0, 0, 14, 8);
        dirField.setText((update ? existing : defaultDir()).toString());
        dirField.setEnabled(!update);
        form.add(dirField, c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 14, 0);
        Ui.McButton browse = new Ui.McButton("Обзор…", Ui.McButton.Kind.DARK).size(13f);
        browse.setPreferredSize(new Dimension(100, 38));
        browse.setEnabled(!update);
        browse.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) dirField.setText(fc.getSelectedFile().toPath().resolve(NAME).toString());
        });
        form.add(browse, c);
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 6, 0);
        for (JCheckBox cb : new JCheckBox[]{desktop, menu, launch}) {
            c.gridy++;
            form.add(cb, c);
        }
        c.gridy++;
        c.insets = new Insets(14, 0, 8, 0);
        c.fill = GridBagConstraints.NONE;
        go.setPreferredSize(new Dimension(220, 48));
        go.addActionListener(e -> start());
        form.add(go, c);
        c.gridy++;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 6, 0);
        bar.setPreferredSize(new Dimension(300, 8));
        bar.setVisible(false);
        form.add(bar, c);
        c.gridy++;
        c.insets = new Insets(0, 0, 0, 0);
        form.add(status, c);
        root.add(form, BorderLayout.CENTER);

        frame.setContentPane(root);
        frame.pack();
        frame.setMinimumSize(new Dimension(760, 640));
        frame.setSize(760, 640);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        lightmods.Mc.loadConfig();
        lightmods.Agreement.show(frame, () -> {
        }, () -> System.exit(0));
    }

    private void start() {
        go.setEnabled(false);
        bar.setVisible(true);
        bar.setIndeterminate(true);
        Path dir = Paths.get(dirField.getText().trim());
        boolean d = desktop.isSelected();
        boolean m = menu.isSelected();
        boolean l = launch.isSelected();
        Thread t = new Thread(() -> {
            try {
                Path jar = install(dir, d, m, msg -> SwingUtilities.invokeLater(() -> status.setText(msg)));
                installed = jar;
                SwingUtilities.invokeLater(() -> {
                    bar.setIndeterminate(false);
                    bar.setValue(1000);
                    status.setForeground(new Color(0x7ED957));
                    status.setText((update ? "Готово! Light Launcher обновлён в " : "Готово! Light Launcher установлен в ") + dir);
                    go.setText("ГОТОВО");
                    go.setEnabled(true);
                    go.addActionListener(e -> System.exit(0));
                    if (l) {
                        runApp(jar);
                        new javax.swing.Timer(1500, e -> System.exit(0)).start();
                    }
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    bar.setVisible(false);
                    status.setForeground(new Color(0xFF7B72));
                    status.setText("Ошибка: " + (ex.getMessage() == null ? ex.toString() : ex.getMessage()));
                    go.setEnabled(true);
                });
            }
        }, "setup");
        t.start();
    }

    static void runApp(Path jar) {
        try {
            String javaw = Paths.get(System.getProperty("java.home"), "bin", WIN ? "javaw.exe" : "java").toString();
            new ProcessBuilder(javaw, "-jar", jar.toString()).directory(jar.getParent().toFile()).start();
        } catch (IOException ignored) {
            // пользователь запустит вручную
        }
    }

    interface Say {
        void say(String s);
    }

    static Path install(Path dir, boolean desktopLink, boolean menuLink, Say say) throws Exception {
        say.say("Копирую файлы…");
        try {
            Files.createDirectories(dir);
            Path probe = dir.resolve(".write-test");
            Files.writeString(probe, "x");
            Files.deleteIfExists(probe);
        } catch (IOException e) {
            throw new IOException("Нет прав на запись в папку «" + dir + "». Лаунчеру не нужны права администратора — выберите папку в вашем профиле пользователя (например, по умолчанию).");
        }
        Path jar = dir.resolve("LightLauncher.jar");
        try (InputStream in = Setup.class.getResourceAsStream("/app/LightLauncher.jar")) {
            if (in == null) throw new IOException("Внутри установщика нет LightLauncher.jar");
            replaceFile(jar, in);
        }
        Path exe = null;
        if (WIN) {
            try (InputStream in = Setup.class.getResourceAsStream("/app/LightLauncher.exe")) {
                if (in != null) {
                    exe = dir.resolve("LightLauncher.exe");
                    replaceFile(exe, in);
                }
            }
        }
        BufferedImage big = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = big.createGraphics();
        g.drawImage(Ui.logoScaled(256), 0, 0, null);
        g.dispose();
        ByteArrayOutputStream png = new ByteArrayOutputStream();
        ImageIO.write(big, "png", png);
        Files.write(dir.resolve("LightLauncher.png"), png.toByteArray());
        if (WIN) writeIco(dir.resolve("LightLauncher.ico"), png.toByteArray());

        say.say("Создаю ярлыки…");
        if (WIN) installWindows(dir, jar, exe, desktopLink, menuLink);
        else if (MAC) installMac(dir, jar);
        else installLinux(dir, jar, desktopLink, menuLink);
        say.say("Готово");
        return jar;
    }

    /**
     * Заменяет файл. В Windows запущенный exe/jar нельзя перезаписать, но можно переименовать — старый уходит в .old
     * (удаляется при следующем обновлении). Если и это не вышло, просим закрыть лаунчер.
     */
    static void replaceFile(Path target, InputStream in) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".new");
        Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (IOException first) {
            Path old = target.resolveSibling(target.getFileName() + ".old");
            try {
                Files.deleteIfExists(old);
                Files.move(target, old, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException second) {
                Files.deleteIfExists(tmp);
                throw new IOException("Не удалось заменить «" + target.getFileName() + "». Закройте Light Launcher и запустите установщик снова.");
            }
        }
    }

    /** ICO с PNG внутри (поддерживается Windows Vista и новее). */
    static void writeIco(Path file, byte[] png) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        short[] head = {0, 1, 1};
        for (short s : head) {
            o.write(s & 0xFF);
            o.write(s >> 8 & 0xFF);
        }
        o.write(0); // 256 px
        o.write(0);
        o.write(0);
        o.write(0);
        o.write(1);
        o.write(0);
        o.write(32);
        o.write(0);
        int len = png.length;
        for (int i = 0; i < 4; i++) o.write(len >> (8 * i) & 0xFF);
        int off = 22;
        for (int i = 0; i < 4; i++) o.write(off >> (8 * i) & 0xFF);
        o.write(png);
        Files.write(file, o.toByteArray());
    }

    private static void installWindows(Path dir, Path jar, Path exe, boolean desktopLink, boolean menuLink) throws Exception {
        String javaw = Paths.get(System.getProperty("java.home"), "bin", "javaw.exe").toString();
        String ps = "$ws = New-Object -ComObject WScript.Shell; "
                + "$d = switch ($env:LM_KIND) { 'desktop' { [Environment]::GetFolderPath('Desktop') } default { Join-Path $env:APPDATA 'Microsoft\\Windows\\Start Menu\\Programs' } }; "
                + "$s = $ws.CreateShortcut((Join-Path $d 'Light Launcher.lnk')); "
                + "$s.TargetPath = $env:LM_TARGET; $s.Arguments = $env:LM_ARGS; $s.WorkingDirectory = $env:LM_DIR; "
                + "$s.IconLocation = $env:LM_ICON; $s.Description = 'Light Launcher'; $s.Save()";
        List<String> kinds = new ArrayList<>();
        if (desktopLink) kinds.add("desktop");
        if (menuLink) kinds.add("menu");
        for (String kind : kinds) {
            ProcessBuilder pb = new ProcessBuilder("powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", ps);
            pb.environment().put("LM_KIND", kind);
            pb.environment().put("LM_TARGET", exe != null ? exe.toString() : javaw);
            pb.environment().put("LM_ARGS", exe != null ? "" : "-jar \"" + jar + "\"");
            pb.environment().put("LM_DIR", dir.toString());
            pb.environment().put("LM_ICON", dir.resolve("LightLauncher.ico").toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (p.waitFor() != 0) throw new IOException("Не удалось создать ярлык: " + out.trim());
        }
        String uninstall = "@echo off\r\n"
                + "chcp 65001 >nul\r\n"
                + "powershell -NoProfile -Command \"Remove-Item ([Environment]::GetFolderPath('Desktop') + '\\Light Launcher.lnk') -ErrorAction SilentlyContinue; "
                + "Remove-Item (Join-Path $env:APPDATA 'Microsoft\\Windows\\Start Menu\\Programs\\Light Launcher.lnk') -ErrorAction SilentlyContinue; "
                + "Remove-Item (Join-Path $env:APPDATA 'Microsoft\\Internet Explorer\\Quick Launch\\User Pinned\\TaskBar\\Light Launcher.lnk') -ErrorAction SilentlyContinue\"\r\n"
                + "reg delete \"HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\LightLauncher\" /f >nul 2>&1\r\n"
                + "start \"\" /min cmd /c \"timeout /t 2 /nobreak >nul & rmdir /s /q \"%~dp0.\"\"\r\n";
        Files.writeString(dir.resolve("uninstall.cmd"), uninstall, StandardCharsets.UTF_8);
        String key = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\LightLauncher";
        String[][] vals = {
                {"DisplayName", "Light Launcher"}, {"DisplayVersion", "1.2 beta"}, {"Publisher", "Light Launcher"},
                {"InstallLocation", dir.toString()}, {"DisplayIcon", dir.resolve("LightLauncher.ico").toString()},
                {"UninstallString", "\"" + dir.resolve("uninstall.cmd") + "\""}};
        for (String[] v : vals) {
            new ProcessBuilder("reg", "add", key, "/v", v[0], "/d", v[1], "/f").redirectErrorStream(true).start().waitFor();
        }
    }

    private static void installLinux(Path dir, Path jar, boolean desktopLink, boolean menuLink) throws IOException {
        String home = System.getProperty("user.home");
        String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        String entry = "[Desktop Entry]\nType=Application\nName=Light Launcher\nComment=Лаунчер Minecraft с модами, сборками и текстурами\n"
                + "Exec=\"" + java + "\" -jar \"" + jar + "\"\nIcon=" + dir.resolve("LightLauncher.png") + "\nTerminal=false\nCategories=Game;\n";
        if (menuLink) {
            Path apps = Paths.get(home, ".local", "share", "applications");
            Files.createDirectories(apps);
            Files.writeString(apps.resolve("light-launcher.desktop"), entry);
        }
        if (desktopLink) {
            Path dsk = Paths.get(home, "Desktop");
            if (Files.isDirectory(dsk)) {
                Path f = dsk.resolve("Light Launcher.desktop");
                Files.writeString(f, entry);
                f.toFile().setExecutable(true);
            }
        }
        Path un = dir.resolve("uninstall.sh");
        Files.writeString(un, "#!/bin/sh\nrm -f \"" + home + "/.local/share/applications/light-launcher.desktop\" \"" + home + "/Desktop/Light Launcher.desktop\"\nrm -rf \"" + dir + "\"\n");
        un.toFile().setExecutable(true);
    }

    private static void installMac(Path dir, Path jar) throws IOException {
        Path cmd = dir.resolve("Light Launcher.command");
        Files.writeString(cmd, "#!/bin/sh\ncd \"$(dirname \"$0\")\"\nexec \"" + Paths.get(System.getProperty("java.home"), "bin", "java") + "\" -jar \"" + jar + "\"\n");
        cmd.toFile().setExecutable(true);
    }
}
