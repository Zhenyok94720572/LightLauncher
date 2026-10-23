public class FakeMain {
    public static void main(String[] a) {
        System.out.println("ARGS:" + String.join(" ", a));
        System.out.println("NAT:" + System.getProperty("nat"));
        System.out.println("CP:" + System.getProperty("java.class.path"));
    }
}
