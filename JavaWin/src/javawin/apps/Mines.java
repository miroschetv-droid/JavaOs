package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Random;

public class Mines extends JComponent implements AppBody {
    static final int N = 9, MINES = 10, C = 32, PAD = 14, HEAD = 64;
    static final Color[] NUM = { null, new Color(0x3b82f6), new Color(0x22a05a), new Color(0xe5484d), new Color(0x7c3aed),
        new Color(0xb45309), new Color(0x0e9aa7), new Color(0x333333), new Color(0x888888) };

    final int[] mine = new int[N * N];      // -1 мина, иначе число мин вокруг
    final int[] state = new int[N * N];     // 0 закрыта, 1 открыта, 2 флаг
    boolean first = true, over, won;
    int boom = -1, hover = -1;
    long t0, elapsed;
    final Timer timer = new Timer(250, e -> { if (!first && !over) { elapsed = (System.currentTimeMillis() - t0) / 1000; repaint(); } });

    public Mines() {
        setFocusable(true);
        MouseAdapter m = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (face(e.getPoint())) { reset(); return; }
                int i = cell(e.getPoint());
                if (i < 0 || over) return;
                if (SwingUtilities.isRightMouseButton(e)) {
                    if (state[i] != 1) state[i] = state[i] == 2 ? 0 : 2;
                } else if (SwingUtilities.isMiddleMouseButton(e) || (state[i] == 1)) chord(i);
                else if (state[i] == 0) open(i);
                repaint();
            }

            public void mouseMoved(MouseEvent e) {
                int i = cell(e.getPoint());
                if (i != hover) { hover = i; repaint(); }
            }

