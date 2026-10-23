import lightmods.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;

public class GuiFlowTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  OK   " : "  FAIL ") + what); if (!ok) fails++; }

    static void collect(Component c, Class<?> cls, List<Component> out) {
        if (cls.isInstance(c)) out.add(c);
        if (c instanceof Container) for (Component k : ((Container) c).getComponents()) collect(k, cls, out);
    }
    static Ui.McButton findBtn(Component root, String text) {
        List<Component> l = new ArrayList<>();
        collect(root, Ui.McButton.class, l);
        for (Component c : l) if (((Ui.McButton) c).getText().equals(text) && c.isShowing()) return (Ui.McButton) c;
        return null;
    }
    static Window visibleDialog() {
        for (Window w : Window.getWindows()) if (w instanceof JDialog && w.isShowing()) return w;
        return null;
    }
    static Window waitDialog(int ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        while (System.currentTimeMillis() < end) { Window w = visibleDialog(); if (w != null) return w; Thread.sleep(100); }
        return null;
    }
    static void clickInDialog(Window d, String text) throws Exception {
        Ui.McButton b = findBtn(d, text);
        if (b == null) throw new RuntimeException("no button " + text);
        SwingUtilities.invokeAndWait(b::doClick);
    }
    static void shot(Window w, Path p) throws Exception {
        Rectangle r = w.getBounds();
        BufferedImage im = new Robot().createScreenCapture(r);
        ImageIO.write(im, "png", p.toFile());
    }

    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcgui");
        Mc.dir = tmp.resolve(".minecraft");
        Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{},\"settings\":{},\"version\":3}");
        Mc.installVanilla("1.21.1", Progress.NONE);
        String fid = Mc.installFabricLike(false, "1.21.1", "0.16.9", Progress.NONE);
        Mc.Profile inst = Mc.createInstance("Fabric Test", fid, false, "Glowstone");
        Mc.selectedId = inst.id;
        Path out = Paths.get(a[0]); Files.createDirectories(out);

        final MainFrame[] fr = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            fr[0] = new MainFrame(); fr[0].setLocation(0, 0); fr[0].setSize(1180, 760); fr[0].setVisible(true);
            fr[0].show("mods");
        });
        Thread.sleep(2000);

        System.out.println("== install mod via GUI");
        Ui.McButton inst1 = findBtn(fr[0], "Установить");
        check(inst1 != null, "install button visible");
        SwingUtilities.invokeAndWait(inst1::doClick);
        Thread.sleep(2500);
        check(Files.isRegularFile(inst.gameDir.resolve("mods/sodium.jar")), "sodium.jar installed");
        check(Files.isRegularFile(inst.gameDir.resolve("mods/fabric-api.jar")), "dependency fabric-api.jar installed");
        check(findBtn(fr[0], "Установлено") != null, "button shows 'Установлено'");
        shot(fr[0], out.resolve("after_mod.png"));

        System.out.println("== incompatible mod -> choose dialog (filter off)");
        // выключаем фильтр совместимости, чтобы показать Old Mod
        List<Component> cbs = new ArrayList<>(); collect(fr[0], JCheckBox.class, cbs);
        for (Component c : cbs) if (c.isShowing() && ((JCheckBox) c).getText().startsWith("Только")) SwingUtilities.invokeAndWait(((JCheckBox) c)::doClick);
        Thread.sleep(1500);
        List<Component> all = new ArrayList<>(); collect(fr[0], Ui.McButton.class, all);
        Ui.McButton old = null;
        // третья карточка (Old Mod): берём последнюю кнопку "Установить"
        for (Component c : all) if (c.isShowing() && ((Ui.McButton) c).getText().equals("Установить")) old = (Ui.McButton) c;
        check(old != null, "Old Mod install button");
        final Ui.McButton fo = old;
        SwingUtilities.invokeLater(fo::doClick);
        Window d = waitDialog(5000);
        check(d != null, "alternative-version dialog shown");
        if (d != null) { shot(d, out.resolve("dlg_choose.png")); clickInDialog(d, "Отмена"); Thread.sleep(400); }
        check(!Files.exists(inst.gameDir.resolve("mods/oldmod.jar")), "nothing installed after cancel");

        System.out.println("== modpack via GUI");
        SwingUtilities.invokeAndWait(() -> fr[0].show("packs"));
        Thread.sleep(1500);
        Ui.McButton pb = findBtn(fr[0], "Установить");
        check(pb != null, "pack install button");
        final Ui.McButton fpb = pb;
        SwingUtilities.invokeLater(fpb::doClick);
        d = waitDialog(5000);
        check(d != null, "confirm dialog shown");
        if (d != null) { shot(d, out.resolve("dlg_confirm.png")); clickInDialog(d, "Установить"); }
        d = waitDialog(15000);
        check(d != null, "success dialog shown");
        if (d != null) { shot(d, out.resolve("dlg_done.png")); clickInDialog(d, "OK"); }
        Thread.sleep(800);
        check(Mc.readProfiles().stream().anyMatch(p -> p.name.equals("Test Pack") && p.isolated), "pack profile created");
        shot(fr[0], out.resolve("after_pack.png"));

        System.out.println("== versions page install (Forge via fake installer)");
        SwingUtilities.invokeAndWait(() -> fr[0].show("versions"));
        Thread.sleep(2500);
        List<Component> chips = new ArrayList<>(); collect(fr[0], Ui.Chip.class, chips);
        for (Component c : chips) if (c.isShowing() && ((Ui.Chip) c).getText().equals("Forge")) SwingUtilities.invokeAndWait(((Ui.Chip) c)::doClick);
        Thread.sleep(2000);
        shot(fr[0], out.resolve("versions_forge.png"));
        Ui.McButton ib = findBtn(fr[0], "СКАЧАТЬ И УСТАНОВИТЬ");
        check(ib != null && ib.isEnabled(), "install button enabled for Forge");
        if (ib != null) SwingUtilities.invokeAndWait(ib::doClick);
        d = waitDialog(20000);
        check(d != null, "forge done dialog");
        if (d != null) clickInDialog(d, "OK");
        Thread.sleep(500);
        check(Files.isDirectory(Mc.versionsDir().resolve("1.21.1-forge-52.0.0")), "forge version dir exists");
        check(Mc.readProfiles().stream().anyMatch(p -> p.loader.equals("forge") && p.isolated), "forge isolated profile exists");

        System.out.println(fails == 0 ? "\nGUI FLOW PASSED" : "\nGUI FLOW FAILED: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
