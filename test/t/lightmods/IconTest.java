package lightmods;
public class IconTest { public static void main(String[] a) {
  Object ok = BrowsePanel.loadIcon("http://localhost:18080/icons/0_96.webp");
  Object ok2 = BrowsePanel.loadIcon("http://localhost:18080/icons/1.png");
  Object bad = BrowsePanel.loadIcon("http://localhost:18080/icons/none_96.webp");
  System.out.println("webp->png fallback: " + (ok != null) + ", plain png: " + (ok2 != null) + ", missing: " + (bad == null));
  System.exit(0);
} }
