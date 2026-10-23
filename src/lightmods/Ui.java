package lightmods;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.RadialGradientPaint;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JProgressBar;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.plaf.basic.BasicScrollBarUI;

/** Тема и самописные компоненты в стиле официального лаунчера Minecraft. */
public final class Ui {
    public static final Color SIDEBAR = new Color(0x15161A);
    public static final Color BG = new Color(0x1D1E23);
    public static final Color BAR = new Color(0x111215);
    public static final Color CARD = new Color(0x26282E);
    public static final Color CARD_HI = new Color(0x2F323A);
    public static final Color FIELD = new Color(0x141519);
    public static final Color BORDER = new Color(0x363943);
    public static final Color MUTED = new Color(0x9CA0AB);
    public static final Color GREEN = new Color(0x3A9A3E);
    public static final Color GREEN_HI = new Color(0x56C25B);
    public static final Color GREEN_LO = new Color(0x2A7230);
    /** Тонкие разделители. */
    public static final Color LINE = new Color(0x26282F);

    private static final String FAMILY = pickFamily();

    private Ui() {
    }

    private static String pickFamily() {
        Set<String> have = new HashSet<>(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String f : new String[]{"Segoe UI", "SF Pro Text", "Noto Sans", "Ubuntu", "Roboto", "Helvetica Neue", "DejaVu Sans"}) {
            if (have.contains(f)) return f;
        }
        return Font.SANS_SERIF;
    }

    public static Font font(float size) {
        return new Font(FAMILY, Font.PLAIN, 12).deriveFont(size);
    }

    public static Font bold(float size) {
        return new Font(FAMILY, Font.BOLD, 12).deriveFont(size);
    }

    public static Graphics2D aa(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        return g2;
    }

