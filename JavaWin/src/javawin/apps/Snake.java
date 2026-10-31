package javawin.apps;

import javawin.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

// Змейка: стрелки или WASD, пробел — пауза, Enter или F2 — новая игра. Чем длиннее змейка, тем быстрее.
public class Snake extends JComponent implements AppBody {
    static final int W = 20, H = 15, C = 26, PAD = 14, HEAD = 56;

    final Deque<Point> body = new ArrayDeque<>();
    final Deque<Point> turns = new ArrayDeque<>();   // нажатия между шагами: две быстрые стрелки не теряются
    final Random rnd = new Random();
    Point dir = new Point(1, 0), food;
    int score, best;
    boolean over, paused, started;
    final Timer timer = new Timer(140, e -> step());

    public Snake() {
        setFocusable(true);
        best = loadBest();
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) { key(e.getKeyCode()); }
        });
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) { requestFocusInWindow(); if (over) reset(); }
        });
        reset();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(W * C + PAD * 2, HEAD + H * C + PAD); }
    @Override public void closing() { timer.stop(); }
    @Override public Component focusTarget() { return this; }

    void reset() {
        body.clear();
        turns.clear();
        for (int i = 0; i < 3; i++) body.addLast(new Point(W / 2 - i, H / 2));
        dir = new Point(1, 0);
        score = 0;
        over = paused = started = false;
        timer.setDelay(140);
        food();
        timer.stop();
        repaint();
    }

    void food() {
        do food = new Point(rnd.nextInt(W), rnd.nextInt(H)); while (body.contains(food));
    }

    void key(int k) {
        Point d = switch (k) {
            case KeyEvent.VK_UP, KeyEvent.VK_W -> new Point(0, -1);
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> new Point(0, 1);
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> new Point(-1, 0);
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> new Point(1, 0);
            default -> null;
        };
        if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_F2) { reset(); return; }
        if (k == KeyEvent.VK_SPACE && started && !over) { paused = !paused; repaint(); return; }
        if (d == null || over) return;
        Point last = turns.isEmpty() ? dir : turns.peekLast();
        if (d.x == -last.x && d.y == -last.y) return;   // развернуться в себя нельзя
        if (turns.size() < 3) turns.addLast(d);
        if (!started) { started = true; timer.start(); }
        paused = false;
    }

    void step() {
        if (paused || over) return;
        if (!turns.isEmpty()) dir = turns.pollFirst();
        Point h = body.peekFirst();
        Point n = new Point(h.x + dir.x, h.y + dir.y);
        boolean eat = n.equals(food);
        Point tail = eat ? null : body.pollLast();   // хвост уходит раньше, чем встаёт голова — в свой хвост въезжать можно
        if (n.x < 0 || n.y < 0 || n.x >= W || n.y >= H || body.contains(n)) {
            if (tail != null) body.addLast(tail);   // вернуть хвост на место для картинки
            over = true;
            timer.stop();
            if (score > best) { best = score; saveBest(best); }
            repaint();
            return;
        }
        body.addFirst(n);
        if (eat) {
            score++;
            timer.setDelay(Math.max(60, 140 - score * 3));
            if (body.size() == W * H) { over = true; timer.stop(); } else food();
        }
        repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        UI.aa(g);
        g.setColor(Theme.window());
        g.fillRect(0, 0, getWidth(), getHeight());

        g.setFont(Theme.font(Font.BOLD, 20f));
        g.setColor(Theme.text());
        g.drawString("Счёт: " + score, PAD, 36);
        String b = "Рекорд: " + Math.max(best, score);
        g.setColor(Theme.dim());
        g.drawString(b, getWidth() - PAD - g.getFontMetrics().stringWidth(b), 36);

        int fx = PAD, fy = HEAD;
        g.setColor(Theme.panel());
        g.fillRoundRect(fx, fy, W * C, H * C, 10, 10);
        g.setColor(new Color(128, 128, 128, 22));
        for (int x = 0; x < W; x++) for (int y = 0; y < H; y++) if ((x + y) % 2 == 0) g.fillRect(fx + x * C, fy + y * C, C, C);

        // яблоко
        int ax = fx + food.x * C, ay = fy + food.y * C;
        g.setColor(new Color(0xe5484d));
        g.fillOval(ax + 4, ay + 5, C - 8, C - 8);
        g.setColor(new Color(0x22a05a));
        g.fillOval(ax + C / 2, ay + 2, 7, 5);

        // змейка: голова темнее, к хвосту светлее
        int i = 0, n = body.size();
        for (Point p : body) {
            float k = n <= 1 ? 0 : i / (float) (n - 1);
            g.setColor(blend(Theme.accent, Color.WHITE, 0.45f * k));
            g.fillRoundRect(fx + p.x * C + 2, fy + p.y * C + 2, C - 4, C - 4, 9, 9);
            if (i == 0) {   // глаза смотрят туда, куда ползём
                g.setColor(Color.WHITE);
                int cx = fx + p.x * C + C / 2, cy = fy + p.y * C + C / 2;
                int ex = dir.y != 0 ? 5 : 0, ey = dir.x != 0 ? 5 : 0;
                int ox = dir.x * 4, oy = dir.y * 4;
                g.fillOval(cx + ox - ex - 3, cy + oy - ey - 3, 6, 6);
                g.fillOval(cx + ox + ex - 3, cy + oy + ey - 3, 6, 6);
            }
            i++;
        }

        String msg = null, sub = null;
        if (!started) { msg = "Змейка"; sub = "Нажми стрелку, чтобы начать"; }
        else if (over) { msg = body.size() == W * H ? "Победа!" : "Игра окончена"; sub = "Enter или щелчок — ещё раз"; }
        else if (paused) { msg = "Пауза"; sub = "Пробел — продолжить"; }
        if (msg != null) {
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(fx, fy, W * C, H * C, 10, 10);
            g.setColor(Color.WHITE);
            g.setFont(Theme.font(Font.BOLD, 30f));
            FontMetrics fm = g.getFontMetrics();
            g.drawString(msg, fx + (W * C - fm.stringWidth(msg)) / 2, fy + H * C / 2 - 6);
            g.setFont(Theme.font(Font.PLAIN, 15f));
            fm = g.getFontMetrics();
            g.drawString(sub, fx + (W * C - fm.stringWidth(sub)) / 2, fy + H * C / 2 + 22);
        }
        g.dispose();
    }

    static Color blend(Color a, Color b, float k) {
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * k),
                         Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * k),
                         Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * k));
    }

    // рекорд хранится рядом с настройками системы
    static int loadBest() {
        try { return Integer.parseInt(java.nio.file.Files.readString(Theme.base.resolve("snake.txt")).trim()); }
        catch (Exception e) { return 0; }
    }

    static void saveBest(int v) {
        try { java.nio.file.Files.writeString(Theme.base.resolve("snake.txt"), String.valueOf(v)); }
        catch (Exception ignored) {}
    }
}
