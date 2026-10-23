import lightmods.*;
import java.nio.file.*;
import java.util.*;

public class FlowTest {
    static int fails = 0;
    static void check(boolean ok, String what) { System.out.println((ok ? "  OK   " : "  FAIL ") + what); if (!ok) fails++; }

    public static void main(String[] a) throws Exception {
        Path tmp = Files.createTempDirectory("mcdir");
        Mc.dir = tmp.resolve(".minecraft");
        Files.createDirectories(Mc.dir);
        // существующий профиль с посторонними полями — должен сохраниться
        Files.writeString(Mc.profilesFile(), "{\"profiles\":{\"abc\":{\"name\":\"Mine\",\"lastVersionId\":\"1.20.1\",\"type\":\"custom\",\"javaArgs\":\"-Xmx4G\",\"icon\":\"Grass\"}},\"settings\":{\"soundOn\":true},\"version\":3,\"clientToken\":\"tok\"}");

        System.out.println("== Json");
        Object o = Json.parse("{\"a\":[1,2.5,\"x\\u0416\\n\"],\"b\":{\"c\":null,\"d\":true}}");
        check(Json.stringify(o, false).equals("{\"a\":[1,2.5,\"xЖ\\n\"],\"b\":{\"c\":null,\"d\":true}}"), "roundtrip");

        System.out.println("== Vanilla / Fabric");
        Mc.installVanilla("1.21.1", Progress.NONE);
        check(Files.size(Mc.versionsDir().resolve("1.21.1/1.21.1.jar")) > 0, "client jar downloaded + sha1 verified");
        String fid = Mc.installFabricLike(false, "1.21.1", "0.16.9", Progress.NONE);
        check(fid.equals("fabric-loader-0.16.9-1.21.1") && Files.isRegularFile(Mc.versionsDir().resolve(fid + "/" + fid + ".json")), "fabric profile written: " + fid);
        List<Mc.LV> lv = Mc.loaderVersions("fabric", "1.21.1");
        check(lv.size() == 2 && lv.get(0).v.equals("0.16.9"), "fabric stable-only list " + lv);
        check(Mc.loaderVersions("fabric", "9.9").isEmpty(), "unsupported mc -> empty");
        List<Mc.LV> fg = Mc.loaderVersions("forge", "1.21.1");
        check(fg.size() == 2 && fg.get(0).label.contains("рекомендуемая"), "forge list newest first " + fg);
        List<Mc.LV> nf = Mc.loaderVersions("neoforge", "1.21.1");
        check(nf.size() == 2 && nf.get(0).v.equals("21.1.77"), "neoforge filter/sort " + nf);

        System.out.println("== Forge (fake installer)");
        String gid = Mc.installForgeLike(false, "1.21.1", "52.0.0", (f, t) -> {});
        check(gid.equals("1.21.1-forge-52.0.0"), "forge version detected: " + gid);
        Mc.Info inf = Mc.describe(gid);
        check("1.21.1".equals(inf.mc) && inf.loader.equals("forge"), "describe forge -> " + inf.mc + "/" + inf.loader);

        System.out.println("== Profiles / isolation");
        Mc.Profile inst = Mc.createInstance("Мой Fabric", fid, true, "Glowstone");
        check(Files.isDirectory(inst.gameDir.resolve("mods")), "isolated gameDir created " + inst.gameDir);
        String json = Files.readString(Mc.profilesFile());
        check(json.contains("\"javaArgs\": \"-Xmx4G\"") && json.contains("clientToken") && json.contains("soundOn"), "existing launcher_profiles fields preserved");
        check(json.contains("gameDir") && json.contains(fid), "new profile written");
        check(Files.exists(Mc.profilesFile().resolveSibling("launcher_profiles.json.lightmods.bak")), "backup created");
        List<Mc.Profile> ps = Mc.readProfiles();
        Mc.Profile got = ps.stream().filter(p -> p.id.equals(inst.id)).findFirst().orElse(null);
        check(got != null && got.isolated && "fabric".equals(got.loader) && "1.21.1".equals(got.mc), "readProfiles: isolated fabric 1.21.1");
        Mc.Profile mine = ps.stream().filter(p -> p.id.equals("abc")).findFirst().orElse(null);
        check(mine != null && !mine.isolated && mine.gameDir.equals(Mc.dir), "plain profile uses .minecraft");
        check(Mc.installedVersions().size() >= 3, "installed versions listed: " + Mc.installedVersions());

        System.out.println("== Modrinth search / install");
        List<Modrinth.Hit> hits = Modrinth.search("", "mod", "1.21.1", "fabric", "relevance", 0, 20);
        check(hits.size() == 2, "filtered search: " + hits.size());
        List<Modrinth.Ver> vs = Modrinth.versions("PROJ1", got.mc, Modrinth.loadersFor(got.loader));
        Modrinth.Ver best = Modrinth.pickBest(vs);
        check(best != null, "version picked");
        List<String> notes = Modrinth.installVersion(best, "Sodium Test", "mod", got, (f, t) -> {});
        check(Files.isRegularFile(got.gameDir.resolve("mods/sodium.jar")), "mod installed");
        check(Files.isRegularFile(got.gameDir.resolve("mods/fabric-api.jar")), "required dependency installed");
        check(notes.isEmpty(), "no warnings " + notes);
        check(Mc.registry(got.gameDir).containsKey("PROJ1") && Mc.registry(got.gameDir).containsKey("LIB1"), "registry has both");
        check(Modrinth.versions("PROJ2", got.mc, Modrinth.loadersFor(got.loader)).isEmpty(), "incompatible project -> no versions");
        Modrinth.Ver rp = Modrinth.pickBest(Modrinth.versions("RP1", got.mc, null));
        Modrinth.installVersion(rp, "Faithful", "resourcepack", got, (f, t) -> {});
        check(Files.isRegularFile(got.gameDir.resolve("resourcepacks/pack.zip")), "resource pack installed");
        Modrinth.Ver sh = Modrinth.pickBest(Modrinth.versions("SH1", got.mc, null));
        Modrinth.installVersion(sh, "Shader", "shader", got, (f, t) -> {});
        check(Files.isRegularFile(got.gameDir.resolve("shaderpacks/shader.zip")), "shader installed");

        System.out.println("== Modpack");
        Modrinth.Ver mp = Modrinth.pickBest(Modrinth.versions("MP1", null, null));
        Mc.Profile pk = Modrinth.installModpack("Test Pack", mp, (f, t) -> {});
        check(Files.isRegularFile(pk.gameDir.resolve("mods/sodium.jar")), "pack file downloaded");
        check(!Files.exists(pk.gameDir.resolve("mods/serveronly.jar")), "server-only file skipped");
        check(Files.isRegularFile(pk.gameDir.resolve("resourcepacks/pack.zip")), "pack resourcepack");
        check(Files.readString(pk.gameDir.resolve("config/test.txt")).equals("hello"), "overrides applied");
        check(Files.isRegularFile(pk.gameDir.resolve("options.txt")), "client-overrides applied");
        check(!Files.exists(pk.gameDir.getParent().resolve("evil.txt")) && !Files.exists(Mc.dir.resolve("evil.txt")) && !Files.exists(tmp.resolve("evil.txt")), "zip-slip blocked");
        check(pk.versionId.equals(fid) && pk.isolated, "pack profile uses fabric " + pk.versionId);
        Mc.removeProfile(pk.id);
        Mc.deleteInstanceFiles(pk.gameDir);
        check(!Files.exists(pk.gameDir), "instance removed");
        try { Mc.deleteInstanceFiles(Mc.dir); check(false, "delete outside blocked"); } catch (Exception e) { check(true, "delete outside instances blocked"); }

        System.out.println(fails == 0 ? "\nALL PASSED" : "\nFAILED: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
