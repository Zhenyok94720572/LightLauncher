import java.awt.*; import javax.imageio.ImageIO; import javax.swing.*; import java.io.File;
public class SetupShot { public static void main(String[] a) throws Exception {
  Thread t = new Thread(() -> { try { lightmods.setup.Setup.main(new String[0]); } catch (Exception e) { e.printStackTrace(); } }); t.start();
  Thread.sleep(2500);
  Window w = null; for (Window x : Window.getWindows()) if (x.isShowing()) w = x;
  ImageIO.write(new Robot().createScreenCapture(w.getBounds()), "png", new File(a[0])); System.exit(0); } }
