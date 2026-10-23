package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.IntConsumer;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;

/** Ползунок памяти для игры: шаг 512 МБ, подсказка по объёму ОЗУ компьютера и рекомендуемому значению. */
public final class RamSlider extends JPanel {
    private final JSlider slider;
    private final JLabel value = Ui.label("", 15f, Color.WHITE, true);
    private final JLabel hint = Ui.label("", 12f, Ui.MUTED, false);
    private final int totalMb;
    private final int recommended;

    public static int systemMb() {
        try {
            java.lang.management.OperatingSystemMXBean os = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                return (int) (((com.sun.management.OperatingSystemMXBean) os).getTotalMemorySize() / (1024 * 1024));
            }
        } catch (Throwable ignored) {
            // нет данных об ОЗУ
        }
        return 8192;
    }

    public RamSlider(int initial, IntConsumer onChange) {
        super(new BorderLayout(14, 4));
        setOpaque(false);
        totalMb = systemMb();
        int max = Math.max(2048, Math.min(32768, (totalMb - 1024) / 512 * 512));
        recommended = totalMb >= 16000 ? 6144 : totalMb >= 12000 ? 4096 : totalMb >= 8000 ? 3072 : 2048;
        slider = new JSlider(1024, max, Math.max(1024, Math.min(max, initial)));
        slider.setOpaque(false);
        slider.setFocusable(false);
        slider.setUI(new BasicSliderUI(slider) {
            @Override
            public void paintTrack(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Rectangle t = trackRect;
                int cy = t.y + t.height / 2;
                g.setColor(Ui.FIELD);
                g.fill(new RoundRectangle2D.Double(t.x, cy - 3, t.width, 6, 6, 6));
                int fx = xPositionForValue(slider.getValue());
                g.setColor(slider.getValue() > recommended * 1.5 && slider.getValue() > totalMb * 0.6 ? new Color(0xE0A33C) : Ui.GREEN_HI);
                g.fill(new RoundRectangle2D.Double(t.x, cy - 3, fx - t.x, 6, 6, 6));
                int rx = xPositionForValue(recommended);
                g.setColor(new Color(255, 255, 255, 90));
                g.fillRect(rx - 1, cy - 8, 2, 16);
                g.dispose();
            }

            @Override
            public void paintThumb(Graphics g0) {
                Graphics2D g = Ui.aa(g0);
                Rectangle r = thumbRect;
                double d = 18;
                double x = r.x + (r.width - d) / 2.0;
                double y = r.y + (r.height - d) / 2.0;
                g.setColor(new Color(0, 0, 0, 90));
                g.fill(new Ellipse2D.Double(x, y + 1.5, d, d));
                g.setColor(Color.WHITE);
                g.fill(new Ellipse2D.Double(x, y, d, d));
                g.setColor(Ui.GREEN);
                g.fill(new Ellipse2D.Double(x + 5, y + 5, d - 10, d - 10));
                g.dispose();
            }

            @Override
            protected Dimension getThumbSize() {
                return new Dimension(20, 20);
            }

            @Override
            public void paintFocus(Graphics g) {
            }
        });
        slider.addChangeListener(e -> {
            int v = (int) Math.round(slider.getValue() / 512.0) * 512;
            if (!slider.getValueIsAdjusting() && v != slider.getValue()) {
                slider.setValue(v);
                return;
            }
            refresh();
            onChange.accept(v);
        });
        slider.setPreferredSize(new Dimension(420, 30));
        value.setPreferredSize(new Dimension(92, 24));
        value.setHorizontalAlignment(JLabel.RIGHT);
        add(slider, BorderLayout.CENTER);
        add(value, BorderLayout.EAST);
        add(hint, BorderLayout.SOUTH);
        refresh();
    }

    public void setValue(int mb) {
        slider.setValue(mb);
    }

    private void refresh() {
        int v = (int) Math.round(slider.getValue() / 512.0) * 512;
        value.setText(v % 1024 == 0 ? (v / 1024) + (I18n.isEn() ? " GB" : " ГБ") : String.format(java.util.Locale.ROOT, "%.1f", v / 1024.0) + (I18n.isEn() ? " GB" : " ГБ"));
        String ram = String.format(java.util.Locale.ROOT, "%.0f", totalMb / 1024.0);
        String rec = (recommended / 1024) + "";
        hint.setText(I18n.isEn()
                ? "Computer RAM: " + ram + " GB · recommended " + rec + " GB (mark on the bar). For big modpacks 6–8 GB."
                : "ОЗУ компьютера: " + ram + " ГБ · рекомендуется " + rec + " ГБ (метка на шкале). Для больших сборок 6–8 ГБ.");
    }

    @Override
    public void setEnabled(boolean e) {
        super.setEnabled(e);
        slider.setEnabled(e);
    }

    JComponent slider() {
        return slider;
    }
}
