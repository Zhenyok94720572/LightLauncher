import lightmods.*;
import java.nio.file.*;
public class MpFixTest {
    static int fails = 0;
    static void check(boolean ok, String m) { System.out.println((ok ? "  ok   " : "  FAIL ") + m); if (!ok) fails++; }
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mpfix");
        Account off = Account.addOffline("Tester");
        Mc.Profile p = new Mc.Profile();
        p.id = "x"; p.name = "n"; p.mc = "1.16.5"; p.loader = "fabric"; p.gameDir = tmp.resolve("g");
        check(MultiplayerFix.applies(p, off), "applies: offline + 1.16.5");
        check(MultiplayerFix.ensure(p, off, (f, t) -> {}), "ensure installs mod");
        check(Files.isRegularFile(p.gameDir.resolve("mods/enablemultiplayermode-1.0.jar")), "mod file present");
        long m1 = Files.getLastModifiedTime(p.gameDir.resolve("mods/enablemultiplayermode-1.0.jar")).toMillis();
        Thread.sleep(1100);
        check(MultiplayerFix.ensure(p, off, (f, t) -> {}), "ensure again ok");
        check(Files.getLastModifiedTime(p.gameDir.resolve("mods/enablemultiplayermode-1.0.jar")).toMillis() == m1, "not re-downloaded");
        p.loader = "vanilla";
        check(!MultiplayerFix.ensure(p, off, (f, t) -> {}), "vanilla -> false (user is told)");
        p.mc = "1.21.1"; p.loader = "fabric";
        check(!MultiplayerFix.applies(p, off), "not applied to 1.21.1");
        System.out.println(fails == 0 ? "MPFIX PASSED" : "MPFIX FAILED");
        System.exit(fails);
    }
}
