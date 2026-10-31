package javawin.apps;

import javawin.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Files;
import java.util.Random;

// 2048: стрелки или WASD двигают все плитки, одинаковые складываются. Собери 2048! Enter или F2 — новая игра.
public class Game2048 extends JComponent implements AppBody {
    static final int N = 4, C = 96, GAP = 12, PAD = 18, HEAD = 64;

    final int[][] b = new int[N][N];
    final Random rnd = new Random();
    int score, best;
    boolean over, won, keepGoing;

    public Game2048() {
        setFocusable(true);
        best = loadBest();
        addKeyListener(new KeyAdapter() {
            public void keyPressed(KeyEvent e) { key(e.getKeyCode()); }
        });
        addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (over) reset();
                else if (won && !keepGoing) { keepGoing = true; repaint(); }
            }
        });
        reset();
    }

    @Override public Dimension getPreferredSize() {
        int s = N * C + (N + 1) * GAP;
        return new Dimension(s + PAD * 2, HEAD + s + PAD);
    }
    @Override public Component focusTarget() { return this; }

    void reset() {
        for (int[] r : b) java.util.Arrays.fill(r, 0);
        score = 0;
        over = won = keepGoing = false;
        add();
        add();
        repaint();
    }

    void add() {
        int free = 0;
        for (int[] r : b) for (int v : r) if (v == 0) free++;
        if (free == 0) return;
        int k = rnd.nextInt(free);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++)
            if (b[y][x] == 0 && k-- == 0) b[y][x] = rnd.nextInt(10) == 0 ? 4 : 2;
    }

    void key(int k) {
        if (k == KeyEvent.VK_ENTER || k == KeyEvent.VK_F2) { reset(); return; }
        if (over || (won && !keepGoing)) return;
        int dir = switch (k) {
            case KeyEvent.VK_LEFT, KeyEvent.VK_A -> 0;
            case KeyEvent.VK_UP, KeyEvent.VK_W -> 3;
            case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> 2;
            case KeyEvent.VK_DOWN, KeyEvent.VK_S -> 1;
            default -> -1;
        };
        if (dir < 0 || !move(dir)) return;
        add();
        if (score > best) { best = score; saveBest(best); }
        if (!canMove()) over = true;
        repaint();
    }

    // сдвиг влево по повёрнутой доске: dir — сколько раз повернуть доску перед сдвигом
    boolean move(int dir) {
        boolean moved = false;
        for (int i = 0; i < dir; i++) rotate();
        for (int y = 0; y < N; y++) {
            int[] row = new int[N];
            int n = 0, last = 0;
            for (int x = 0; x < N; x++) {
                int v = b[y][x];
                if (v == 0) continue;
                if (last == v) {
                    row[n - 1] = v * 2;
                    score += v * 2;
                    if (v * 2 == 2048) won = true;
                    last = 0;
                } else { row[n++] = v; last = v; }
            }
            for (int x = 0; x < N; x++) {
                if (b[y][x] != row[x]) moved = true;
                b[y][x] = row[x];
            }
        }
        for (int i = 0; i < (4 - dir) % 4; i++) rotate();
        return moved;
    }

    void rotate() {   // по часовой стрелке
        int[][] t = new int[N][N];
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) t[x][N - 1 - y] = b[y][x];
        for (int y = 0; y < N; y++) b[y] = t[y];
    }

    boolean canMove() {
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            if (b[y][x] == 0) return true;
            if (x + 1 < N && b[y][x] == b[y][x + 1]) return true;
            if (y + 1 < N && b[y][x] == b[y + 1][x]) return true;
        }
        return false;
    }

    static Color tile(int v) {
        return switch (v) {
            case 2 -> new Color(0xeee4da); case 4 -> new Color(0xede0c8); case 8 -> new Color(0xf2b179);
            case 16 -> new Color(0xf59563); case 32 -> new Color(0xf67c5f); case 64 -> new Color(0xf65e3b);
            case 128 -> new Color(0xedcf72); case 256 -> new Color(0xedcc61); case 512 -> new Color(0xedc850);
            case 1024 -> new Color(0xedc53f); case 2048 -> new Color(0xedc22e); default -> new Color(0x3c3a32);
        };
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        UI.aa(g);
        g.setColor(Theme.window());
        g.fillRect(0, 0, getWidth(), getHeight());
        int s = N * C + (N + 1) * GAP;
        int ox = (getWidth() - s) / 2, oy = HEAD;
        g.setFont(Theme.font(Font.BOLD, 20f));
        g.setColor(Theme.text());
        g.drawString("Очки: " + score, ox, 38);
        String bs = "Рекорд: " + best;
        g.setColor(Theme.dim());
        g.drawString(bs, ox + s - g.getFontMetrics().stringWidth(bs), 38);

        g.setColor(new Color(0xbbada0));
        g.fillRoundRect(ox, oy, s, s, 14, 14);
        for (int y = 0; y < N; y++) for (int x = 0; x < N; x++) {
            int v = b[y][x], tx = ox + GAP + x * (C + GAP), ty = oy + GAP + y * (C + GAP);
            g.setColor(v == 0 ? new Color(0xcdc1b4) : tile(v));
            g.fillRoundRect(tx, ty, C, C, 10, 10);
            if (v == 0) continue;
            String t = String.valueOf(v);
            g.setFont(Theme.font(Font.BOLD, t.length() <= 2 ? 40f : t.length() == 3 ? 32f : 26f));
            g.setColor(v <= 4 ? new Color(0x776e65) : Color.WHITE);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(t, tx + (C - fm.stringWidth(t)) / 2, ty + (C + fm.getAscent() - fm.getDescent()) / 2);
        }
        if (over || (won && !keepGoing)) {
            g.setColor(new Color(0, 0, 0, 120));
            g.fillRoundRect(ox, oy, s, s, 14, 14);
            g.setColor(Color.WHITE);
            g.setFont(Theme.font(Font.BOLD, 34f));
            String t = over ? "Игра окончена" : "Ты собрал 2048!";
            g.drawString(t, ox + (s - g.getFontMetrics().stringWidth(t)) / 2, oy + s / 2);
            g.setFont(Theme.font(Font.PLAIN, 16f));
            String h = over ? "Щёлкни или нажми Enter — новая игра" : "Щёлкни — играть дальше, Enter — заново";
            g.drawString(h, ox + (s - g.getFontMetrics().stringWidth(h)) / 2, oy + s / 2 + 34);
        }
        g.dispose();
    }

    static int loadBest() {
        try { return Integer.parseInt(Files.readString(Theme.base.resolve("2048.txt")).trim()); }
        catch (Exception e) { return 0; }
    }

    static void saveBest(int v) {
        try { Files.writeString(Theme.base.resolve("2048.txt"), String.valueOf(v)); } catch (Exception ignored) {}
    }
}
