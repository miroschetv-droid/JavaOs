package javawin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Полноэкранные экраны вне рабочего стола: загрузка, вход, выключение.
// Фон каждого экрана рисуется один раз в картинку, а таймер перерисовывает только
// маленькие области, которые двигаются (крутилка, мигающая подсказка). Без видеоускорения
// (например, в виртуалке) перерисовка всего экрана 60 раз в секунду тормозит и заставляет
// курсор мигать.
public final class Screens {
    private Screens() {}

    static final int SPIN_R = 14;   // крутилка рисуется внутри квадрата радиуса SPIN_R вокруг центра

    // кружок из бегущих точек
    static void spinner(Graphics2D g, double cx, double cy, double r, double t, Color c) {
        for (int i = 0; i < 5; i++) {
            double p = t - i * 0.11;
            double cycle = p - Math.floor(p);
            double a = -Math.PI / 2 + (cycle + 0.35 * Math.sin(cycle * Math.PI * 2) / Math.PI) * Math.PI * 2;
            double x = cx + Math.cos(a) * r, y = cy + Math.sin(a) * r;
            g.setColor(c);
            g.fill(new Ellipse2D.Double(x - 3, y - 3, 6, 6));
        }
    }

    static Rectangle around(double cx, double cy, int r) {
        return new Rectangle((int) cx - r - 4, (int) cy - r - 4, 2 * r + 8, 2 * r + 8);
    }

    static abstract class Animated extends JPanel {
        final long t0 = System.currentTimeMillis();
        final Timer timer;
        BufferedImage bg;
        int bgW, bgH;
        Object bgKey;

        Animated(int fps) {
            setOpaque(true);
            setDoubleBuffered(true);
            setFocusable(true);
            timer = new Timer(1000 / fps, e -> tick());
            addHierarchyListener(e -> {
                if ((e.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) != 0) {
                    if (isDisplayable()) timer.start();
                    else timer.stop();
                }
            });
        }

        double secs() { return (System.currentTimeMillis() - t0) / 1000.0; }

        // каждый кадр: подвинуть то, что анимируется, и перерисовать только его область
        abstract void tick();

        // статичный фон для текущего состояния; key меняется — фон рисуется заново
        abstract Object bgKey();
        abstract void paintBackground(Graphics2D g, int w, int h);

        // поверх фона — только движущиеся части
        void paintMoving(Graphics2D g) {}

        BufferedImage background() {
            int w = Math.max(1, getWidth()), h = Math.max(1, getHeight());
            Object key = bgKey();
            if (bg == null || bgW != w || bgH != h || !key.equals(bgKey)) {
                GraphicsConfiguration gc = getGraphicsConfiguration();
                bg = gc != null ? gc.createCompatibleImage(w, h) : new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = bg.createGraphics();
                UI.aa(g);
                paintBackground(g, w, h);
                g.dispose();
                bgW = w;
                bgH = h;
                bgKey = key;
            }
            return bg;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.drawImage(background(), 0, 0, null);   // Swing уже обрезал отрисовку до грязной области
            UI.aa(g);
            paintMoving(g);
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- загрузка

    public static class Boot extends Animated {
        final Runnable done;
        boolean fired;

        public Boot(Runnable done) {
            super(30);
            this.done = done;
        }

        Point spin() { return new Point(getWidth() / 2, getHeight() / 2 + 90); }

        @Override void tick() {
            Point s = spin();
            repaint(around(s.x, s.y, SPIN_R));
            if (!fired && secs() > 2.8) {
                fired = true;
                timer.stop();
                done.run();
            }
        }

        @Override Object bgKey() { return "boot"; }

        @Override void paintBackground(Graphics2D g, int w, int h) {
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, w, h);
            Icons.draw(g, "logo", w / 2 - 60, h / 2 - 110, 120);
            g.setColor(new Color(255, 255, 255, 110));
            g.setFont(Theme.font(Font.PLAIN, 13f));
            String s = Theme.NAME + " " + Theme.VERSION;
            g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, h - 40);
        }

        @Override void paintMoving(Graphics2D g) {
            Point s = spin();
            spinner(g, s.x, s.y, SPIN_R, secs() * 0.9, Color.WHITE);
        }
    }

    // ---------------------------------------------------------------- экран блокировки и вход

    public static class Login extends Animated {
        final Runnable done;
        int phase;              // 0 часы, 1 вход, 2 «Добро пожаловать»
        double phaseAt;
        BufferedImage blur;
        int blW, blH;
        final UI.Btn enter;

