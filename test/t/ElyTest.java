import lightmods.*;
public class ElyTest {
    static int fails = 0;
    static void check(boolean ok, String m) { System.out.println((ok ? "  ok   " : "  FAIL ") + m); if (!ok) fails++; }
    public static void main(String[] a) throws Exception {
        try { Account.loginEly("u", "wrong", null); check(false, "bad password rejected"); } catch (java.io.IOException e) { check(e.getMessage().contains("Неверный"), "bad password rejected"); }
        try { Account.loginEly("u", "secret", null); check(false, "2fa asked"); } catch (Account.TwoFactor e) { check(true, "2fa asked"); }
        Account acc = Account.loginEly("u", "secret", "123456");
        check(acc.isEly() && acc.name.equals("ElyPlayer"), "logged in");
        Account.Session s = acc.session();
        check(s.online && s.ely && s.accessToken.equals("ELYTOK"), "session valid");
        Account.load();
        check(Account.current() != null && Account.current().isEly(), "persisted");
        System.out.println(fails == 0 ? "ELY PASSED" : "ELY FAILED");
        System.exit(fails);
    }
}