    public static Color mix(Color a, Color b, double t) {
        return new Color((int) (a.getRed() + (b.getRed() - a.getRed()) * t), (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    // ------------------------------------------------------------------ логотип

    private static BufferedImage logo;

    public static synchronized BufferedImage logo() {
        if (logo != null) return logo;
        BufferedImage img = null;
        for (String n : new String[]{"/avatar.png", "/avatar.jpg", "/avatar.jpeg", "/icon.png", "/logo.png"}) {
            try (InputStream in = Ui.class.getResourceAsStream(n)) {
                if (in != null) {
                    img = ImageIO.read(in);
                    if (img != null) break;
                }
            } catch (IOException ignored) {
                // дальше
            }
        }
        if (img == null) {
            List<File> dirs = new ArrayList<>();
            try {
                File jar = new File(Ui.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                dirs.add(jar.isFile() ? jar.getParentFile() : jar);
            } catch (URISyntaxException | RuntimeException ignored) {
                // без папки jar
            }
            dirs.add(new File(System.getProperty("user.dir", ".")));
            outer:
            for (File d : dirs) {
                for (String n : new String[]{"avatar.png", "avatar.jpg", "avatar.jpeg", "icon.png", "logo.png"}) {
                    File f = new File(d, n);
                    if (f.isFile()) {
                        try {
                            img = ImageIO.read(f);
                            if (img != null) break outer;
                        } catch (IOException ignored) {
                            // дальше
                        }
                    }
                }
            }
        }
        if (img == null) img = defaultLogo(256);
        logo = squareTrim(img);
        return logo;
    }

    private static final java.util.Map<Integer, BufferedImage> SCALED = new java.util.concurrent.ConcurrentHashMap<>();

    /** Обрезает прозрачные поля и делает изображение квадратным. */
    private static BufferedImage squareTrim(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        int x0 = w;
        int y0 = h;
        int x1 = -1;
        int y1 = -1;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((src.getRGB(x, y) >>> 24) > 8) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (y < y0) y0 = y;
                    if (y > y1) y1 = y;
                }
            }
        }
        if (x1 < 0) return src;
        int cw = x1 - x0 + 1;
        int ch = y1 - y0 + 1;
        int side = Math.max(cw, ch);
        BufferedImage out = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        out.setRGB((side - cw) / 2, (side - ch) / 2, cw, ch, src.getRGB(x0, y0, cw, ch, null, 0, cw), 0, cw);
        return out;
    }

    /** Качественное уменьшение без белых ореолов: работаем в премультиплицированном альфа-канале, шаг за шагом вдвое. */
    public static BufferedImage logoScaled(int size) {
        return SCALED.computeIfAbsent(size, k -> {
            BufferedImage cur = new BufferedImage(logo().getWidth(), logo().getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g0 = cur.createGraphics();
            g0.drawImage(logo(), 0, 0, null);
            g0.dispose();
            while (cur.getWidth() / 2 >= k) {
                BufferedImage nx = new BufferedImage(cur.getWidth() / 2, cur.getHeight() / 2, BufferedImage.TYPE_INT_ARGB_PRE);
                Graphics2D g = nx.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(cur, 0, 0, nx.getWidth(), nx.getHeight(), null);
                g.dispose();
                cur = nx;
            }
            BufferedImage out = new BufferedImage(k, k, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(cur, 0, 0, k, k, null);
            g.dispose();
            return out;
        });
    }

    public static java.util.List<java.awt.Image> iconImages() {
        java.util.List<java.awt.Image> l = new ArrayList<>();
        for (int s : new int[]{16, 24, 32, 48, 64, 128, 256}) l.add(logoScaled(s));
        return l;
    }

    public static BufferedImage defaultLogo(int s) {
        BufferedImage im = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = aa(im.getGraphics());
        g.setPaint(new GradientPaint(0, 0, new Color(0x2B2F33), s, s, new Color(0x121315)));
        g.fill(new RoundRectangle2D.Double(0, 0, s, s, s * 0.22, s * 0.22));
        g.setPaint(new RadialGradientPaint(s * 0.5f, s * 0.5f, s * 0.55f, new float[]{0f, 1f}, new Color[]{new Color(0x66FFC83D, true), new Color(0x00FFC83D, true)}));
        g.fillRect(0, 0, s, s);
        Path2D bolt = new Path2D.Double();
        bolt.moveTo(s * 0.58, s * 0.10);
        bolt.lineTo(s * 0.27, s * 0.55);
        bolt.lineTo(s * 0.46, s * 0.55);
        bolt.lineTo(s * 0.38, s * 0.90);
        bolt.lineTo(s * 0.74, s * 0.40);
        bolt.lineTo(s * 0.53, s * 0.40);
        bolt.closePath();
        g.setPaint(new GradientPaint(0, (float) (s * 0.1), new Color(0xFFE066), 0, (float) (s * 0.9), new Color(0xF59E0B)));
        g.fill(bolt);
        g.setColor(new Color(0x80FFFFFF, true));
        g.setStroke(new BasicStroke(s * 0.012f));
        g.draw(bolt);
        g.dispose();
        return im;
    }

    /** Рисует аватарку заранее уменьшенной до нужного размера копией: без обрезки и без светлых краёв. */
    public static void paintAvatar(Graphics2D g, int x, int y, int s) {
        g.drawImage(logoScaled(s), x, y, null);
    }

    // ------------------------------------------------------------------ иконки навигации

    public interface Glyph {
        void paint(Graphics2D g, int x, int y, int s);
    }

    private static Graphics2D stroke(Graphics2D g, float w) {
        g.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        return g;
    }

    public static final Glyph HOME = (g, x, y, s) -> {
        stroke(g, 2f);
        Path2D p = new Path2D.Double();
        p.moveTo(x + s * 0.08, y + s * 0.5);
        p.lineTo(x + s * 0.5, y + s * 0.12);
        p.lineTo(x + s * 0.92, y + s * 0.5);
        g.draw(p);
        Path2D b = new Path2D.Double();
        b.moveTo(x + s * 0.2, y + s * 0.44);
        b.lineTo(x + s * 0.2, y + s * 0.88);
        b.lineTo(x + s * 0.8, y + s * 0.88);
        b.lineTo(x + s * 0.8, y + s * 0.44);
        g.draw(b);
        g.drawRect(x + (int) (s * 0.42), y + (int) (s * 0.58), (int) (s * 0.16), (int) (s * 0.30));
    };
    public static final Glyph SKIN = (g, x, y, s) -> {
        stroke(g, 2f);
        g.drawRect(x + (int) (s * 0.3), y + (int) (s * 0.08), (int) (s * 0.4), (int) (s * 0.36));
        Path2D b = new Path2D.Double();
        b.moveTo(x + s * 0.12, y + s * 0.92);
        b.lineTo(x + s * 0.12, y + s * 0.62);
        b.lineTo(x + s * 0.88, y + s * 0.62);
        b.lineTo(x + s * 0.88, y + s * 0.92);
        g.draw(b);
    };
    public static final Glyph MODS = (g, x, y, s) -> {
        stroke(g, 2f);
        java.awt.geom.Area a = new java.awt.geom.Area(new java.awt.geom.Rectangle2D.Double(x + s * 0.14, y + s * 0.30, s * 0.58, s * 0.58));
        a.add(new java.awt.geom.Area(new Ellipse2D.Double(x + s * 0.30, y + s * 0.10, s * 0.26, s * 0.26)));
        a.add(new java.awt.geom.Area(new Ellipse2D.Double(x + s * 0.60, y + s * 0.44, s * 0.28, s * 0.26)));
        g.draw(a);
    };
    public static final Glyph PACKS = (g, x, y, s) -> {
        stroke(g, 2f);
        Path2D p = new Path2D.Double();
        p.moveTo(x + s * 0.5, y + s * 0.08);
        p.lineTo(x + s * 0.9, y + s * 0.3);
        p.lineTo(x + s * 0.9, y + s * 0.72);
        p.lineTo(x + s * 0.5, y + s * 0.94);
        p.lineTo(x + s * 0.1, y + s * 0.72);
        p.lineTo(x + s * 0.1, y + s * 0.3);
        p.closePath();
        g.draw(p);
        g.drawLine(x + (int) (s * 0.1), y + (int) (s * 0.3), x + (int) (s * 0.5), y + (int) (s * 0.52));
        g.drawLine(x + (int) (s * 0.9), y + (int) (s * 0.3), x + (int) (s * 0.5), y + (int) (s * 0.52));
        g.drawLine(x + (int) (s * 0.5), y + (int) (s * 0.52), x + (int) (s * 0.5), y + (int) (s * 0.94));
    };
    public static final Glyph TEXTURES = (g, x, y, s) -> {
        stroke(g, 2f);
        g.drawRoundRect(x + (int) (s * 0.08), y + (int) (s * 0.14), (int) (s * 0.84), (int) (s * 0.72), 4, 4);
        Path2D m = new Path2D.Double();
        m.moveTo(x + s * 0.12, y + s * 0.8);
        m.lineTo(x + s * 0.4, y + s * 0.44);
        m.lineTo(x + s * 0.6, y + s * 0.66);
        m.lineTo(x + s * 0.72, y + s * 0.54);
        m.lineTo(x + s * 0.9, y + s * 0.8);
        g.draw(m);
        g.drawOval(x + (int) (s * 0.62), y + (int) (s * 0.24), (int) (s * 0.14), (int) (s * 0.14));
    };
    public static final Glyph SHADERS = (g, x, y, s) -> {
        stroke(g, 2f);
        g.drawOval(x + (int) (s * 0.3), y + (int) (s * 0.3), (int) (s * 0.4), (int) (s * 0.4));
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            double cx = x + s * 0.5;
            double cy = y + s * 0.5;
            g.drawLine((int) (cx + Math.cos(a) * s * 0.30), (int) (cy + Math.sin(a) * s * 0.30), (int) (cx + Math.cos(a) * s * 0.46), (int) (cy + Math.sin(a) * s * 0.46));
        }
    };
    public static final Glyph VERSIONS = (g, x, y, s) -> {
        stroke(g, 2f);
        g.drawLine(x + (int) (s * 0.5), y + (int) (s * 0.1), x + (int) (s * 0.5), y + (int) (s * 0.64));
        g.drawLine(x + (int) (s * 0.28), y + (int) (s * 0.42), x + (int) (s * 0.5), y + (int) (s * 0.64));
        g.drawLine(x + (int) (s * 0.72), y + (int) (s * 0.42), x + (int) (s * 0.5), y + (int) (s * 0.64));
        Path2D t = new Path2D.Double();
        t.moveTo(x + s * 0.1, y + s * 0.66);
        t.lineTo(x + s * 0.1, y + s * 0.9);
        t.lineTo(x + s * 0.9, y + s * 0.9);
        t.lineTo(x + s * 0.9, y + s * 0.66);
        g.draw(t);
    };
    public static final Glyph FOLDER = (g, x, y, s) -> {
        stroke(g, 2f);
        Path2D p = new Path2D.Double();
        p.moveTo(x + s * 0.08, y + s * 0.2);
        p.lineTo(x + s * 0.38, y + s * 0.2);
        p.lineTo(x + s * 0.48, y + s * 0.32);
        p.lineTo(x + s * 0.92, y + s * 0.32);
        p.lineTo(x + s * 0.92, y + s * 0.82);
        p.lineTo(x + s * 0.08, y + s * 0.82);
        p.closePath();
        g.draw(p);
    };
    public static final Glyph GEAR = (g, x, y, s) -> {
        stroke(g, 2f);
        g.drawOval(x + (int) (s * 0.3), y + (int) (s * 0.3), (int) (s * 0.4), (int) (s * 0.4));
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            double cx = x + s * 0.5;
            double cy = y + s * 0.5;
            g.drawLine((int) (cx + Math.cos(a) * s * 0.30), (int) (cy + Math.sin(a) * s * 0.30), (int) (cx + Math.cos(a) * s * 0.44), (int) (cy + Math.sin(a) * s * 0.44));
        }
        g.drawOval(x + (int) (s * 0.43), y + (int) (s * 0.43), (int) (s * 0.14), (int) (s * 0.14));
    };

    // ------------------------------------------------------------------ кнопки

    public static class McButton extends JButton {
        public enum Kind {GREEN, GRAY, DARK}

        private final Kind kind;
        private boolean hover;
        private float fontSize = 14f;

        @Override
        public void setText(String t) {
            super.setText(I18n.t(t));
        }

        public McButton(String text, Kind kind) {
            super(I18n.t(text));
            this.kind = kind;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setForeground(Color.WHITE);
            setFont(bold(fontSize));
            setMargin(new Insets(0, 0, 0, 0));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        public McButton size(float fs) {
            fontSize = fs;
            setFont(bold(fs));
            return this;
        }

        @Override
        public Dimension getPreferredSize() {
            if (isPreferredSizeSet()) return super.getPreferredSize();
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + (int) (fontSize * 2.6), (int) (fontSize * 2.7));
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0);
            int w = getWidth();
            int h = getHeight();
            Color base;
            switch (kind) {
                case GREEN:
                    base = GREEN;
                    break;
                case GRAY:
                    base = new Color(0x3A3D46);
                    break;
                default:
                    base = new Color(0x2B2D35);
            }
            boolean en = isEnabled();
            boolean pressed = en && getModel().isArmed() && getModel().isPressed();
            if (!en) base = kind == Kind.GREEN ? new Color(0x2F4A33) : new Color(0x24262C);
            else if (pressed) base = mix(base, Color.BLACK, 0.18);
            else if (hover) base = mix(base, Color.WHITE, kind == Kind.GREEN ? 0.12 : 0.07);
            double r = Math.min(12, h * 0.42);
            if (kind == Kind.GREEN && en) {
                // мягкое свечение под основной кнопкой
                g.setColor(new Color(GREEN_HI.getRed(), GREEN_HI.getGreen(), GREEN_HI.getBlue(), hover ? 46 : 26));
                g.fill(new RoundRectangle2D.Double(0, 2, w, h - 2, r + 2, r + 2));
            }
            g.setPaint(new GradientPaint(0, 0, mix(base, Color.WHITE, 0.06), 0, h, base));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h - 1, r, r));
            g.setColor(kind == Kind.GREEN ? new Color(255, 255, 255, en ? 40 : 15) : new Color(255, 255, 255, en ? (hover ? 26 : 16) : 8));
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 2, r, r));
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            String t = getText();
            int tx = (w - fm.stringWidth(t)) / 2;
            int ty = (h - fm.getHeight()) / 2 + fm.getAscent() + (pressed ? 1 : 0);
            g.setColor(en ? Color.WHITE : new Color(0x8A8E99));
            g.drawString(t, tx, ty);
            g.dispose();
        }
    }

    public static class NavButton extends JButton {
        private final Glyph glyph;
        private boolean selected;
        private boolean hover;

        public NavButton(String text, Glyph glyph) {
            super(I18n.t(text));
            this.glyph = glyph;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setHorizontalAlignment(LEFT);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(220, 42));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        public void setSelectedNav(boolean s) {
            selected = s;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0);
            int w = getWidth();
            int h = getHeight();
            if (selected) {
                g.setColor(new Color(GREEN_HI.getRed(), GREEN_HI.getGreen(), GREEN_HI.getBlue(), 30));
                g.fill(new RoundRectangle2D.Double(10, 3, w - 20, h - 6, 12, 12));
                g.setColor(GREEN_HI);
                g.fill(new RoundRectangle2D.Double(10, h / 2.0 - 9, 3.5, 18, 3.5, 3.5));
            } else if (hover) {
                g.setColor(new Color(255, 255, 255, 12));
                g.fill(new RoundRectangle2D.Double(10, 3, w - 20, h - 6, 12, 12));
            }
            g.setColor(selected ? GREEN_HI : (hover ? new Color(0xE6E7EB) : MUTED));
            glyph.paint(g, 26, (h - 20) / 2, 20);
            g.setColor(selected ? Color.WHITE : (hover ? new Color(0xE6E7EB) : new Color(0xC3C6CE)));
            g.setFont(selected ? bold(14f) : font(14f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(getText(), 58, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    public static class Chip extends JToggleButton {
        private boolean hover;

        public Chip(String text) {
            super(text);
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setFont(bold(13.5f));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }
            });
        }

        @Override
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(getFont());
            return new Dimension(fm.stringWidth(getText()) + 36, 36);
        }

        @Override
        public Dimension getMinimumSize() {
            return getPreferredSize();
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0);
            int w = getWidth();
            int h = getHeight();
            boolean sel = isSelected();
            g.setColor(sel ? GREEN : (hover ? CARD_HI : CARD));
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, 18, 18));
            if (sel) {
                g.setColor(GREEN_HI);
                g.setStroke(new BasicStroke(1.5f));
                g.draw(new RoundRectangle2D.Double(0.75, 0.75, w - 1.5, h - 1.5, 18, 18));
            } else {
                g.setColor(BORDER);
                g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 18, 18));
            }
            g.setFont(getFont());
            FontMetrics fm = g.getFontMetrics();
            g.setColor(sel ? Color.WHITE : new Color(0xDADADD));
            g.drawString(getText(), (w - fm.stringWidth(getText())) / 2, (h - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    // ------------------------------------------------------------------ поля, списки, прочее

    public static class Field extends JTextField {
        private final String placeholder;

        public Field(String placeholder) {
            this.placeholder = I18n.t(placeholder);
            setOpaque(false);
            setForeground(Color.WHITE);
            setCaretColor(Color.WHITE);
            setSelectionColor(GREEN);
            setSelectedTextColor(Color.WHITE);
            setFont(font(14f));
            setBorder(new EmptyBorder(0, 12, 0, 12));
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(Math.max(d.width, 120), 38);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(60, 38);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0);
            int w = getWidth();
            int h = getHeight();
            g.setColor(FIELD);
            g.fill(new RoundRectangle2D.Double(0, 0, w, h, 10, 10));
            g.setColor(hasFocus() ? GREEN_HI : BORDER);
            g.setStroke(new BasicStroke(hasFocus() ? 1.6f : 1f));
            g.draw(new RoundRectangle2D.Double(0.5, 0.5, w - 1, h - 1, 10, 10));
            g.dispose();
            super.paintComponent(g0);
            if (getText().isEmpty() && placeholder != null) {
                Graphics2D g2 = aa(g0);
                g2.setFont(getFont());
                g2.setColor(new Color(0x6D717C));
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(placeholder, getInsets().left, (h - fm.getHeight()) / 2 + fm.getAscent());
                g2.dispose();
            }
        }
    }

    public static class CheckIcon implements Icon {
        private final boolean on;

        public CheckIcon(boolean on) {
            this.on = on;
        }

        @Override
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = aa(g0);
            g.setColor(on ? GREEN : FIELD);
            g.fill(new RoundRectangle2D.Double(x, y, 18, 18, 5, 5));
            g.setColor(on ? GREEN_HI : BORDER);
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, 17, 17, 5, 5));
            if (on) {
                g.setColor(Color.WHITE);
                g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(x + 4, y + 9, x + 8, y + 13);
                g.drawLine(x + 8, y + 13, x + 14, y + 5);
            }
            g.dispose();
        }

        @Override
        public int getIconWidth() {
            return 18;
        }

        @Override
        public int getIconHeight() {
            return 18;
        }
    }

    public static JCheckBox check(String text, boolean sel) {
        JCheckBox c = new JCheckBox(I18n.t(text), sel) {
            @Override
            public void setText(String t) {
                super.setText(I18n.t(t));
            }
        };
        c.setOpaque(false);
        c.setForeground(new Color(0xE6E6E8));
        c.setFont(font(13.5f));
        c.setIcon(new CheckIcon(false));
        c.setSelectedIcon(new CheckIcon(true));
        c.setFocusPainted(false);
        c.setIconTextGap(9);
        c.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return c;
    }

    public static javax.swing.JLabel label(String text, float size, Color color, boolean bold) {
        javax.swing.JLabel l = new javax.swing.JLabel(I18n.t(text)) {
            @Override
            public void setText(String t) {
                super.setText(I18n.t(t));
            }
        };
        l.setFont(bold ? bold(size) : font(size));
        l.setForeground(color);
        return l;
    }

    /** Многострочная подпись фиксированного размера (без HTML): не раздувает макет длинным текстом. */
    public static class Note extends javax.swing.JTextArea {
        @Override
        public void setText(String t) {
            super.setText(I18n.t(t));
        }

        public Note(int widthPx, int lines, float size, Color color) {
            setEditable(false);
            setOpaque(false);
            setLineWrap(true);
            setWrapStyleWord(true);
            setFocusable(false);
            setBorder(null);
            setFont(font(size));
            setForeground(color);
            int h = (int) (size * 1.6 * lines) + 2;
            setPreferredSize(new Dimension(widthPx, h));
            setMinimumSize(new Dimension(60, h));
        }
    }

    /** Однострочная подпись, которая обрезается многоточием и никогда не требует больше места, чем ей дали. */
    public static class EllipsisLabel extends javax.swing.JLabel {
        @Override
        public void setText(String t) {
            super.setText(I18n.t(t));
        }

        public EllipsisLabel(String text, float size, Color color, boolean right) {
            super(I18n.t(text));
            setFont(font(size));
            setForeground(color);
            setHorizontalAlignment(right ? RIGHT : LEFT);
        }

        @Override
        public Dimension getMinimumSize() {
            return new Dimension(0, super.getPreferredSize().height);
        }

        @Override
        public Dimension getPreferredSize() {
            return new Dimension(40, super.getPreferredSize().height);
        }

        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = aa(g0);
            g.setFont(getFont());
            g.setColor(getForeground());
            FontMetrics fm = g.getFontMetrics();
            String t = ellipsize(getText() == null ? "" : getText(), fm, getWidth());
            int x = getHorizontalAlignment() == RIGHT ? getWidth() - fm.stringWidth(t) : 0;
            g.drawString(t, x, (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g.dispose();
        }
    }

    public static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br>");
    }

    /** Подпись с переносом строк: HTML с фиксированной шириной, чтобы длинный текст не раздувал макет. */
    public static String wrapHtml(String text, int widthPx) {
        return "<html><div style='width:" + widthPx + "px'>" + esc(text) + "</div></html>";
    }

    public static <T> void styleCombo(JComboBox<T> cb) {
        cb.setUI(new BasicComboBoxUI() {
            @Override
            public Dimension getMinimumSize(JComponent c) {
                return c.getPreferredSize();
            }

            @Override
            protected javax.swing.JButton createArrowButton() {
                JButton b = new JButton() {
                    @Override
                    protected void paintComponent(Graphics g0) {
                        Graphics2D g = aa(g0);
                        g.setColor(MUTED);
                        int cx = getWidth() / 2;
                        int cy = getHeight() / 2;
                        Path2D p = new Path2D.Double();
                        p.moveTo(cx - 5, cy - 2);
                        p.lineTo(cx + 5, cy - 2);
                        p.lineTo(cx, cy + 4);
                        p.closePath();
                        g.fill(p);
                        g.dispose();
                    }
                };
                b.setBorder(new EmptyBorder(0, 0, 0, 0));
                b.setContentAreaFilled(false);
                b.setFocusPainted(false);
                b.setOpaque(false);
                return b;
            }

            @Override
            public void paintCurrentValueBackground(Graphics g, Rectangle bounds, boolean hasFocus) {
                // фон рисует update() целиком со скруглением
            }

            @Override
            public void update(Graphics g0, JComponent c) {
                Graphics2D g = aa(g0);
                g.setColor(FIELD);
                g.fill(new RoundRectangle2D.Double(0, 0, c.getWidth(), c.getHeight(), 10, 10));
                g.dispose();
                paint(g0, c);
            }

            @Override
            protected javax.swing.plaf.basic.ComboPopup createPopup() {
                BasicComboPopup p = new BasicComboPopup(comboBox) {
                    @Override
                    protected JScrollPane createScroller() {
                        JScrollPane sp = super.createScroller();
                        styleScroll(sp);
                        return sp;
                    }
                };
                p.setBorder(new LineBorder(BORDER));
                p.getList().setBackground(FIELD);
                p.getList().setSelectionBackground(new Color(0x2F6B34));
                p.getList().setForeground(Color.WHITE);
                p.getList().setSelectionBackground(GREEN);
                p.getList().setSelectionForeground(Color.WHITE);
                return p;
            }
        });
        cb.setBackground(FIELD);
        cb.setForeground(Color.WHITE);
        cb.setFont(font(14f));
        cb.setBorder(new RoundBorder(BORDER, 10));
        cb.setOpaque(false);
        cb.setMaximumRowCount(12);
        cb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                JComponent c = (JComponent) super.getListCellRendererComponent(list, value, index, sel, focus);
                c.setBorder(new EmptyBorder(6, 10, 6, 10));
                c.setBackground(sel && index >= 0 ? GREEN : FIELD);
                c.setForeground(Color.WHITE);
                c.setFont(font(14f));
                return c;
            }
        });
    }

    /** Скруглённая рамка для полей и списков. */
    public static final class RoundBorder extends javax.swing.border.AbstractBorder {
        private final Color color;
        private final int radius;

        public RoundBorder(Color color, int radius) {
            this.color = color;
            this.radius = radius;
        }

        @Override
        public void paintBorder(Component c, Graphics g0, int x, int y, int w, int h) {
            Graphics2D g = aa(g0);
            g.setColor(color);
            g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, radius, radius));
            g.dispose();
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(1, 4, 1, 2);
        }
    }

    public static void styleScroll(JScrollPane sp) {
        sp.setBorder(new EmptyBorder(0, 0, 0, 0));
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        JScrollBar v = sp.getVerticalScrollBar();
        if (v != null) {
            v.setUI(new DarkScrollBarUI());
            v.setPreferredSize(new Dimension(12, 10));
            v.setUnitIncrement(24);
            v.setOpaque(false);
        }
        JScrollBar h = sp.getHorizontalScrollBar();
        if (h != null) {
            h.setUI(new DarkScrollBarUI());
            h.setPreferredSize(new Dimension(10, 12));
            h.setOpaque(false);
        }
    }

    static final class DarkScrollBarUI extends BasicScrollBarUI {
        @Override
        protected JButton createDecreaseButton(int o) {
            return zero();
        }

        @Override
        protected JButton createIncreaseButton(int o) {
            return zero();
        }

        private JButton zero() {
            JButton b = new JButton();
            b.setPreferredSize(new Dimension(0, 0));
            b.setBorder(null);
            return b;
        }

        @Override
        protected void paintTrack(Graphics g, JComponent c, Rectangle r) {
            // дорожка прозрачная
        }

        @Override
        protected void paintThumb(Graphics g0, JComponent c, Rectangle r) {
            if (r.isEmpty()) return;
            Graphics2D g = aa(g0);
            g.setColor(isThumbRollover() || isDragging ? new Color(0x6E7380) : new Color(0x474B56));
            g.fill(new RoundRectangle2D.Double(r.x + 3, r.y + 2, r.width - 6, r.height - 4, r.width - 6, r.width - 6));
            g.dispose();
        }

        @Override
        protected void configureScrollBarColors() {
            super.configureScrollBarColors();
            thumbColor = new Color(0x66666B);
            trackColor = BG;
        }
    }

    public static JProgressBar progressBar() {
        // собственная анимация: перерисовываем всю шкалу целиком, иначе на Windows с масштабированием остаются «хвосты» прежних положений
        JProgressBar b = new JProgressBar(0, 1000) {
            private final javax.swing.Timer anim = new javax.swing.Timer(50, e -> {
                if (isIndeterminate() && isShowing()) repaint();
            });

            @Override
            public void addNotify() {
                super.addNotify();
                anim.start();
            }

            @Override
            public void removeNotify() {
                anim.stop();
                super.removeNotify();
            }
        };
        b.setBorderPainted(false);
        b.setOpaque(false);
        b.setUI(new BasicProgressBarUI() {
            @Override
            protected void paintDeterminate(Graphics g0, JComponent c) {
                Graphics2D g = aa(g0);
                int w = c.getWidth();
                int h = c.getHeight();
                g.setColor(FIELD);
                g.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
                int fw = (int) (w * progressBar.getPercentComplete());
                if (fw > 0) {
                    g.setPaint(new GradientPaint(0, 0, GREEN_HI, 0, h, GREEN));
                    g.fill(new RoundRectangle2D.Double(0, 0, Math.max(h, fw), h, h, h));
                }
                g.dispose();
            }

            @Override
            protected void paintIndeterminate(Graphics g0, JComponent c) {
                Graphics2D g = aa(g0);
                int w = c.getWidth();
                int h = c.getHeight();
                g.setColor(FIELD);
                g.fill(new RoundRectangle2D.Double(0, 0, w, h, h, h));
                int bw = Math.max(40, w / 4);
                double ph = (System.currentTimeMillis() % 1800) / 1800.0;
                double pos = ph < 0.5 ? ph * 2 : (1 - ph) * 2;
                g.setColor(GREEN_HI);
                g.fill(new RoundRectangle2D.Double((w - bw) * pos, 0, bw, h, h, h));
                g.dispose();
            }
        });
        return b;
    }

    // ------------------------------------------------------------------ рисование карточек и текста

    public static void card(Graphics2D g, int x, int y, int w, int h, Color fill, Color border) {
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, 14, 14));
        if (border != null) {
            g.setColor(border);
            g.setStroke(new BasicStroke(1f));
            g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, w - 1, h - 1, 14, 14));
        }
    }

    public static List<String> wrap(String text, FontMetrics fm, int maxW, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null) return lines;
        String[] words = text.replace('\n', ' ').trim().split("\\s+");
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < words.length; i++) {
            String w = words[i];
            String tryS = cur.length() == 0 ? w : cur + " " + w;
            if (fm.stringWidth(tryS) <= maxW) {
                cur = new StringBuilder(tryS);
            } else {
                if (cur.length() > 0) {
                    lines.add(cur.toString());
                    cur = new StringBuilder();
                }
                if (lines.size() == maxLines) break;
                if (fm.stringWidth(w) > maxW) w = ellipsize(w, fm, maxW);
                cur = new StringBuilder(w);
            }
            if (lines.size() == maxLines) break;
        }
        if (lines.size() < maxLines && cur.length() > 0) {
            lines.add(cur.toString());
            cur = new StringBuilder();
        }
        boolean truncated = cur.length() > 0 || String.join(" ", lines).length() < text.replace('\n', ' ').trim().replaceAll("\\s+", " ").length();
        if (truncated && !lines.isEmpty()) {
            int last = lines.size() - 1;
            lines.set(last, ellipsize(lines.get(last) + " …", fm, maxW));
        }
        return lines;
    }

    public static String ellipsize(String s, FontMetrics fm, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        String e = "…";
        int n = s.length();
        while (n > 0 && fm.stringWidth(s.substring(0, n) + e) > maxW) n--;
        return s.substring(0, Math.max(0, n)).stripTrailing() + e;
    }

    // ------------------------------------------------------------------ фон главной

    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return (h ^ (h >>> 16)) & 0x7fffffff;
    }

    /** Фон главной: только синее небо и пиксельные облака, которые медленно плывут (timeMs — текущее время). */
    private static BufferedImage heroSky;
    private static BufferedImage heroShade;

    /** Неподвижные слои пейзажа рисуются один раз на размер окна: градиенты на всю площадь каждый кадр сильно грузят процессор. */
    private static synchronized void heroLayers(int w, int h) {
        if (heroSky != null && heroSky.getWidth() == w && heroSky.getHeight() == h) return;
        heroSky = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = heroSky.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0x2F7FE0), 0, h, new Color(0x9CCBFF)));
        g.fillRect(0, 0, w, h);
        g.dispose();
        heroShade = new BufferedImage(Math.max(1, w), Math.max(1, h), BufferedImage.TYPE_INT_ARGB);
        g = heroShade.createGraphics();
        g.setPaint(new GradientPaint(0, h * 0.30f, new Color(10, 12, 16, 0), 0, h, new Color(10, 12, 16, 215)));
        g.fillRect(0, 0, w, h);
        g.setPaint(new GradientPaint(0, 0, new Color(10, 12, 16, 120), w * 0.55f, 0, new Color(10, 12, 16, 0)));
        g.fillRect(0, 0, w, h);
        g.dispose();
    }

    public static void paintHero(Graphics2D g, int w, int h, long timeMs) {
        heroLayers(w, h);
        g.drawImage(heroSky, 0, 0, null);
        int b = Math.max(22, h / 20);
        Color white = new Color(255, 255, 255, 225);
        Color shade = new Color(214, 232, 252, 225);
        for (int i = 0; i < 10; i++) {
            int cw = b * (4 + hash(i, 13) % 5);
            double speed = 14 + hash(i, 14) % 26;
            int span = w + cw + b * 4;
            double off = hash(i, 11) % span;
            int cx = (int) ((off + speed * timeMs / 1000.0) % span) - cw - b * 2;
            // верхняя и нижняя полосы: облака не заходят под заголовок и текст
            int cy = i % 2 == 0 ? b / 2 + hash(i, 12) % Math.max(1, (int) (h * 0.14)) : (int) (h * 0.50) + hash(i, 12) % Math.max(1, (int) (h * 0.2));
            g.setColor(white);
            g.fillRect(cx, cy, cw, b);
            g.fillRect(cx + b, cy - b / 2, cw - b * 2, b / 2);
            g.fillRect(cx + b * 2, cy - b, Math.max(b, cw - b * 4), b / 2);
            g.setColor(shade);
            g.fillRect(cx, cy + b - b / 3, cw, b / 3);
        }
        g.drawImage(heroShade, 0, 0, null);
    }
}
