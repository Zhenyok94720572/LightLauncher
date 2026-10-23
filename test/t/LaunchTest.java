import java.nio.file.*;
import java.util.*;
import lightmods.*;
import java.util.zip.*;
import java.io.*;

public class LaunchTest {
    static int fails = 0;
    static void check(boolean ok, String m) { System.out.println((ok ? "  ok   " : "  FAIL ") + m); if (!ok) fails++; }
    static String sha1(byte[] b) throws Exception { var md = java.security.MessageDigest.getInstance("SHA-1"); var sb = new StringBuilder(); for (byte x : md.digest(b)) sb.append(String.format("%02x", x)); return sb.toString(); }

    public static void main(String[] a) throws Exception {
        String HOST = "http://localhost:18081";
        Path fakeJar = Paths.get(a[0]);
        Path tmp = Files.createTempDirectory("launch");
        Mc.dir = tmp.resolve(".minecraft");
        Path vs = Mc.dir.resolve("versions");
        Files.createDirectories(vs.resolve("base-1.0"));
        Files.createDirectories(vs.resolve("fake-1.0"));
        Files.copy(fakeJar, vs.resolve("base-1.0/base-1.0.jar"));
        byte[] sod = ("sodium-bytes".repeat(50)).getBytes();
        String sodSha = sha1(sod);
        // natives zip (локальный, без ссылки)
        Path natZip = Mc.dir.resolve("libraries/org/nat/1/nat-1-natives-linux.jar");
        Files.createDirectories(natZip.getParent());
        try (ZipOutputStream z = new ZipOutputStream(Files.newOutputStream(natZip))) {
            z.putNextEntry(new ZipEntry("libfoo.so")); z.write(1); z.closeEntry();
            z.putNextEntry(new ZipEntry("META-INF/x")); z.write(1); z.closeEntry();
        }
        String base = "{\"id\":\"base-1.0\",\"type\":\"release\",\"mainClass\":\"FakeMain\",\"assets\":\"idx\",\"assetIndex\":{\"id\":\"idx\"},"
            + "\"javaVersion\":{\"component\":\"jre-test\",\"majorVersion\":21},"
            + "\"libraries\":[{\"name\":\"x:sodium:1\",\"downloads\":{\"artifact\":{\"path\":\"x/sodium/1/sodium-1.jar\",\"url\":\"" + HOST + "/files/sodium.jar\",\"sha1\":\"" + sodSha + "\",\"size\":" + sod.length + "}}},"
            + "{\"name\":\"win:only:1\",\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}],\"downloads\":{\"artifact\":{\"path\":\"win/only/1/only-1.jar\",\"url\":\"" + HOST + "/files/MISSING.jar\"}}},"
            + "{\"name\":\"org:nat:1\",\"natives\":{\"linux\":\"natives-linux\"},\"extract\":{\"exclude\":[\"META-INF/\"]},\"downloads\":{\"classifiers\":{\"natives-linux\":{\"path\":\"org/nat/1/nat-1-natives-linux.jar\"}}}}],"
            + "\"arguments\":{\"jvm\":[\"-Dnat=${natives_directory}\",{\"rules\":[{\"action\":\"allow\",\"os\":{\"name\":\"windows\"}}],\"value\":\"-Dwin=1\"},\"-cp\",\"${classpath}\"],"
            + "\"game\":[\"--username\",\"${auth_player_name}\",\"--uuid\",\"${auth_uuid}\",\"--accessToken\",\"${auth_access_token}\",{\"rules\":[{\"action\":\"allow\",\"features\":{\"is_demo_user\":true}}],\"value\":\"--demo\"}]}}";
        String child = "{\"id\":\"fake-1.0\",\"inheritsFrom\":\"base-1.0\",\"type\":\"release\","
            + "\"libraries\":[{\"name\":\"x:sodium:2\",\"downloads\":{\"artifact\":{\"path\":\"x/sodium/2/sodium-2.jar\",\"url\":\"" + HOST + "/files/sodium.jar\",\"sha1\":\"" + sodSha + "\",\"size\":" + sod.length + "}}}],"
            + "\"arguments\":{\"game\":[\"--fromChild\",\"yes\"]}}";
        Files.writeString(vs.resolve("base-1.0/base-1.0.json"), base);
        Files.writeString(vs.resolve("fake-1.0/fake-1.0.json"), child);
        Path idx = Mc.dir.resolve("assets/indexes/idx.json");
        Files.createDirectories(idx.getParent());
        StringBuilder objs = new StringBuilder();
        for (int i = 0; i < 60; i++) objs.append(i > 0 ? "," : "").append("\"dup").append(i).append(".txt\":{\"hash\":\"").append(sodSha).append("\",\"size\":").append(sod.length).append("}");
        Files.writeString(idx, "{\"objects\":{" + objs + "}}");

        Account.load();
        Account acc = Account.addOffline("Steve_1");
        check(acc.uuid.length() == 32, "offline uuid " + acc.uuid);
        check(!Account.validName("a b") && Account.validName("Ab_12"), "name validation");

        Mc.Profile prof = new Mc.Profile();
        prof.id = "p1"; prof.name = "t"; prof.versionId = "fake-1.0"; prof.gameDir = tmp.resolve("game");
        Account.Session s = acc.session();
        Game.Plan plan = Game.prepare(prof, s, (f, t) -> {});
        Process pr = Game.start(prof, plan, s);
        check(pr.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) && pr.exitValue() == 0, "game process exited 0");
        Thread.sleep(500);
        String all = String.join("\n", Log.history());
        check(all.contains("[игра] ARGS:") && all.contains("--username Steve_1") && all.contains("--uuid " + acc.uuid), "player args substituted");
        check(all.contains("--accessToken 0"), "offline token");
        check(all.contains("--fromChild yes"), "child game args merged");
        check(!all.contains("--demo") && !all.contains("-Dwin"), "feature/os rules excluded");
        check(all.contains("[игра] NAT:" + Mc.dir.resolve("versions/fake-1.0/natives").toAbsolutePath()), "natives dir passed");
        check(all.contains("sodium-2.jar") && !all.contains("sodium-1.jar:") , "child lib overrides parent (dedupe) -> " + (all.contains("sodium-1.jar") ? "BAD" : "ok"));
        check(Files.isRegularFile(Mc.dir.resolve("versions/fake-1.0/natives/libfoo.so")) && !Files.exists(Mc.dir.resolve("versions/fake-1.0/natives/META-INF")), "natives extracted with exclude");
        check(Files.isExecutable(Mc.dir.resolve("runtime/jre-test").resolve("linux").resolve("jre-test/bin/java")), "java runtime downloaded + executable");
        check(Files.isRegularFile(Mc.dir.resolve("assets/objects/" + sodSha.substring(0, 2) + "/" + sodSha)), "asset object downloaded");
        check(!Files.exists(Mc.dir.resolve("libraries/win")), "windows-only lib skipped");
        check(Files.isDirectory(prof.gameDir), "game dir created");
        // повторный запуск не должен качать заново и работает
        Game.Plan plan2 = Game.prepare(prof, s, (f, t) -> {});
        check(plan2.command.equals(plan.command), "second prepare identical");
        Path ali = Paths.get(System.getProperty("user.home"), ".lightmods", "authlib-injector.jar");
        Files.createDirectories(ali.getParent()); Files.write(ali, new byte[20000]);
        Account.Session es = new Account.Session(); es.name = "ElyP"; es.uuid = "0123456789abcdef0123456789abcdef"; es.accessToken = "ELYTOK"; es.online = true; es.ely = true;
        Game.Plan eplan = Game.prepare(prof, es, (f, t) -> {});
        String ej = String.join(" ", eplan.command);
        System.out.println("ELYCMD " + ej.replaceAll(".*(-javaagent[^ ]*).*", "$1") + " | " + ej.replaceAll(".*(--userType [^ ]*).*", "$1") + " | " + ej.replaceAll(".*(--uuid [^ ]*).*", "$1"));
        check(ej.contains("-javaagent:") && ej.contains("=https://authserver.ely.by/api/authlib-injector"), "ely: javaagent with explicit API");
        check(ej.contains("--userType mojang"), "ely: userType mojang");