        public Login(Runnable done) {
            super(20);
            this.done = done;
            setLayout(null);
            enter = UI.accent("Войти", this::signIn);
            enter.setVisible(false);
            add(enter);
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { if (phase == 0) next(); }
            });
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    if (phase == 0) next();
                    else if (phase == 1 && e.getKeyCode() == KeyEvent.VK_ENTER) signIn();
                }
            });
        }

        void next() {
            phase = 1;
            phaseAt = secs();
            enter.setVisible(true);
            repaint();
            requestFocusInWindow();
        }

        void signIn() {
            if (phase == 2) return;
            phase = 2;
            phaseAt = secs();
            enter.setVisible(false);
            repaint();
        }

        Point spin() { return new Point(getWidth() / 2 - 70, getHeight() / 2 + 92); }
        Rectangle hint() { return new Rectangle(0, getHeight() - 85, getWidth(), 40); }

        @Override void tick() {
            if (phase == 0) {
                repaint(hint());
                if (!bgKey().equals(bgKey)) repaint();          // сменилась минута
            } else if (phase == 2) {
                Point s = spin();
                repaint(around(s.x, s.y, 9));
                if (secs() - phaseAt > 1.4) {
                    phase = 3;
                    timer.stop();
                    done.run();
                }
            }
        }

        @Override public void doLayout() {
            enter.setBounds(getWidth() / 2 - 70, getHeight() / 2 + 80, 140, 34);
        }

        @Override Object bgKey() {
            return phase == 0 ? "clock " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm dd")) + Theme.wallpaper
                : "user " + Math.min(phase, 2) + Theme.user + Theme.wallpaper;
        }

        BufferedImage blurred(int w, int h) {
            if (blur == null || blW != w || blH != h) {
                BufferedImage src = Wallpaper.get(Theme.wallpaper, w, h);
                // размытие: уменьшить, размыть, растянуть обратно
                int sw = Math.max(1, w / 8), sh = Math.max(1, h / 8);
                BufferedImage small = new BufferedImage(sw, sh, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = small.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(src, 0, 0, sw, sh, null);
                g.dispose();
                float[] k = new float[25];
                java.util.Arrays.fill(k, 1f / 25);
                ConvolveOp op = new ConvolveOp(new Kernel(5, 5, k), ConvolveOp.EDGE_NO_OP, null);
                small = op.filter(op.filter(small, null), null);
                blur = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                g = blur.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(small, 0, 0, w, h, null);
                g.dispose();
                blW = w;
                blH = h;
            }
            return blur;
        }

        @Override void paintBackground(Graphics2D g, int w, int h) {
            LocalDateTime now = LocalDateTime.now();
            if (phase == 0) g.drawImage(Wallpaper.get(Theme.wallpaper, w, h), 0, 0, null);
            else g.drawImage(blurred(w, h), 0, 0, null);
            g.setColor(new Color(0, 0, 0, phase == 0 ? 50 : 90));
            g.fillRect(0, 0, w, h);
            if (phase == 0) {
                g.setColor(Color.WHITE);
                g.setFont(Theme.font(Font.PLAIN, 22f));
                String date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.of("ru")));
                date = Character.toUpperCase(date.charAt(0)) + date.substring(1);
                center(g, date, w, h / 5);
                g.setFont(Theme.font(Font.BOLD, 104f));
                center(g, now.format(DateTimeFormatter.ofPattern("HH:mm")), w, h / 5 + 120);
            } else {
                Icons.draw(g, "user", w / 2 - 70, h / 2 - 150, 140);
                g.setColor(Color.WHITE);
                g.setFont(Theme.font(Font.BOLD, 26f));
                center(g, Theme.user, w, h / 2 + 34);
                if (phase >= 2) {
                    g.setFont(Theme.font(Font.PLAIN, 17f));
                    g.drawString("Добро пожаловать", w / 2 - 50, h / 2 + 98);
                }
            }
        }

        @Override void paintMoving(Graphics2D g) {
            if (phase == 0) {
                Rectangle r = hint();
                g.setFont(Theme.font(Font.PLAIN, 14f));
                g.setColor(new Color(255, 255, 255, (int) (150 + 80 * Math.sin(secs() * 2.5))));
                center(g, "Щёлкни или нажми любую клавишу", getWidth(), r.y + 25);
            } else if (phase == 2) {
                Point s = spin();
                spinner(g, s.x, s.y, 9, secs() * 0.9, Color.WHITE);
            }
        }
    }

    static void center(Graphics2D g, String s, int w, int y) {
        g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, y);
    }

    // ---------------------------------------------------------------- выключение

    public static class Shutdown extends Animated {
        final boolean restart;
        final Runnable done;
        boolean fired;

        public Shutdown(boolean restart, Runnable done) {
            super(30);
            this.restart = restart;
            this.done = done;
        }

        Point spin() { return new Point(getWidth() / 2 - 90, getHeight() / 2 - 6); }

        @Override void tick() {
            Point s = spin();
            repaint(around(s.x, s.y, 10));
            if (!fired && secs() > 2.0) {
                fired = true;
                timer.stop();
                done.run();
            }
        }

        @Override Object bgKey() { return "off" + restart; }

        @Override void paintBackground(Graphics2D g, int w, int h) {
            g.setPaint(new GradientPaint(0, 0, new Color(0x0b2a5a), 0, h, new Color(0x061633)));
            g.fillRect(0, 0, w, h);
            g.setColor(Color.WHITE);
            g.setFont(Theme.font(Font.PLAIN, 22f));
            g.drawString(restart ? "Перезагрузка" : "Завершение работы", w / 2 - 66, h / 2);
        }

        @Override void paintMoving(Graphics2D g) {
            Point s = spin();
            spinner(g, s.x, s.y, 10, secs() * 0.9, Color.WHITE);
        }
    }
}
