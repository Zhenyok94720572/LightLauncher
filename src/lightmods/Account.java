package lightmods;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Аккаунты: офлайн-ник и вход Microsoft (device code). Хранятся в ~/.lightmods/accounts.json. */
public final class Account {
    static final String LOGIN = System.getProperty("lightmods.msauth", "https://login.microsoftonline.com/consumers/oauth2/v2.0");
    static final String XBL = System.getProperty("lightmods.xbl", "https://user.auth.xboxlive.com/user/authenticate");
    static final String XSTS = System.getProperty("lightmods.xsts", "https://xsts.auth.xboxlive.com/xsts/authorize");
    static final String MCAPI = System.getProperty("lightmods.mcapi", "https://api.minecraftservices.com");
    static final String ELY = System.getProperty("lightmods.ely", "https://authserver.ely.by/auth");
    static final String ELY_API = System.getProperty("lightmods.elyapi", "https://authserver.ely.by/api/authlib-injector");
    static final String ALI_META = System.getProperty("lightmods.ali", "https://authlib-injector.yushi.moe/artifact/latest.json");
    static final String SCOPE = "XboxLive.signin offline_access";

    private static final Path FILE = Paths.get(System.getProperty("user.home"), ".lightmods", "accounts.json");

    /** Данные для запуска игры. */
    public static final class Session {
        public String name;
        public String uuid;
        public String accessToken = "0";
        public String xuid;
        public boolean online;
        public boolean ely;
    }

    public String type = "offline";
    public String name;
    public String uuid;
    public String refresh;
    public String mcToken;
    public long mcExpires;
    public String xuid;

    private static final List<Account> LIST = new ArrayList<>();
    private static String selected;
    private static final List<Runnable> LISTENERS = new java.util.concurrent.CopyOnWriteArrayList<>();

    public boolean isMs() {
        return "msa".equals(type);
    }

    /** Есть онлайн-сессия (Microsoft или Ely.by) — значит скины и сетевые обходы для офлайна не нужны. */
    public boolean isOnline() {
        return isMs() || isEly();
    }

    public boolean isEly() {
        return "ely".equals(type);
    }

    public String key() {
        return type + ":" + (isMs() || isEly() ? uuid : name.toLowerCase());
    }

    public static void addListener(Runnable r) {
        LISTENERS.add(r);
    }

    private static void fire() {
        for (Runnable r : LISTENERS) r.run();
    }

    public static synchronized List<Account> all() {
        return new ArrayList<>(LIST);
    }

    public static synchronized Account current() {
        for (Account a : LIST) if (a.key().equals(selected)) return a;
        return LIST.isEmpty() ? null : LIST.get(0);
    }

    public static synchronized void select(Account a) {
        selected = a.key();
        save();
        fire();
    }

    public static synchronized void remove(Account a) {
        LIST.removeIf(x -> x.key().equals(a.key()));
        if (a.key().equals(selected)) selected = null;
        save();
        fire();
    }