            public void mouseExited(MouseEvent e) { hover = -1; repaint(); }
        };
        addMouseListener(m);
        addMouseMotionListener(m);
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("F2"), "new");
        getActionMap().put("new", new AbstractAction() { public void actionPerformed(ActionEvent e) { reset(); } });
        timer.start();
    }

    @Override public Dimension getPreferredSize() { return new Dimension(N * C + PAD * 2, HEAD + N * C + PAD); }
    @Override public void closing() { timer.stop(); }
    @Override public Component focusTarget() { return this; }

    void reset() {
        java.util.Arrays.fill(mine, 0);
        java.util.Arrays.fill(state, 0);
        first = true;
        over = won = false;
        boom = -1;
        elapsed = 0;
        repaint();
    }

    int cell(Point p) {
        int x = (p.x - PAD) / C, y = (p.y - HEAD) / C;
        if (p.x < PAD || p.y < HEAD || x >= N || y >= N) return -1;
        return y * N + x;
    }

    boolean face(Point p) { return Math.abs(p.x - getWidth() / 2) < 18 && Math.abs(p.y - HEAD / 2) < 18; }

    void place(int safe) {
        Random r = new Random();
        int sx = safe % N, sy = safe / N, n = 0;
        while (n < MINES) {
            int i = r.nextInt(N * N);
            if (mine[i] == -1 || (Math.abs(i % N - sx) <= 1 && Math.abs(i / N - sy) <= 1)) continue;
            mine[i] = -1;
            n++;
        }
        for (int i = 0; i < N * N; i++) {
            if (mine[i] == -1) continue;
            int c = 0;
            for (int j : around(i)) if (mine[j] == -1) c++;
            mine[i] = c;
        }
        first = false;
        t0 = System.currentTimeMillis();
    }

    int[] around(int i) {
        int x = i % N, y = i / N, k = 0;
        int[] tmp = new int[8];
        for (int dy = -1; dy <= 1; dy++)
            for (int dx = -1; dx <= 1; dx++) {
                int nx = x + dx, ny = y + dy;
                if ((dx != 0 || dy != 0) && nx >= 0 && ny >= 0 && nx < N && ny < N) tmp[k++] = ny * N + nx;
            }
        return java.util.Arrays.copyOf(tmp, k);
    }

    void open(int i) {
        if (first) place(i);
        if (state[i] != 0) return;
        if (mine[i] == -1) {
            boom = i;
            over = true;
            for (int j = 0; j < N * N; j++) if (mine[j] == -1 && state[j] != 2) state[j] = 1;
            return;
        }
        java.util.ArrayDeque<Integer> q = new java.util.ArrayDeque<>();
        q.add(i);
        while (!q.isEmpty()) {
            int c = q.poll();
            if (state[c] != 0) continue;
            state[c] = 1;
            if (mine[c] == 0) for (int j : around(c)) if (state[j] == 0) q.add(j);
        }
        int closed = 0;
        for (int j = 0; j < N * N; j++) if (state[j] != 1) closed++;
        if (closed == MINES) {
            over = won = true;
            for (int j = 0; j < N * N; j++) if (mine[j] == -1) state[j] = 2;
        }
    }

    void chord(int i) {
        if (state[i] != 1 || mine[i] <= 0) return;
        int flags = 0;
        for (int j : around(i)) if (state[j] == 2) flags++;
        if (flags == mine[i]) for (int j : around(i)) if (state[j] == 0) open(j);
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        UI.aa(g);
        g.setColor(Theme.window());
        g.fillRect(0, 0, getWidth(), getHeight());
        int flags = 0;
        for (int s : state) if (s == 2) flags++;
        g.setFont(Theme.mono(22f).deriveFont(Font.BOLD));
        g.setColor(new Color(0xe5484d));
        g.drawString(String.format("%03d", Math.max(0, MINES - flags)), PAD, HEAD / 2 + 8);
        String t = String.format("%03d", Math.min(999, elapsed));
        g.drawString(t, getWidth() - PAD - g.getFontMetrics().stringWidth(t), HEAD / 2 + 8);
        drawFace(g, getWidth() / 2, HEAD / 2);

        g.setFont(Theme.font(Font.BOLD, 17f));
        FontMetrics fm = g.getFontMetrics();
        for (int i = 0; i < N * N; i++) {
            int x = PAD + (i % N) * C, y = HEAD + (i / N) * C;
            if (state[i] == 1) {
                g.setColor(i == boom ? new Color(0xe5484d) : Theme.mix(Theme.window(), Theme.text(), 0.06));
                g.fillRoundRect(x + 1, y + 1, C - 2, C - 2, 4, 4);
                if (mine[i] == -1) Icons.draw(g, "mine", x + 5, y + 5, C - 10);
                else if (mine[i] > 0) {
                    g.setColor(Theme.dark ? NUM[mine[i]].brighter() : NUM[mine[i]]);
                    String s = String.valueOf(mine[i]);
                    g.drawString(s, x + (C - fm.stringWidth(s)) / 2, y + (C + fm.getAscent() - fm.getDescent()) / 2);
                }
            } else {
                Color base = Theme.mix(Theme.control(), Theme.accent, 0.25);
                g.setColor(i == hover && !over ? Theme.mix(base, Color.WHITE, 0.15) : base);
                g.fillRoundRect(x + 1, y + 1, C - 2, C - 2, 6, 6);
                if (state[i] == 2) {
                    boolean wrong = over && !won && mine[i] != -1;
                    g.setColor(new Color(0x444444));
                    g.fillRect(x + 12, y + 8, 2, 16);
                    g.setColor(wrong ? Color.GRAY : new Color(0xe5484d));
                    g.fillPolygon(new int[] { x + 14, x + 24, x + 14 }, new int[] { y + 7, y + 12, y + 17 }, 3);
                }
            }
        }
        g.dispose();
    }

    void drawFace(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(0xffd34d));
        g.fillOval(cx - 16, cy - 16, 32, 32);
        g.setColor(new Color(0x553300));
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        if (over && !won) {
            g.drawLine(cx - 9, cy - 7, cx - 3, cy - 1);
            g.drawLine(cx - 3, cy - 7, cx - 9, cy - 1);
            g.drawLine(cx + 3, cy - 7, cx + 9, cy - 1);
            g.drawLine(cx + 9, cy - 7, cx + 3, cy - 1);
            g.drawArc(cx - 7, cy + 4, 14, 10, 0, 180);
        } else if (won) {
            g.fillRoundRect(cx - 11, cy - 7, 9, 6, 3, 3);
            g.fillRoundRect(cx + 2, cy - 7, 9, 6, 3, 3);
            g.drawLine(cx - 2, cy - 5, cx + 2, cy - 5);
            g.drawArc(cx - 8, cy - 2, 16, 12, 200, 140);
        } else {
            g.fillOval(cx - 7, cy - 6, 4, 4);
            g.fillOval(cx + 3, cy - 6, 4, 4);
            g.drawArc(cx - 8, cy - 4, 16, 13, 200, 140);
        }
    }
}
