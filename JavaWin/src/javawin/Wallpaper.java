package javawin;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.Random;

// Обои рисуются кодом, без картинок: так система остаётся одним маленьким jar.
public final class Wallpaper {
    public static final String[] NAMES = { "Цветок", "Закат", "Лес", "Ночь", "Закулисье" };

    private static BufferedImage cache;
    private static int cIdx = -1, cW, cH;

    private Wallpaper() {}

    public static synchronized BufferedImage get(int idx, int w, int h) {
        w = Math.max(w, 1); h = Math.max(h, 1);
        if (cache == null || cIdx != idx || cW != w || cH != h) {
            cache = render(idx, w, h);
            cIdx = idx; cW = w; cH = h;
        }
        return cache;
    }

    public static BufferedImage render(int idx, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        switch (idx) {
            case 1 -> sunset(g, w, h);
            case 2 -> forest(g, w, h);
            case 3 -> night(g, w, h);
            case 4 -> backrooms(g, w, h);
            default -> bloom(g, w, h);
        }
        g.dispose();
        return img;
    }

    static void vgrad(Graphics2D g, int w, int h, float[] stops, Color[] cs) {
        g.setPaint(new LinearGradientPaint(0, 0, 0, h, stops, cs));
        g.fillRect(0, 0, w, h);
    }

