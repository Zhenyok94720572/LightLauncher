import lightmods.*;
import java.nio.file.*;
import javax.swing.*;
public class CpuIdle {
    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mccpu"); Mc.dir = tmp.resolve(".minecraft"); Files.createDirectories(Mc.dir);
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"a\":{\"name\":\"My build\",\"lastVersionId\":\"1.16.5\"}},\"settings\":{},\"version\":3}");
        Mc.agreed = Agreement.VERSION; I18n.set("ru"); Account.load(); Account.addOffline("Steve_1");
        SwingUtilities.invokeAndWait(() -> {
            try { UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName()); } catch (Exception e) {}
            MainFrame f = new MainFrame(); f.setSize(1180, 760); f.setVisible(true);
            if (a.length > 0) new Thread(() -> { try { Thread.sleep(2000); Tasks.run("Тест", p -> { for (int i = 0; i <= 100; i++) { p.set(i / 100.0, "Скачиваю " + i); Thread.sleep(150); } }, null); } catch (Exception e) {} }).start();
        });
        Thread.sleep(3000);
        com.sun.management.OperatingSystemMXBean os = (com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean();
        long t0 = os.getProcessCpuTime(); long w0 = System.nanoTime();
        Thread.sleep(8000);
        java.lang.management.ThreadMXBean tm = java.lang.management.ManagementFactory.getThreadMXBean();
        java.util.Map<Long,Long> before = new java.util.HashMap<>();
        for (long id : tm.getAllThreadIds()) before.put(id, tm.getThreadCpuTime(id));
        Thread.sleep(4000);
        for (long id : tm.getAllThreadIds()) { long d = tm.getThreadCpuTime(id) - before.getOrDefault(id, 0L); if (d > 50_000_000L) System.out.println("  thread " + tm.getThreadInfo(id).getThreadName() + " " + d / 1_000_000 + " ms"); }
        double cpu = (os.getProcessCpuTime() - t0) / (double) (System.nanoTime() - w0);
        System.out.printf("CPU cores used: %.2f%n", cpu);
        System.exit(0);
    }
}