        // старый формат (minecraftArguments) + загрузчик с блоком arguments, как Fabric на 1.16.5
        Files.createDirectories(vs.resolve("leg-1.0"));
        Files.createDirectories(vs.resolve("fab-1.0"));
        Files.copy(fakeJar, vs.resolve("leg-1.0/leg-1.0.jar"));
        Files.writeString(vs.resolve("leg-1.0/leg-1.0.json"), "{\"id\":\"leg-1.0\",\"type\":\"release\",\"mainClass\":\"FakeMain\",\"assets\":\"idx\",\"assetIndex\":{\"id\":\"idx\"},"
            + "\"javaVersion\":{\"component\":\"jre-test\",\"majorVersion\":21},\"libraries\":[],"
            + "\"minecraftArguments\":\"--username ${auth_player_name} --version ${version_name} --gameDir ${game_directory} --uuid ${auth_uuid} --accessToken ${auth_access_token} --userType ${user_type}\"}");
        Files.writeString(vs.resolve("fab-1.0/fab-1.0.json"), "{\"id\":\"fab-1.0\",\"inheritsFrom\":\"leg-1.0\",\"type\":\"release\",\"mainClass\":\"FakeMain\","
            + "\"arguments\":{\"game\":[],\"jvm\":[\"-Dnat=legacyfab\"]},\"libraries\":[]}");
        Mc.Profile fp = new Mc.Profile();
        fp.id = "p2"; fp.name = "fab"; fp.versionId = "fab-1.0"; fp.gameDir = tmp.resolve("game2");
        Game.Plan fplan = Game.prepare(fp, s, (f, t) -> {});
        String fc = String.join(" ", fplan.command);
        check(fc.contains("-cp ") && fc.contains("-Djava.library.path=") && fc.contains("--username Steve_1") && fc.contains("--userType legacy") && fc.contains("-Dnat=legacyfab"), "legacy+loader args merged: " + fc.replaceAll(".*FakeMain", "FakeMain"));
        Process fpr = Game.start(fp, fplan, s);
        check(fpr.waitFor(20, java.util.concurrent.TimeUnit.SECONDS) && fpr.exitValue() == 0, "legacy+loader game runs");

        // Microsoft
        Mc.msClientId = "cid";
        Account.DeviceCode dc = Account.startDeviceLogin();
        check("ABCD1234".equals(dc.userCode), "device code " + dc.userCode);
        Account ms = Account.finishDeviceLogin(dc, () -> false);
        check("MsPlayer".equals(ms.name) && ms.isMs(), "ms profile " + ms.name);
        Account.Session ms2 = ms.session();
        check("MCTOKEN".equals(ms2.accessToken) && ms2.online, "ms session token");
        ms.mcExpires = 0;
        Account.Session ms3 = ms.session();
        check("MCTOKEN".equals(ms3.accessToken), "ms refresh flow");
        Account.load();
        boolean persisted = false;
        for (Account x : Account.all()) if (x.isMs() && "MsPlayer".equals(x.name) && x.refresh != null) persisted = true;
        check(persisted, "accounts persisted");
        System.out.println(fails == 0 ? "LAUNCH TEST PASSED" : "LAUNCH TEST FAILED: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