    static void glow(Graphics2D g, double cx, double cy, double r, Color c) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx, cy), (float) r, new float[] { 0, 1 },
            new Color[] { c, Theme.alpha(c, 0) }));
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    // Синий «цветок» из полупрозрачных лепестков.
    static void bloom(Graphics2D g, int w, int h) {
        vgrad(g, w, h, new float[] { 0, 0.55f, 1 },
            new Color[] { new Color(0x0a1630), new Color(0x123a7a), new Color(0x0b1f45) });
        double cx = w * 0.5, cy = h * 0.58, R = Math.min(w, h) * 0.42;
        glow(g, cx, cy, R * 1.6, new Color(60, 130, 255, 90));
        for (int layer = 0; layer < 3; layer++) {
            int petals = 7;
            for (int i = 0; i < petals; i++) {
                double a = -Math.PI / 2 + (i - (petals - 1) / 2.0) * 0.36 + layer * 0.12;
                double len = R * (1.05 - layer * 0.18), wid = R * (0.36 - layer * 0.05);
                AffineTransform t = new AffineTransform();
                t.translate(cx, cy);
                t.rotate(a + Math.PI / 2);
                Shape petal = t.createTransformedShape(new Ellipse2D.Double(-wid / 2, -len, wid, len));
                Color c1 = new Color(120 + layer * 40, 180 + layer * 20, 255, 70 + layer * 25);
                Color c2 = new Color(30, 80, 200, 20);
                g.setPaint(new GradientPaint((float) cx, (float) (cy - len), c1, (float) cx, (float) cy, c2));
                g.fill(petal);
                g.setColor(new Color(200, 225, 255, 40));
                g.setStroke(new BasicStroke(1.2f));
                g.draw(petal);
            }
        }
        glow(g, cx, cy, R * 0.35, new Color(190, 220, 255, 150));
    }

    static void sunset(Graphics2D g, int w, int h) {
        vgrad(g, w, h, new float[] { 0, 0.45f, 0.75f, 1 },
            new Color[] { new Color(0x2b1055), new Color(0xa1306d), new Color(0xf28c45), new Color(0xfcd27d) });
        glow(g, w * 0.5, h * 0.66, h * 0.5, new Color(255, 220, 140, 160));
        g.setColor(new Color(0xfff1c4));
        double r = h * 0.11;
        g.fill(new Ellipse2D.Double(w * 0.5 - r, h * 0.62 - r, r * 2, r * 2));
        Color[] hills = { new Color(0x7a2f5c), new Color(0x4f1f4d), new Color(0x2d1236) };
        Random rnd = new Random(7);
        for (int k = 0; k < hills.length; k++) {
            Path2D p = new Path2D.Double();
            double base = h * (0.66 + k * 0.1);
            p.moveTo(0, h);
            p.lineTo(0, base);
            int n = 6 + k * 2;
            double ph = rnd.nextDouble() * 6;
            for (int i = 0; i <= 60; i++) {
                double x = w * i / 60.0;
                double y = base - Math.sin(i / 60.0 * n + ph) * h * 0.04 - Math.sin(i / 60.0 * n * 2.3 + ph) * h * 0.02;
                p.lineTo(x, y);
            }
            p.lineTo(w, h);
            p.closePath();
            g.setColor(hills[k]);
            g.fill(p);
        }
    }

    static void forest(Graphics2D g, int w, int h) {
        vgrad(g, w, h, new float[] { 0, 0.6f, 1 },
            new Color[] { new Color(0x0f2a2e), new Color(0x3f7b6f), new Color(0xbfe3c3) });
        glow(g, w * 0.7, h * 0.35, h * 0.45, new Color(255, 250, 220, 110));
        Color[] rows = { new Color(0x2f6656), new Color(0x1f4a3f), new Color(0x123128), new Color(0x08201a) };
        Random rnd = new Random(3);
        for (int k = 0; k < rows.length; k++) {
            g.setColor(rows[k]);
            double base = h * (0.62 + k * 0.11);
            g.fill(new Rectangle2D.Double(0, base, w, h - base));
            double step = w / (14.0 + k * 3);
            for (double x = -step; x < w + step; x += step * (0.6 + rnd.nextDouble() * 0.6)) {
                double th = h * (0.16 + k * 0.05) * (0.7 + rnd.nextDouble() * 0.5), tw = th * 0.38;
                Path2D t = new Path2D.Double();
                t.moveTo(x, base - th);
                t.lineTo(x + tw / 2, base + 2);
                t.lineTo(x - tw / 2, base + 2);
                t.closePath();
                g.fill(t);
            }
        }
    }

    static void night(Graphics2D g, int w, int h) {
        vgrad(g, w, h, new float[] { 0, 0.7f, 1 },
            new Color[] { new Color(0x020111), new Color(0x10184a), new Color(0x2a2f7a) });
        Random rnd = new Random(42);
        for (int i = 0; i < w * h / 2500; i++) {
            double x = rnd.nextDouble() * w, y = rnd.nextDouble() * h * 0.85, s = rnd.nextDouble() * 1.8 + 0.4;
            g.setColor(new Color(255, 255, 255, 80 + rnd.nextInt(175)));
            g.fill(new Ellipse2D.Double(x, y, s, s));
        }
        double r = h * 0.07, mx = w * 0.78, my = h * 0.22;
        glow(g, mx, my, r * 4, new Color(200, 210, 255, 70));
        g.setColor(new Color(0xf4f1de));
        Area moon = new Area(new Ellipse2D.Double(mx - r, my - r, r * 2, r * 2));
        moon.subtract(new Area(new Ellipse2D.Double(mx - r * 0.45, my - r * 1.15, r * 2, r * 2)));
        g.fill(moon);
        g.setColor(new Color(0x05061a));
        Path2D p = new Path2D.Double();
        p.moveTo(0, h);
        for (int i = 0; i <= 40; i++) p.lineTo(w * i / 40.0, h * 0.9 - Math.abs(Math.sin(i * 0.7)) * h * 0.06);
        p.lineTo(w, h);
        p.closePath();
        g.fill(p);
    }

    // Жёлтый коридор — кивок в сторону «Закулисья».
    static void backrooms(Graphics2D g, int w, int h) {
        double vx = w * 0.5, vy = h * 0.47, bw = w * 0.16, bh = h * 0.16;
        double l = vx - bw / 2, r = vx + bw / 2, t = vy - bh / 2, b = vy + bh / 2;
        g.setColor(new Color(0xd8cc8a));
        g.fillRect(0, 0, w, h);
        Path2D ceil = poly(0, 0, w, 0, r, t, l, t);
        g.setPaint(new GradientPaint(0, 0, new Color(0xcfc79f), 0, (float) t, new Color(0x8f875f)));
        g.fill(ceil);
        Path2D floor = poly(0, h, w, h, r, b, l, b);
        g.setPaint(new GradientPaint(0, h, new Color(0x8a7a3a), 0, (float) b, new Color(0x4d4420)));
        g.fill(floor);
        Path2D lw = poly(0, 0, l, t, l, b, 0, h);
        g.setPaint(new GradientPaint(0, 0, new Color(0xd9c873), (float) l, 0, new Color(0x857636)));
        g.fill(lw);
        Path2D rw = poly(w, 0, r, t, r, b, w, h);
        g.setPaint(new GradientPaint(w, 0, new Color(0xcdbd68), (float) r, 0, new Color(0x7c6d30)));
        g.fill(rw);
        g.setColor(new Color(0x3a3315));
        g.fill(new Rectangle2D.Double(l, t, bw, bh));
        glow(g, vx, vy, bw * 0.8, new Color(255, 245, 190, 60));
        // полосы на обоях
        g.setStroke(new BasicStroke(1f));
        for (int i = 1; i < 24; i++) {
            double k = i / 24.0;
            g.setColor(new Color(110, 95, 40, 55));
            g.draw(new Line2D.Double(l * k, t * k, l * k, h - (h - b) * k));
            g.draw(new Line2D.Double(w - (w - r) * k, t * k, w - (w - r) * k, h - (h - b) * k));
        }
        // лампы на потолке
        for (int i = 0; i < 6; i++) {
            double k0 = Math.pow(0.62, i), k1 = k0 * 0.86;
            double y0 = vy - (vy - 0) * k0 * 0.95, y1 = vy - (vy - 0) * k1 * 0.95;
            double hw0 = (w / 2.0) * k0 * 0.28, hw1 = (w / 2.0) * k1 * 0.28;
            Path2D lamp = poly(vx - hw0, y0, vx + hw0, y0, vx + hw1, y1, vx - hw1, y1);
            g.setColor(new Color(255, 252, 225, 235));
            g.fill(lamp);
            glow(g, vx, (y0 + y1) / 2, hw0 * 1.6, new Color(255, 250, 210, 70));
        }
        g.setPaint(new RadialGradientPaint(new Point2D.Double(vx, vy), (float) Math.max(w, h) * 0.8f,
            new float[] { 0.4f, 1 }, new Color[] { new Color(0, 0, 0, 0), new Color(0, 0, 0, 140) }));
        g.fillRect(0, 0, w, h);
    }

    static Path2D poly(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.closePath();
        return p;
    }
}
