package javawin;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

// Все значки рисуются векторно в квадрате 32×32 и масштабируются под нужный размер.
public final class Icons {
    private Icons() {}

    public static Icon icon(String kind, int s) {
        return new Icon() {
            public void paintIcon(Component c, Graphics g, int x, int y) { draw((Graphics2D) g, kind, x, y, s); }
            public int getIconWidth() { return s; }
            public int getIconHeight() { return s; }
        };
    }

    public static BufferedImage image(String kind, int s) {
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        draw(g, kind, 0, 0, s);
        g.dispose();
        return img;
    }

    public static List<Image> appImages() {
        List<Image> l = new ArrayList<>();
        for (int s : new int[] { 16, 20, 24, 32, 40, 48, 64, 128, 256 }) l.add(image("logo", s));
        return l;
    }

    public static void draw(Graphics2D g0, String kind, int x, int y, int s) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.translate(x, y);
        g.scale(s / 32.0, s / 32.0);
        switch (kind) {
            case "folder", "explorer" -> folder(g);
            case "text" -> { page(g, Color.WHITE); lines(g, new Color(0x9aa4b2), 9, 13, 23, 4); }
            case "file" -> page(g, new Color(0xf1f3f6));
            case "image" -> image(g);
            case "drive" -> drive(g);
            case "pc" -> pc(g);
            case "notepad" -> notepad(g);
            case "calc" -> calc(g);
            case "paint" -> paint(g);
            case "cmd" -> cmd(g);
            case "settings" -> gear(g);
            case "taskmgr" -> taskmgr(g);
            case "mines" -> { g.setPaint(new GradientPaint(0, 1, new Color(0xd6dde8), 0, 31, new Color(0x9aa8bb))); g.fill(rr(1, 1, 30, 30, 7)); g.translate(16, 16); g.scale(0.8, 0.8); g.translate(-16, -17); mine(g); }
            case "music" -> {   // нота в круге
                g.setPaint(new GradientPaint(0, 1, new Color(0xf472b6), 0, 31, new Color(0xbe185d))); g.fill(rr(1, 1, 30, 30, 7));
                g.setColor(Color.WHITE);
                g.fillOval(8, 19, 7, 6); g.fillOval(18, 17, 7, 6);
                g.fillRect(13, 8, 2, 14); g.fillRect(23, 6, 2, 14);
                g.fillPolygon(new int[] { 13, 25, 25, 13 }, new int[] { 8, 6, 10, 12 }, 4);
            }
            case "snake" -> {
                g.setPaint(new GradientPaint(0, 1, new Color(0x4ade80), 0, 31, new Color(0x15803d))); g.fill(rr(1, 1, 30, 30, 7));
                g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.setColor(new Color(0xf0fdf4));
                java.awt.geom.Path2D sn = new java.awt.geom.Path2D.Float();
                sn.moveTo(8, 24); sn.lineTo(8, 15); sn.lineTo(18, 15); sn.lineTo(18, 8); sn.lineTo(24, 8);
                g.draw(sn);
                g.setColor(new Color(0xe5484d)); g.fillOval(21, 19, 6, 6);
            }
            case "2048" -> {
                g.setPaint(new GradientPaint(0, 1, new Color(0xf6c453), 0, 31, new Color(0xe08a1e))); g.fill(rr(1, 1, 30, 30, 7));
                g.setColor(Color.WHITE); g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
                FontMetrics fm = g.getFontMetrics(); g.drawString("2048", 16 - fm.stringWidth("2048") / 2, 20);
            }
            case "clock" -> {
                g.setPaint(new GradientPaint(0, 1, new Color(0x60a5fa), 0, 31, new Color(0x1d4ed8))); g.fill(rr(1, 1, 30, 30, 7));
                g.setColor(Color.WHITE); g.fillOval(5, 5, 22, 22);
                g.setColor(new Color(0x1e3a8a)); g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(16, 16, 16, 9); g.drawLine(16, 16, 21, 19);
            }
            case "mic" -> {
                g.setPaint(new GradientPaint(0, 1, new Color(0xf87171), 0, 31, new Color(0xb91c1c))); g.fill(rr(1, 1, 30, 30, 7));
                g.setColor(Color.WHITE); g.fill(rr(12, 5, 8, 14, 8));
                g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawArc(8, 9, 16, 14, 180, 180); g.drawLine(16, 23, 16, 27); g.drawLine(12, 27, 20, 27);
            }
            case "bluetooth" -> {
                g.setPaint(new GradientPaint(0, 1, new Color(0x3b82f6), 0, 31, new Color(0x1e40af))); g.fill(rr(1, 1, 30, 30, 7));
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                java.awt.geom.Path2D bt = new java.awt.geom.Path2D.Float();
                bt.moveTo(10, 11); bt.lineTo(21, 21); bt.lineTo(16, 26); bt.lineTo(16, 6); bt.lineTo(21, 11); bt.lineTo(10, 21);
                g.draw(bt);
            }
            case "radio" -> {
                g.setPaint(new GradientPaint(0, 1, new Color(0xa78bfa), 0, 31, new Color(0x6d28d9))); g.fill(rr(1, 1, 30, 30, 7));
                g.setColor(Color.WHITE); g.fill(rr(5, 11, 22, 15, 4));
                g.setColor(new Color(0x6d28d9)); g.fillOval(8, 14, 9, 9);
                g.setColor(Color.WHITE); g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g.drawLine(9, 10, 22, 5);
                g.setColor(new Color(0x6d28d9)); g.fillRect(20, 15, 4, 2); g.fillRect(20, 19, 4, 2);
            }
            case "mine" -> mine(g);
            case "about" -> about(g);
            case "trash" -> trash(g);
            case "logo" -> logo(g);
            case "user" -> user(g);
            case "power" -> power(g, Theme.text());
            case "search" -> search(g, Theme.dim());
            case "back" -> arrow(g, 0);
            case "fwd" -> arrow(g, Math.PI);
            case "up" -> arrow(g, Math.PI / 2);
            case "refresh" -> refresh(g);
            case "plus" -> plus(g);
            case "home" -> home(g);
            case "browser" -> browser(g);
            case "linux" -> penguin(g);
            case "wifi" -> wifi(g);
            case "sound" -> sound(g);
            case "battery" -> battery(g);
            default -> page(g, Color.LIGHT_GRAY);
        }
        g.dispose();
    }

    static RoundRectangle2D rr(double x, double y, double w, double h, double r) {
        return new RoundRectangle2D.Double(x, y, w, h, r, r);
    }

    static void folder(Graphics2D g) {
        g.setColor(new Color(0xd9962b));
        Path2D tab = new Path2D.Double();
        tab.moveTo(2, 8); tab.lineTo(2, 6.5); tab.quadTo(2, 5, 3.5, 5);
        tab.lineTo(11, 5); tab.lineTo(14, 8); tab.closePath();
        g.fill(tab);
        g.fill(rr(2, 7, 28, 20, 3));
        g.setPaint(new GradientPaint(0, 10, new Color(0xffd56a), 0, 27, new Color(0xf5b83d)));
        g.fill(rr(2, 10.5, 28, 17, 3));
        g.setColor(new Color(255, 255, 255, 90));
        g.fill(new Rectangle2D.Double(3, 11, 26, 1));
    }

    static void page(Graphics2D g, Color fill) {
        Path2D p = new Path2D.Double();
        p.moveTo(7, 2); p.lineTo(19, 2); p.lineTo(26, 9); p.lineTo(26, 30); p.lineTo(7, 30); p.closePath();
        g.setColor(fill);
        g.fill(p);
        g.setColor(new Color(0x8c96a3));
        g.setStroke(new BasicStroke(1f));
        g.draw(p);
        Path2D fold = new Path2D.Double();
        fold.moveTo(19, 2); fold.lineTo(19, 9); fold.lineTo(26, 9);
        g.setColor(new Color(0xd5dae1));
        g.fill(fold);
        g.setColor(new Color(0x8c96a3));
        g.draw(fold);
    }

    static void lines(Graphics2D g, Color c, double x1, double y, double x2, int n) {
        g.setColor(c);
        g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < n; i++) g.draw(new Line2D.Double(x1, y + i * 4, i == n - 1 ? x2 - 5 : x2, y + i * 4));
    }

    static void image(Graphics2D g) {
        g.setColor(new Color(0x2f80ed));
        g.fill(rr(3, 5, 26, 22, 4));
        g.setPaint(new GradientPaint(0, 6, new Color(0x6fb6ff), 0, 26, new Color(0xbfe0ff)));
        g.fill(rr(4.5, 6.5, 23, 19, 3));
        g.setColor(new Color(0xffd34d));
        g.fill(new Ellipse2D.Double(19, 9, 5, 5));
        Path2D m = new Path2D.Double();
        m.moveTo(4.5, 23); m.lineTo(12, 13); m.lineTo(18, 20); m.lineTo(21, 17); m.lineTo(27.5, 24);
        m.lineTo(27.5, 25.5); m.lineTo(4.5, 25.5); m.closePath();
        g.setColor(new Color(0x2e9e5b));
        g.fill(m);
    }

    static void drive(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 10, new Color(0xe9edf2), 0, 24, new Color(0xaab3bf)));
        g.fill(rr(2, 11, 28, 12, 4));
        g.setColor(new Color(0x6b7480));
        g.draw(rr(2, 11, 28, 12, 4));
        g.setColor(new Color(0x45d16b));
        g.fill(new Ellipse2D.Double(24, 18, 3, 3));
        g.setColor(new Color(0x3b82f6));
        g.fill(rr(5, 14, 8, 5, 1.5));
        g.setColor(new Color(0x8892a0));
        g.fill(new Rectangle2D.Double(4, 25, 24, 2));
    }

    static void pc(Graphics2D g) {
        g.setColor(new Color(0x30343b));
        g.fill(rr(2, 4, 28, 19, 3));
        g.setPaint(new GradientPaint(4, 6, new Color(0x3b82f6), 28, 21, new Color(0x7dd3fc)));
        g.fill(rr(3.5, 5.5, 25, 16, 2));
        g.setColor(new Color(0x9aa1ab));
        g.fill(new Rectangle2D.Double(13, 23, 6, 4));
        g.fill(rr(9, 27, 14, 2.5, 2));
    }

    static void notepad(Graphics2D g) {
        g.setColor(new Color(0x2b6cd4));
        g.fill(rr(5, 3, 22, 27, 4));
        g.setColor(Color.WHITE);
        g.fill(rr(7, 8, 18, 20, 2));
        lines(g, new Color(0x7ea8e8), 9.5, 12, 22.5, 4);
        g.setColor(new Color(0xe0e8f5));
        for (int i = 0; i < 4; i++) g.fill(new Ellipse2D.Double(8.5 + i * 4.5, 4, 2.5, 2.5));
    }

    static void calc(Graphics2D g) {
        g.setColor(new Color(0x3a3f47));
        g.fill(rr(6, 2, 20, 28, 4));
        g.setColor(new Color(0xc9e7d2));
        g.fill(rr(8.5, 4.5, 15, 6, 1.5));
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 3; c++) {
                g.setColor(r == 3 && c == 2 ? new Color(0xf59e0b) : new Color(0x9aa3ae));
                g.fill(rr(8.5 + c * 5.2, 12.5 + r * 4.2, 4, 3.2, 1));
            }
    }

    static void paint(Graphics2D g) {
        Path2D pal = new Path2D.Double();
        pal.moveTo(16, 4);
        pal.curveTo(29, 4, 31, 14, 28, 19);
        pal.curveTo(26, 22, 21, 19, 20, 23);
        pal.curveTo(19, 27, 18, 29, 14, 29);
        pal.curveTo(6, 29, 2, 23, 2, 16);
        pal.curveTo(2, 9, 8, 4, 16, 4);
        g.setColor(new Color(0xf3e6c9));
        g.fill(pal);
        g.setColor(new Color(0xb79d6b));
        g.draw(pal);
        Color[] cs = { new Color(0xef4444), new Color(0x3b82f6), new Color(0x22c55e), new Color(0xfacc15), new Color(0xa855f7) };
        double[][] pos = { { 9, 9 }, { 15, 7 }, { 21, 9 }, { 6.5, 15 }, { 8, 21 } };
        for (int i = 0; i < cs.length; i++) {
            g.setColor(cs[i]);
            g.fill(new Ellipse2D.Double(pos[i][0], pos[i][1], 4.5, 4.5));
        }
    }

    static void cmd(Graphics2D g) {
        g.setColor(new Color(0x1e1e1e));
        g.fill(rr(2, 5, 28, 22, 4));
        g.setColor(new Color(0x4a4a4a));
        g.fill(new Rectangle2D.Double(2, 8, 28, 0.8));
        g.setColor(new Color(0x666666));
        g.draw(rr(2, 5, 28, 22, 4));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        p.moveTo(7, 13); p.lineTo(11, 16.5); p.lineTo(7, 20);
        g.draw(p);
        g.draw(new Line2D.Double(13, 21, 19, 21));
    }

    static void gear(Graphics2D g) {
        Area a = new Area(new Ellipse2D.Double(7, 7, 18, 18));
        for (int i = 0; i < 8; i++) {
            AffineTransform t = AffineTransform.getRotateInstance(i * Math.PI / 4, 16, 16);
            a.add(new Area(t.createTransformedShape(rr(13.5, 2.5, 5, 7, 1.5))));
        }
        a.subtract(new Area(new Ellipse2D.Double(11.5, 11.5, 9, 9)));
        g.setPaint(new GradientPaint(0, 3, new Color(0x9aa5b5), 0, 29, new Color(0x5d6878)));
        g.fill(a);
    }

    static void taskmgr(Graphics2D g) {
        g.setColor(new Color(0x24303c));
        g.fill(rr(2, 4, 28, 24, 4));
        g.setColor(new Color(0x34495e));
        for (int i = 1; i < 4; i++) g.fill(new Rectangle2D.Double(3, 4 + i * 6, 26, 0.6));
        g.setColor(new Color(0x34d399));
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        p.moveTo(4, 22); p.lineTo(9, 18); p.lineTo(13, 21); p.lineTo(18, 10); p.lineTo(22, 16); p.lineTo(28, 8);
        g.draw(p);
    }

    static void mine(Graphics2D g) {
        g.setColor(new Color(0x2b2b2b));
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 4;
            g.draw(new Line2D.Double(16 - Math.cos(a) * 13, 17 - Math.sin(a) * 13, 16 + Math.cos(a) * 13, 17 + Math.sin(a) * 13));
        }
        g.fill(new Ellipse2D.Double(7, 8, 18, 18));
        g.setColor(new Color(255, 255, 255, 200));
        g.fill(new Ellipse2D.Double(11, 12, 5, 5));
    }

    static void about(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 2, new Color(0x60a5fa), 0, 30, new Color(0x2563eb)));
        g.fill(new Ellipse2D.Double(2, 2, 28, 28));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(14, 7.5, 4, 4));
        g.fill(rr(14, 13.5, 4, 11, 2));
    }

    static void trash(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 8, new Color(0xdfe7ef), 0, 30, new Color(0x9fb0c2)));
        Path2D b = new Path2D.Double();
        b.moveTo(6, 9); b.lineTo(26, 9); b.lineTo(24, 29); b.lineTo(8, 29); b.closePath();
        g.fill(b);
        g.setColor(new Color(0x6c7d90));
        g.draw(b);
        g.fill(rr(4, 6, 24, 3.5, 2));
        g.fill(rr(12, 3, 8, 3.5, 1.5));
        g.setStroke(new BasicStroke(1.2f));
        for (int i = 0; i < 3; i++) g.draw(new Line2D.Double(12 + i * 4, 13, 12 + i * 4 - (i - 1) * 0.6, 26));
    }

    // Логотип JavaWin: окно из четырёх стёкол, над ним — пар от чашки кофе.
    static void logo(Graphics2D g) {
        Color a = Theme.accent;
        Color light = Theme.mix(a, Color.WHITE, 0.45);
        double s = 12.2, gap = 1.6, ox = 3.5, oy = 6.8;
        for (int r = 0; r < 2; r++)
            for (int c = 0; c < 2; c++) {
                double x = ox + c * (s + gap), y = oy + r * (s + gap);
                g.setPaint(new GradientPaint((float) x, (float) y, light, (float) (x + s), (float) (y + s), a));
                g.fill(rr(x, y, s, s, 3.2));
            }
        g.setColor(Theme.mix(a, Color.WHITE, 0.7));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            double x = 9 + i * 7;
            Path2D p = new Path2D.Double();
            p.moveTo(x, 5.2); p.curveTo(x - 2, 3.8, x + 2, 2.6, x, 0.9);
            g.draw(p);
        }
    }

    static void user(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, Theme.mix(Theme.accent, Color.WHITE, 0.3), 0, 32, Theme.accent));
        g.fill(new Ellipse2D.Double(0, 0, 32, 32));
        g.setColor(new Color(255, 255, 255, 235));
        g.fill(new Ellipse2D.Double(11, 6.5, 10, 10));
        Shape body = new Ellipse2D.Double(6, 18.5, 20, 16);
        Area ar = new Area(body);
        ar.intersect(new Area(new Ellipse2D.Double(0, 0, 32, 32)));
        g.fill(ar);
    }

    static void power(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(6, 7, 20, 20, 120, 300, Arc2D.OPEN));
        g.draw(new Line2D.Double(16, 4, 16, 15));
    }

    static void search(Graphics2D g, Color c) {
        g.setColor(c);
        g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Double(6, 6, 15, 15));
        g.draw(new Line2D.Double(19, 19, 26, 26));
    }

    static void arrow(Graphics2D g, double rot) {
        g.rotate(rot, 16, 16);
        g.setColor(Theme.text());
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(7, 16, 25, 16));
        Path2D p = new Path2D.Double();
        p.moveTo(14, 9); p.lineTo(7, 16); p.lineTo(14, 23);
        g.draw(p);
    }

    static void refresh(Graphics2D g) {
        g.setColor(Theme.text());
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(7, 7, 18, 18, 60, 290, Arc2D.OPEN));
        Path2D p = new Path2D.Double();
        p.moveTo(18, 4.5); p.lineTo(23.5, 8.5); p.lineTo(18.5, 12.5);
        g.draw(p);
    }

    static void home(Graphics2D g) {
        g.setColor(Theme.text());
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D p = new Path2D.Double();
        p.moveTo(5, 15); p.lineTo(16, 5); p.lineTo(27, 15);
        g.draw(p);
        Path2D h = new Path2D.Double();
        h.moveTo(8, 13); h.lineTo(8, 27); h.lineTo(24, 27); h.lineTo(24, 13);
        g.draw(h);
        g.draw(new Rectangle2D.Double(13.5, 19, 5, 8));
    }

    // пингвин — значок программ Linux
    static void penguin(Graphics2D g) {
        g.setColor(new Color(0x1f2328));
        g.fill(new Ellipse2D.Double(7, 6, 18, 23));
        g.fill(new Ellipse2D.Double(10, 1.5, 12, 12));
        g.setColor(new Color(0xf5f5f0));
        g.fill(new Ellipse2D.Double(10.5, 11, 11, 16));
        g.fill(new Ellipse2D.Double(12, 5, 3.4, 4));
        g.fill(new Ellipse2D.Double(16.6, 5, 3.4, 4));
        g.setColor(Color.BLACK);
        g.fill(new Ellipse2D.Double(13.2, 6.2, 1.6, 2));
        g.fill(new Ellipse2D.Double(17.4, 6.2, 1.6, 2));
        g.setColor(new Color(0xf5a623));
        Path2D beak = new Path2D.Double();
        beak.moveTo(13.5, 9.5); beak.lineTo(18.5, 9.5); beak.lineTo(16, 12); beak.closePath();
        g.fill(beak);
        g.fill(new Ellipse2D.Double(7, 26.5, 8, 4));
        g.fill(new Ellipse2D.Double(17, 26.5, 8, 4));
    }

    // глобус в синем круге
    static void browser(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 2, new Color(0x38bdf8), 0, 30, new Color(0x1d4ed8)));
        g.fill(new Ellipse2D.Double(2, 2, 28, 28));
        g.setColor(new Color(255, 255, 255, 220));
        g.setStroke(new BasicStroke(1.6f));
        g.draw(new Ellipse2D.Double(5, 5, 22, 22));
        g.draw(new Ellipse2D.Double(11, 5, 10, 22));
        g.draw(new Line2D.Double(5, 16, 27, 16));
        g.draw(new Arc2D.Double(7, 3, 18, 12, 200, 140, Arc2D.OPEN));
        g.draw(new Arc2D.Double(7, 17, 18, 12, 20, 140, Arc2D.OPEN));
        g.draw(new Line2D.Double(16, 5, 16, 27));
    }

    static void plus(Graphics2D g) {
        g.setColor(Theme.text());
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Double(16, 7, 16, 25));
        g.draw(new Line2D.Double(7, 16, 25, 16));
    }

    static void wifi(Graphics2D g) {
        g.setColor(Theme.text());
        g.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 3; i++) {
            double r = 7 + i * 6;
            g.draw(new Arc2D.Double(16 - r, 25 - r, r * 2, r * 2, 45, 90, Arc2D.OPEN));
        }
        g.fill(new Ellipse2D.Double(14, 23, 4, 4));
    }

    static void sound(Graphics2D g) {
        g.setColor(Theme.text());
        Path2D p = new Path2D.Double();
        p.moveTo(4, 12); p.lineTo(9, 12); p.lineTo(16, 6); p.lineTo(16, 26); p.lineTo(9, 20); p.lineTo(4, 20); p.closePath();
        g.fill(p);
        g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(12, 10, 10, 12, -50, 100, Arc2D.OPEN));
        g.draw(new Arc2D.Double(11, 5, 17, 22, -50, 100, Arc2D.OPEN));
    }

    static void battery(Graphics2D g) {
        g.setColor(Theme.text());
        g.setStroke(new BasicStroke(2f));
        g.draw(rr(3, 10, 23, 12, 3));
        g.fill(rr(27, 13.5, 2.5, 5, 1));
        g.fill(rr(5.5, 12.5, 15, 7, 1.5));
    }
}
