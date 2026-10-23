package lightmods;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/** Всплывающие уведомления в правом нижнем углу окна: сами исчезают через несколько секунд. */
public final class Toast extends JComponent {
    public enum Kind {INFO, OK, ERROR}

    private static JRootPane root;
    private static final List<Toast> ACTIVE = new ArrayList<>();

    private final String text;
    private final Kind kind;
    private final long born = System.currentTimeMillis();
    private final Timer timer;
    private static final int LIFE = 3800;

    private Toast(String text, Kind kind) {
        this.text = text;
        this.kind = kind;
        timer = new Timer(30, e -> tick());
    }

    static void install(JRootPane r) {
        root = r;
    }

    public static void show(String text) {
        show(text, Kind.INFO);
    }

    public static void show(String text, Kind kind) {
        if (text == null || text.isBlank()) return;
        String t = I18n.t(text);
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> show(t, kind));
            return;
        }
        if (root == null || !root.isShowing()) return;
        for (Toast o : ACTIVE) if (o.text.equals(t)) return;
        Toast ts = new Toast(t, kind);
        ACTIVE.add(0, ts);
        while (ACTIVE.size() > 3) ACTIVE.get(ACTIVE.size() - 1).close();
        root.getLayeredPane().add(ts, JLayeredPane.POPUP_LAYER);
        layoutAll();
        ts.timer.start();
    }

    private static void layoutAll() {
        if (root == null) return;
        JLayeredPane lp = root.getLayeredPane();
        int y = lp.getHeight() - 48;
        for (Toast t : ACTIVE) {
            java.awt.Dimension d = t.size(t.getFontMetrics(Ui.bold(13.5f)));
            y -= d.height;
            t.setBounds(lp.getWidth() - d.width - 22, y, d.width, d.height);
            y -= 10;
        }
        lp.repaint();
    }

    private java.awt.Dimension size(FontMetrics fm) {
        int w = Math.min(460, fm.stringWidth(text) + 62);
        return new java.awt.Dimension(Math.max(220, w), 46);
    }

    private void tick() {
        if (System.currentTimeMillis() - born > LIFE) close();
        else repaint();
    }

    private void close() {
        timer.stop();
        ACTIVE.remove(this);
        if (getParent() != null) getParent().remove(this);
        layoutAll();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = Ui.aa(g0);
        long age = System.currentTimeMillis() - born;
        float a = age < 180 ? age / 180f : age > LIFE - 400 ? Math.max(0, (LIFE - age) / 400f) : 1f;
        g.setComposite(java.awt.AlphaComposite.SrcOver.derive(Math.max(0, Math.min(1, a))));
        int w = getWidth();
        int h = getHeight();
        g.setColor(new Color(0, 0, 0, 90));
        g.fill(new java.awt.geom.RoundRectangle2D.Double(2, 3, w - 3, h - 3, 14, 14));
        Ui.card(g, 0, 0, w - 2, h - 3, new Color(0x2A2D35), new Color(0x3E424D));
        Color dot = kind == Kind.OK ? Ui.GREEN_HI : kind == Kind.ERROR ? new Color(0xFF7B72) : new Color(0x7FA8FF);
        g.setColor(dot);
        g.fill(new Ellipse2D.Double(16, (h - 3) / 2.0 - 5, 10, 10));
        g.setFont(Ui.bold(13.5f));
        g.setColor(Color.WHITE);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(Ui.ellipsize(text, fm, w - 56), 38, (h - 3 - fm.getHeight()) / 2 + fm.getAscent());
        g.dispose();
    }
}
