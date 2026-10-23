package lightmods;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

/** Фоновые задачи: установки идут по очереди, сетевые запросы интерфейса — в отдельном пуле. */
public final class Tasks {
    public interface Job {
        void run(Progress p) throws Exception;
    }

    public static final ExecutorService IO = Executors.newFixedThreadPool(6, r -> {
        Thread t = new Thread(r, "lightmods-io");
        t.setDaemon(true);
        return t;
    });

    private static final ExecutorService INSTALL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "lightmods-install");
        t.setDaemon(true);
        return t;
    });

    private static final AtomicInteger PENDING = new AtomicInteger();
    public static volatile StatusBar bar;

    private Tasks() {
    }

    /** Запускает установку в очереди. onDone вызывается в потоке интерфейса: null — успех, иначе ошибка. */
    public static void run(String title, Job job, Consumer<Exception> onDone) {
        int n = PENDING.incrementAndGet();
        StatusBar b = bar;
        if (b != null) SwingUtilities.invokeLater(() -> b.queued(title, n));
        INSTALL.submit(() -> {
            AtomicLong last = new AtomicLong();
            SwingUtilities.invokeLater(() -> {
                if (b != null) b.begin(title);
            });
            Progress p = (f, t) -> {
                long now = System.currentTimeMillis();
                if (now - last.get() < 70 && f < 1.0) return;
                last.set(now);
                SwingUtilities.invokeLater(() -> {
                    if (b != null) b.progress(f, t == null ? title : t);
                });
            };
            Exception err = null;
            try {
                job.run(p);
            } catch (Exception e) {
                err = e;
                Log.i("Ошибка: " + title + " — " + e);
            }
            PENDING.decrementAndGet();
            Exception fe = err;
            SwingUtilities.invokeLater(() -> {
                if (b != null) {
                    if (fe == null) b.done(title + " — готово");
                    else b.fail(title + " — ошибка: " + friendly(fe));
                }
                if (onDone != null) onDone.accept(fe);
            });
        });
    }

    public static String friendly(Throwable e) {
        String m = e.getMessage();
        if (e instanceof java.net.UnknownHostException || e instanceof java.net.ConnectException || e instanceof java.net.http.HttpConnectTimeoutException) {
            return "нет соединения с сервером (" + (m == null ? e.getClass().getSimpleName() : m) + ")";
        }
        return m == null || m.isBlank() ? e.getClass().getSimpleName() : m;
    }

    /** Нижняя строка состояния с прогрессом. */
    public static final class StatusBar extends JPanel {
        private final JLabel label = Ui.label("Готов к работе", 12.5f, Ui.MUTED, false);
        private final JProgressBar bar = Ui.progressBar();

        public StatusBar() {
            super(new BorderLayout(14, 0));
            setBackground(Ui.BAR);
            setBorder(new EmptyBorder(0, 18, 0, 18));
            setPreferredSize(new Dimension(10, 34));
            bar.setPreferredSize(new Dimension(260, 8));
            bar.setVisible(false);
            JPanel right = new JPanel(new java.awt.GridBagLayout());
            right.setOpaque(false);
            right.add(bar);
            add(label, BorderLayout.CENTER);
            add(right, BorderLayout.EAST);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Ui.LINE);
            g.fillRect(0, 0, getWidth(), 1);
        }

        void queued(String title, int n) {
            if (n > 1) label.setText("В очереди: " + title + " (задач в очереди: " + (n - 1) + ")");
        }

        void begin(String title) {
            label.setForeground(Color.WHITE);
            label.setText(title);
            bar.setIndeterminate(true);
            bar.setVisible(true);
        }

        void progress(double f, String text) {
            label.setText(text);
            bar.setIndeterminate(false);
            bar.setValue((int) Math.round(Math.max(0, Math.min(1, f)) * 1000));
            bar.setVisible(true);
        }

        void done(String text) {
            Toast.show(text, Toast.Kind.OK);
            label.setForeground(new Color(0x7ED957));
            label.setText(text);
            bar.setVisible(false);
            bar.setIndeterminate(false);
        }

        void fail(String text) {
            Toast.show(text, Toast.Kind.ERROR);
            label.setForeground(new Color(0xFF7B72));
            label.setText(text);
            bar.setVisible(false);
            bar.setIndeterminate(false);
        }

        public void info(String text) {
            Toast.show(text, Toast.Kind.INFO);
            label.setForeground(Ui.MUTED);
            label.setText(text);
        }
    }
}
