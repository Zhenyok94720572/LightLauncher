package lightmods;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** HTTP-слой: JSON, текст, байты, загрузка файлов с проверкой SHA-1 и прогрессом. */
public final class Net {
    public static final String UA = "LightLauncher/1.0 (Minecraft launcher)";

    /** Прогресс скачивания одного файла: прочитано / всего (всего может быть -1). */
    public interface Bytes {
        void on(long read, long total);
    }

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private Net() {
    }

    private static HttpRequest.Builder req(String url) {
        return HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", UA)
                .header("Accept", "*/*");
    }

    private static HttpResponse<InputStream> open(String url) throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<InputStream> r = CLIENT.send(req(url).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
                int code = r.statusCode();
                if (code == 200) return r;
                try (InputStream in = r.body()) {
                    in.readNBytes(256);
                }
                if (code == 429 || code >= 500) {
                    last = new IOException("HTTP " + code + " для " + url);
                    Thread.sleep(800L * (attempt + 1));
                    continue;
                }
                throw new IOException("HTTP " + code + " для " + url);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервано", e);
            } catch (IOException e) {
                if (e.getMessage() != null && e.getMessage().startsWith("HTTP 4")) throw e;
                last = e;
                try {
                    Thread.sleep(600L * (attempt + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Прервано", ie);
                }
            }
        }
        throw last != null ? last : new IOException("Не удалось загрузить " + url);
    }

    public static byte[] getBytes(String url) throws IOException {
        try (InputStream in = open(url).body()) {
            return in.readAllBytes();
        }
    }

    public static String getString(String url) throws IOException {
        return new String(getBytes(url), StandardCharsets.UTF_8);
    }

    public static Object getJson(String url) throws IOException {
        return Json.parse(getString(url));
    }

    /** JSON с кэшем на время работы программы (манифесты версий и т.п.). */
    public static Object getJsonCached(String url) throws IOException {
        String t = CACHE.get(url);
        if (t == null) {
            t = getString(url);
            CACHE.put(url, t);
        }
        return Json.parse(t);
    }

    public static String getStringCached(String url) throws IOException {
        String t = CACHE.get(url);
        if (t == null) {
            t = getString(url);
            CACHE.put(url, t);
        }
        return t;
    }

    public static String sha1(Path p) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(p)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            return hex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }

    /** Скачивает файл во временный и атомарно перемещает на место. Если задан sha1 — проверяет. */
    public static void download(String url, Path dest, String sha1, Bytes progress) throws IOException {
        if (sha1 != null && Files.isRegularFile(dest)) {
            try {
                if (sha1.equalsIgnoreCase(sha1(dest))) {
                    if (progress != null) progress.on(1, 1);
                    return;
                }
            } catch (IOException ignored) {
                // перекачаем
            }
        }
        Path parent = dest.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path tmp = parent.resolve(dest.getFileName() + "." + Long.toHexString(System.nanoTime() ^ Thread.currentThread().getId()) + ".part");
        HttpResponse<InputStream> r = open(url);
        long total = r.headers().firstValueAsLong("Content-Length").orElse(-1);
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        long read = 0;
        try (InputStream in = r.body(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                md.update(buf, 0, n);
                read += n;
                if (progress != null) progress.on(read, total);
                if (Thread.currentThread().isInterrupted()) throw new IOException("Прервано");
            }
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        if (sha1 != null && !sha1.equalsIgnoreCase(hex(md.digest()))) {
            Files.deleteIfExists(tmp);
            throw new IOException("Контрольная сумма не совпала: " + dest.getFileName());
        }
        moveInto(tmp, dest, sha1);
    }

    /** Перемещение с повторами: в Windows файл может быть на мгновение занят антивирусом или параллельной загрузкой. */
    private static void moveInto(Path tmp, Path dest, String sha1) throws IOException {
        IOException last = null;
        for (int i = 0; i < 8; i++) {
            try {
                Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException e) {
                last = e;
                // другой поток мог уже положить тот же файл
                try {
                    if (Files.isRegularFile(dest) && (sha1 == null ? Files.size(dest) == Files.size(tmp) : sha1.equalsIgnoreCase(sha1(dest)))) {
                        Files.deleteIfExists(tmp);
                        return;
                    }
                } catch (IOException ignored) {
                    // продолжим попытки
                }
                try {
                    Thread.sleep(120L * (i + 1));
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException ignored) {
            // не критично
        }
        throw last != null ? last : new IOException("Не удалось сохранить " + dest.getFileName());
    }

    /** Ответ на POST/GET без выброса исключения на кодах 4xx/5xx. */
    public static final class Resp {
        public final int code;
        public final String body;

        Resp(int code, String body) {
            this.code = code;
            this.body = body;
        }
    }

    public static Resp post(String url, String contentType, String body, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = req(url).header("Content-Type", contentType).header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) b.header(e.getKey(), e.getValue());
        return send(b.build());
    }

    public static Resp get(String url, Map<String, String> headers) throws IOException {
        HttpRequest.Builder b = req(url).header("Accept", "application/json").GET();
        if (headers != null) for (Map.Entry<String, String> e : headers.entrySet()) b.header(e.getKey(), e.getValue());
        return send(b.build());
    }

    private static Resp send(HttpRequest r) throws IOException {
        try {
            HttpResponse<String> x = CLIENT.send(r, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Resp(x.statusCode(), x.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Прервано", e);
        }
    }

    public static String form(String... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (sb.length() > 0) sb.append('&');
            sb.append(enc(kv[i])).append('=').append(enc(kv[i + 1]));
        }
        return sb.toString();
    }

    public static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String host(String url) {
        try {
            String h = URI.create(url).getHost();
            return h == null ? "" : h.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return "";
        }
    }
}
