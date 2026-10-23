import java.nio.file.*;
public class FakeInstaller {
    public static void main(String[] a) throws Exception {
        System.out.println("Fake Forge installer, args=" + String.join(" ", a));
        Path dir = Paths.get(a[1]);
        if (!Files.isRegularFile(dir.resolve("launcher_profiles.json"))) { System.out.println("There is no minecraft launcher profile json"); System.exit(1); }
        String id = "1.21.1-forge-52.0.0";
        Path v = dir.resolve("versions").resolve(id);
        Files.createDirectories(v);
        Files.writeString(v.resolve(id + ".json"), "{\"id\":\"" + id + "\",\"inheritsFrom\":\"1.21.1\",\"libraries\":[]}");
        for (int i = 0; i < 5; i++) System.out.println("Downloading library " + i);
        System.out.println("Done");
    }
}