    public static String offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }

    public static boolean validName(String n) {
        return n != null && n.matches("[A-Za-z0-9_]{3,16}");
    }

    public static synchronized Account addOffline(String name) {
        Account a = new Account();
        a.name = name;
        a.uuid = offlineUuid(name);
        for (Account x : LIST) if (x.key().equals(a.key())) {
            selected = x.key();
            save();
            fire();
            return x;
        }
        LIST.add(a);
        selected = a.key();
        save();
        fire();
        return a;
    }

    private static synchronized void addMs(Account a) {
        LIST.removeIf(x -> x.key().equals(a.key()));
        LIST.add(a);
        selected = a.key();
        save();
        fire();
    }

    // ------------------------------------------------------------------ файл

    public static synchronized void load() {
        LIST.clear();
        try {
            if (!Files.isRegularFile(FILE)) return;
            Map<String, Object> m = Json.map(Json.parse(Files.readString(FILE)));
            selected = Json.str(m, "selected");
            for (Object o : Json.list(m.get("accounts"))) {
                Map<String, Object> x = Json.map(o);
                Account a = new Account();
                a.type = Json.str(x, "type", "offline");
                a.name = Json.str(x, "name");
                a.uuid = Json.str(x, "uuid");
                a.refresh = Json.str(x, "refresh");
                a.mcToken = Json.str(x, "mcToken");
                a.mcExpires = Json.num(x, "mcExpires");
                a.xuid = Json.str(x, "xuid");
                if (a.name != null && a.uuid != null) LIST.add(a);
            }
        } catch (Exception e) {
            Log.i("Не удалось прочитать аккаунты: " + e.getMessage());
        }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            List<Object> arr = new ArrayList<>();
            for (Account a : LIST) {
                Map<String, Object> x = new LinkedHashMap<>();
                x.put("type", a.type);
                x.put("name", a.name);
                x.put("uuid", a.uuid);
                if (a.isMs() || a.isEly()) {
                    x.put("refresh", a.refresh);
                    x.put("mcToken", a.mcToken);
                    x.put("mcExpires", a.mcExpires);
                    if (a.xuid != null) x.put("xuid", a.xuid);
                }
                arr.add(x);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("selected", selected);
            m.put("accounts", arr);
            Files.writeString(FILE, Json.stringify(m, true));
            try {
                FILE.toFile().setReadable(false, false);
                FILE.toFile().setReadable(true, true);
                FILE.toFile().setWritable(true, true);
            } catch (RuntimeException ignored) {
                // права доступа — по возможности
            }
        } catch (IOException e) {
            Log.i("Не удалось сохранить аккаунты: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ Microsoft

    public static final class DeviceCode {
        public String userCode;
        public String url;
        public String deviceCode;
        public int interval = 5;
        public int expiresIn = 900;
    }

    public static DeviceCode startDeviceLogin() throws IOException {
        String cid = Mc.msClientId == null ? "" : Mc.msClientId.trim();
        if (cid.isEmpty()) throw new IOException("Не указан Azure Client ID (Настройки → Вход Microsoft)");
        Net.Resp r = Net.post(LOGIN + "/devicecode", "application/x-www-form-urlencoded", Net.form("client_id", cid, "scope", SCOPE), null);
        Map<String, Object> m = Json.map(Json.parse(r.body));
        if (r.code != 200) throw new IOException("Microsoft: " + Json.str(m, "error_description", Json.str(m, "error", "HTTP " + r.code)));
        DeviceCode d = new DeviceCode();
        d.userCode = Json.str(m, "user_code");
        d.url = Json.str(m, "verification_uri", "https://microsoft.com/link");
        d.deviceCode = Json.str(m, "device_code");
        d.interval = (int) Math.max(1, Json.num(m, "interval"));
        d.expiresIn = (int) Math.max(30, Json.num(m, "expires_in"));
        return d;
    }

    /** Ждёт, пока пользователь подтвердит вход, затем проходит всю цепочку до профиля Minecraft. */
    public static Account finishDeviceLogin(DeviceCode d, java.util.function.BooleanSupplier cancelled) throws IOException {
        String cid = Mc.msClientId.trim();
        long until = System.currentTimeMillis() + d.expiresIn * 1000L;
        int interval = d.interval;
        while (System.currentTimeMillis() < until) {
            if (cancelled.getAsBoolean()) throw new IOException("Вход отменён");
            try {
                Thread.sleep(interval * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Прервано");
            }
            Net.Resp r = Net.post(LOGIN + "/token", "application/x-www-form-urlencoded",
                    Net.form("grant_type", "urn:ietf:params:oauth:grant-type:device_code", "client_id", cid, "device_code", d.deviceCode), null);
            Map<String, Object> m = Json.map(Json.parse(r.body));
            if (r.code == 200) {
                Account a = new Account();
                a.type = "msa";
                a.refresh = Json.str(m, "refresh_token");
                chain(a, Json.str(m, "access_token"));
                addMs(a);
                return a;
            }
            String err = Json.str(m, "error", "");
            if (err.equals("authorization_pending")) continue;
            if (err.equals("slow_down")) {
                interval += 5;
                continue;
            }
            if (err.equals("expired_token") || err.equals("authorization_declined") || err.equals("bad_verification_code")) {
                throw new IOException("Вход не подтверждён (" + err + ")");
            }
            throw new IOException("Microsoft: " + Json.str(m, "error_description", err));
        }
        throw new IOException("Время на вход истекло");
    }

    private static Map<String, Object> postJson(String url, Object body, Map<String, String> headers) throws IOException {
        Net.Resp r = Net.post(url, "application/json", Json.stringify(body, false), headers);
        Map<String, Object> m;
        try {
            m = Json.map(Json.parse(r.body));
        } catch (RuntimeException e) {
            m = new LinkedHashMap<>();
        }
        if (r.code != 200) {
            long x = Json.num(m, "XErr");
            if (x == 2148916233L) throw new IOException("У этой учётной записи Microsoft нет профиля Xbox. Создайте его на xbox.com");
            if (x == 2148916238L) throw new IOException("Детская учётная запись: добавьте её в семейную группу Microsoft");
            if (x == 2148916235L) throw new IOException("Xbox Live недоступен в вашей стране");
            throw new IOException("Ошибка входа (" + url.replaceAll("^https?://([^/]+).*", "$1") + "): HTTP " + r.code + " " + Json.str(m, "Message", Json.str(m, "error", "")));
        }
        return m;
    }

    private static String uhs(Map<String, Object> m) {
        Map<String, Object> claims = Json.map(m.get("DisplayClaims"));
        List<Object> xui = Json.list(claims.get("xui"));
        return xui.isEmpty() ? "" : Json.str(Json.map(xui.get(0)), "uhs", "");
    }

    /** access_token Microsoft → XBL → XSTS → Minecraft → профиль. */
    private static void chain(Account a, String msAccess) throws IOException {
        Map<String, Object> props = new LinkedHashMap<>();
        props.put("AuthMethod", "RPS");
        props.put("SiteName", "user.auth.xboxlive.com");
        props.put("RpsTicket", "d=" + msAccess);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Properties", props);
        body.put("RelyingParty", "http://auth.xboxlive.com");
        body.put("TokenType", "JWT");
        Map<String, Object> xbl = postJson(XBL, body, Map.of("x-xbl-contract-version", "1"));
        String xblToken = Json.str(xbl, "Token");

        Map<String, Object> p2 = new LinkedHashMap<>();
        p2.put("SandboxId", "RETAIL");
        p2.put("UserTokens", List.of(xblToken));
        Map<String, Object> b2 = new LinkedHashMap<>();
        b2.put("Properties", p2);
        b2.put("RelyingParty", "rp://api.minecraftservices.com/");
        b2.put("TokenType", "JWT");
        Map<String, Object> xsts = postJson(XSTS, b2, Map.of("x-xbl-contract-version", "1"));
        String uhs = uhs(xsts);

        Map<String, Object> b3 = new LinkedHashMap<>();
        b3.put("identityToken", "XBL3.0 x=" + uhs + ";" + Json.str(xsts, "Token"));
        Map<String, Object> mc = postJson(MCAPI + "/authentication/login_with_xbox", b3, null);
        a.mcToken = Json.str(mc, "access_token");
        a.mcExpires = Instant.now().getEpochSecond() + Math.max(60, Json.num(mc, "expires_in")) - 120;
        a.xuid = null;

        Net.Resp pr = Net.get(MCAPI + "/minecraft/profile", Map.of("Authorization", "Bearer " + a.mcToken));
        if (pr.code == 404) throw new IOException("На этой учётной записи нет лицензии Minecraft: Java Edition");
        if (pr.code != 200) throw new IOException("Профиль Minecraft недоступен: HTTP " + pr.code);
        Map<String, Object> prof = Json.map(Json.parse(pr.body));
        a.name = Json.str(prof, "name");
        a.uuid = Json.str(prof, "id");
        if (a.name == null || a.uuid == null) throw new IOException("Профиль Minecraft пуст");
    }

    /** Данные для запуска: у офлайн-аккаунта сразу, у Microsoft — после обновления токена при необходимости. */
    public Session session() throws IOException {
        Session s = new Session();
        s.name = name;
        s.uuid = uuid;
        if (isEly()) {
            refreshEly();
            s.accessToken = mcToken;
            s.online = true;
            s.ely = true;
            return s;
        }
        if (!isMs()) return s;
        if (Instant.now().getEpochSecond() >= mcExpires || mcToken == null) refreshTokens();
        s.accessToken = mcToken;
        s.online = true;
        s.xuid = xuid;
        return s;
    }

    private void refreshTokens() throws IOException {
        String cid = Mc.msClientId == null ? "" : Mc.msClientId.trim();
        if (cid.isEmpty()) throw new IOException("Не указан Azure Client ID (Настройки → Вход Microsoft)");
        if (refresh == null) throw new IOException("Нужно войти в аккаунт Microsoft заново");
        Net.Resp r = Net.post(LOGIN + "/token", "application/x-www-form-urlencoded",
                Net.form("grant_type", "refresh_token", "client_id", cid, "refresh_token", refresh, "scope", SCOPE), null);
        Map<String, Object> m = Json.map(Json.parse(r.body));
        if (r.code != 200) {
            throw new IOException("Сессия Microsoft истекла — войдите заново (" + Json.str(m, "error", "HTTP " + r.code) + ")");
        }
        if (Json.str(m, "refresh_token") != null) refresh = Json.str(m, "refresh_token");
        chain(this, Json.str(m, "access_token"));
        synchronized (Account.class) {
            save();
        }
    }

    // ------------------------------------------------------------------ Ely.by

    /** Исключение «нужен код двухфакторной защиты». */
    public static final class TwoFactor extends IOException {
        public TwoFactor() {
            super("Нужен код двухфакторной аутентификации");
        }
    }

    private static Map<String, Object> elyCall(String path, Map<String, Object> body) throws IOException {
        Net.Resp r = Net.post(ELY + path, "application/json", Json.stringify(body, false), null);
        Map<String, Object> m;
        try {
            m = Json.map(Json.parse(r.body == null ? "" : r.body));
        } catch (RuntimeException e) {
            m = new LinkedHashMap<>();
        }
        if (r.code == 200 || r.code == 204) return m;
        String msg = Json.str(m, "errorMessage", "");
        if (msg.contains("two factor") || msg.contains("three_fa") || msg.contains("2fa")) throw new TwoFactor();
        if (r.code == 401 || r.code == 403) throw new IOException(msg.contains("Invalid credentials") || msg.isEmpty()
                ? "Неверный логин или пароль Ely.by" : "Ely.by: " + msg);
        throw new IOException("Ely.by недоступен: HTTP " + r.code + " " + msg);
    }

    /** Вход по логину (ник или e-mail) и паролю Ely.by; при включённой 2FA код дописывается к паролю через «:». */
    public static Account loginEly(String user, String password, String totp) throws IOException {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("username", user);
        b.put("password", totp == null || totp.isBlank() ? password : password + ":" + totp.trim());
        b.put("clientToken", UUID.randomUUID().toString().replace("-", ""));
        b.put("requestUser", true);
        Map<String, Object> m = elyCall("/authenticate", b);
        Map<String, Object> prof = Json.map(m.get("selectedProfile"));
        Account a = new Account();
        a.type = "ely";
        a.name = Json.str(prof, "name");
        a.uuid = Json.str(prof, "id");
        a.mcToken = Json.str(m, "accessToken");
        a.refresh = Json.str(m, "clientToken");
        if (a.name == null || a.uuid == null || a.mcToken == null) throw new IOException("Ely.by не вернул профиль игрока");
        addMs(a);
        return a;
    }

    private void refreshEly() throws IOException {
        if (mcToken == null || refresh == null) throw new IOException("Войдите в Ely.by заново");
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("accessToken", mcToken);
        b.put("clientToken", refresh);
        Net.Resp v = Net.post(ELY + "/validate", "application/json", Json.stringify(b, false), null);
        if (v.code == 200 || v.code == 204) return;
        try {
            Map<String, Object> r = elyCall("/refresh", b);
            String t = Json.str(r, "accessToken");
            if (t != null) mcToken = t;
        } catch (IOException e) {
            throw new IOException("Сессия Ely.by истекла — войдите заново");
        }
        synchronized (Account.class) {
            save();
        }
    }

    /** authlib-injector нужен, чтобы игра и серверы проверяли сессию на Ely.by (и показывались скины). */
    public static Path authlibInjector() throws IOException {
        Path f = Paths.get(System.getProperty("user.home"), ".lightmods", "authlib-injector.jar");
        if (Files.isRegularFile(f) && Files.size(f) > 10_000) return f;
        Map<String, Object> meta = Json.map(Net.getJson(ALI_META));
        String u = Json.str(meta, "download_url");
        if (u == null) throw new IOException("Не удалось найти authlib-injector");
        Net.download(u, f, null, null);
        return f;
    }

    /**
     * Хранилище доверенных сертификатов (JKS) из Java самого лаунчера. Игры 1.16.x и старше запускаются на Java 8 от Mojang,
     * чьи корневые сертификаты устарели: она не может подключиться к authserver.ely.by (HTTPS), и скины с сессией Ely.by не работают.
     * JKS читается любой версией Java.
     */
    public static Path trustStore() {
        try {
            Path out = Paths.get(System.getProperty("user.home"), ".lightmods", "cacerts.jks");
            if (Files.isRegularFile(out) && Files.size(out) > 1000
                    && System.currentTimeMillis() - Files.getLastModifiedTime(out).toMillis() < 30L * 24 * 3600 * 1000) return out;
            Path src = null;
            for (String n : new String[]{"jssecacerts", "cacerts"}) {
                Path c = Paths.get(System.getProperty("java.home"), "lib", "security", n);
                if (Files.isRegularFile(c)) {
                    src = c;
                    break;
                }
            }
            if (src == null) return null;
            java.security.KeyStore in = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType());
            try (java.io.InputStream is = Files.newInputStream(src)) {
                in.load(is, "changeit".toCharArray());
            }
            java.security.KeyStore ks = java.security.KeyStore.getInstance("JKS");
            ks.load(null, null);
            for (java.util.Enumeration<String> e = in.aliases(); e.hasMoreElements();) {
                String a = e.nextElement();
                java.security.cert.Certificate c = in.getCertificate(a);
                if (c != null && in.isCertificateEntry(a)) ks.setCertificateEntry(a, c);
            }
            Files.createDirectories(out.getParent());
            try (java.io.OutputStream os = Files.newOutputStream(out)) {
                ks.store(os, "changeit".toCharArray());
            }
            return out;
        } catch (Exception e) {
            Log.i("Не удалось подготовить сертификаты для Ely.by: " + e);
            return null;
        }
    }

    /** Вспомогательно для интерфейса: выполнить вход в фоне. */
    public static void loginAsync(Consumer<DeviceCode> shown, java.util.function.BooleanSupplier cancelled, Consumer<Account> ok, Consumer<Exception> fail) {
        Tasks.IO.submit(() -> {
            try {
                DeviceCode d = startDeviceLogin();
                shown.accept(d);
                Account a = finishDeviceLogin(d, cancelled);
                ok.accept(a);
            } catch (Exception e) {
                fail.accept(e);
            }
        });
    }
}
