package javawin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;

// Окно программы. Вокруг него прозрачное поле M пикселей: там рисуется тень
// и за него окно тянут, меняя размер.
public class Win extends JPanel {
    public static final int TB = 34, M = 10, R = 10;
    static final int MIN_W = 240, MIN_H = 150;

    final Desktop desk;
    final String appId, icon;
    final JComponent body;
    final boolean dialog;
    final TitleBar bar = new TitleBar();
    String title;
    boolean maximized, minimized;
    Rectangle normal;

    Win(Desktop desk, String appId, String title, String icon, JComponent body, boolean dialog) {
        super(new BorderLayout());
        this.desk = desk;
        this.appId = appId;
        this.title = title;
        this.icon = icon;
        this.body = body;
        this.dialog = dialog;
        setOpaque(false);
        setBorder(new EmptyBorder(M, M, M, M));
        add(bar, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        Resizer rs = new Resizer();
        addMouseListener(rs);
        addMouseMotionListener(rs);
    }

    public void setTitle(String t) {
        title = t;
        bar.repaint();
        desk.repaintBars();
    }

    public String getTitle() { return title; }
    public String appId() { return appId; }
    public boolean isActive() { return desk.active == this; }
    public void close() {
        if (body instanceof AppBody a && a.beforeClose(() -> desk.closeWin(this))) return;
        desk.closeWin(this);
    }

    Rectangle inner() {
        Insets in = getInsets();
        return new Rectangle(in.left, in.top, getWidth() - in.left - in.right, getHeight() - in.top - in.bottom);
    }

    void toggleMax() {
        if (dialog) return;
        if (maximized) {
            maximized = false;
            setBorder(new EmptyBorder(M, M, M, M));
            setBounds(normal);
        } else {
            normal = getBounds();
            maximized = true;
            setBorder(new EmptyBorder(0, 0, 0, 0));
            setBounds(desk.workArea(this));   // на том мониторе, где стоит окно
        }
        revalidate();
        repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        UI.aa(g);
        Rectangle r = inner();
        if (!maximized) {
            // тень — кольца по краю (а не заливки на всю площадь окна: так в разы меньше пикселей)
            g.setStroke(new BasicStroke(1f));
            int sum = 0;
            for (int i = M; i > 0; i--) {
                int a = (int) (isActive() ? 26.0 * (1 - i / (double) M) * (1 - i / (double) M) + 1 : 12.0 * (1 - i / (double) M) + 1);
                sum += a;
                g.setColor(new Color(0, 0, 0, Math.min(255, sum)));
                g.draw(new RoundRectangle2D.Double(r.x - i + 0.5, r.y - i + 3.5, r.width + i * 2 - 1, r.height + i * 2 - 1, R + i * 2, R + i * 2));
            }
        }
        g.setColor(Theme.window());
        if (maximized) g.fillRect(r.x, r.y, r.width, r.height);
        else g.fill(new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, R, R));
        g.dispose();
    }

    @Override protected void paintChildren(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        Rectangle r = inner();
        // скруглённая обрезка уводит всю отрисовку содержимого на медленный путь Java2D;
        // в JavaOS (без видеоускорения) обходимся прямоугольной
        if (!maximized) g.clip(Main.osMode ? r : new RoundRectangle2D.Double(r.x, r.y, r.width, r.height, R, R));
        super.paintChildren(g);
        g.dispose();
        if (!maximized) {
            Graphics2D o = (Graphics2D) g0.create();
            UI.aa(o);
            o.setColor(isActive() ? Theme.mix(Theme.border(), Theme.accent, 0.25) : Theme.border());
            o.draw(new RoundRectangle2D.Double(r.x - 0.5, r.y - 0.5, r.width, r.height, R, R));
            o.dispose();
        }
    }

    // ---------------------------------------------------------------- заголовок

    class TitleBar extends JComponent {
        static final int BW = 46;
        int hot = -1;           // 0 свернуть, 1 развернуть, 2 закрыть
        Point grab;
        boolean dragged;

        TitleBar() {
            setPreferredSize(new Dimension(10, TB));
            MouseAdapter ma = new MouseAdapter() {
                public void mouseMoved(MouseEvent e) { setHot(btnAt(e.getX())); }
                public void mouseExited(MouseEvent e) { setHot(-1); }

                public void mousePressed(MouseEvent e) {
                    if (btnAt(e.getX()) >= 0 || !SwingUtilities.isLeftMouseButton(e)) return;
                    grab = e.getPoint();
                    dragged = false;
                }

                public void mouseReleased(MouseEvent e) {
                    int b = btnAt(e.getX());
                    if (b >= 0 && b == hot && contains(e.getPoint())) {
                        if (b == 0) desk.minimize(Win.this);
                        else if (b == 1) toggleMax();
                        else close();
                        setHot(-1);
                        return;
                    }
                    if (dragged && !maximized && !dialog) {
                        Point p = SwingUtilities.convertPoint(TitleBar.this, e.getPoint(), desk.layer);
                        if (p.y <= 2) toggleMax();
                    }
                    grab = null;
                }

                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && btnAt(e.getX()) < 0 && SwingUtilities.isLeftMouseButton(e)) toggleMax();
                }

                public void mouseDragged(MouseEvent e) {
                    if (grab == null) return;
                    dragged = true;
                    Point p = SwingUtilities.convertPoint(TitleBar.this, e.getPoint(), desk.layer);
                    if (maximized) {
                        // «отлепить» развёрнутое окно, сохранив положение курсора относительно ширины
                        double k = grab.x / (double) getWidth();
                        toggleMax();
                        grab = new Point((int) (k * bar.getWidth()), grab.y);
                    }
                    int x = p.x - grab.x - M, y = Math.max(-M, p.y - grab.y - M);
                    y = Math.min(y, desk.layer.getHeight() - Taskbar.H - TB);
                    Win.this.setLocation(x, y);   // двигаем окно, а не полоску заголовка внутри него
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        void setHot(int h) {
            if (h != hot) { hot = h; repaint(); }
        }

        int buttons() { return dialog ? 1 : 3; }

        int btnAt(int x) {
            int n = buttons(), first = getWidth() - n * BW;
            if (x < first) return -1;
            int i = (x - first) / BW;
            return dialog ? 2 : i;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            int w = getWidth();
            Icons.draw(g, icon, 12, (TB - 16) / 2, 16);
            g.setFont(Theme.font(Font.PLAIN, 12.5f));
            g.setColor(isActive() ? Theme.text() : Theme.dim());
            FontMetrics fm = g.getFontMetrics();
            UI.text(g, title, 36, (TB + fm.getAscent() - fm.getDescent()) / 2, w - 36 - buttons() * BW - 8);

            int n = buttons();
            for (int i = 0; i < n; i++) {
                int kind = dialog ? 2 : i;
                int x = w - (n - i) * BW;
                if (hot == kind) {
                    g.setColor(kind == 2 ? new Color(0xc42b1c) : Theme.hover());
                    g.fillRect(x, 0, BW, TB);
                }
                g.setColor(hot == kind && kind == 2 ? Color.WHITE : isActive() ? Theme.text() : Theme.dim());
                g.setStroke(new BasicStroke(1f));
                int cx = x + BW / 2, cy = TB / 2;
                switch (kind) {
                    case 0 -> g.drawLine(cx - 5, cy, cx + 5, cy);
                    case 1 -> {
                        if (maximized) {
                            g.drawRoundRect(cx - 5, cy - 3, 8, 8, 2, 2);
                            g.drawLine(cx - 2, cy - 5, cx + 4, cy - 5);
                            g.drawLine(cx + 5, cy - 4, cx + 5, cy + 2);
                        } else g.drawRoundRect(cx - 5, cy - 5, 10, 10, 2, 2);
                    }
                    default -> {
                        g.drawLine(cx - 5, cy - 5, cx + 5, cy + 5);
                        g.drawLine(cx + 5, cy - 5, cx - 5, cy + 5);
                    }
                }
            }
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- изменение размера за края

    class Resizer extends MouseAdapter {
        int zone;               // биты: 1 лево, 2 право, 4 верх, 8 низ
        Point start;
        Rectangle startB;

        int zoneAt(Point p) {
            if (maximized || dialog) return 0;
            Rectangle r = inner();
            int t = 7, z = 0;
            if (p.x < r.x + t) z |= 1;
            if (p.x > r.x + r.width - t) z |= 2;
            if (p.y < r.y + t) z |= 4;
            if (p.y > r.y + r.height - t) z |= 8;
            return z;
        }

        public void mouseMoved(MouseEvent e) {
            int z = zoneAt(e.getPoint());
            int c = switch (z) {
                case 1 -> Cursor.W_RESIZE_CURSOR;
                case 2 -> Cursor.E_RESIZE_CURSOR;
                case 4 -> Cursor.N_RESIZE_CURSOR;
                case 8 -> Cursor.S_RESIZE_CURSOR;
                case 5 -> Cursor.NW_RESIZE_CURSOR;
                case 6 -> Cursor.NE_RESIZE_CURSOR;
                case 9 -> Cursor.SW_RESIZE_CURSOR;
                case 10 -> Cursor.SE_RESIZE_CURSOR;
                default -> Cursor.DEFAULT_CURSOR;
            };
            setCursor(Cursor.getPredefinedCursor(c));
        }

        public void mousePressed(MouseEvent e) {
            zone = zoneAt(e.getPoint());
            start = SwingUtilities.convertPoint(Win.this, e.getPoint(), desk.layer);
            startB = getBounds();
        }

        public void mouseReleased(MouseEvent e) { zone = 0; }

        public void mouseDragged(MouseEvent e) {
            if (zone == 0) return;
            Point p = SwingUtilities.convertPoint(Win.this, e.getPoint(), desk.layer);
            int dx = p.x - start.x, dy = p.y - start.y;
            Rectangle b = new Rectangle(startB);
            int minW = MIN_W + 2 * M, minH = MIN_H + 2 * M;
            if ((zone & 1) != 0) { int nw = Math.max(minW, b.width - dx); b.x += b.width - nw; b.width = nw; }
            if ((zone & 2) != 0) b.width = Math.max(minW, b.width + dx);
            if ((zone & 4) != 0) { int nh = Math.max(minH, b.height - dy); b.y += b.height - nh; b.height = nh; }
            if ((zone & 8) != 0) b.height = Math.max(minH, b.height + dy);
            setBounds(b);
            revalidate();
        }
    }
}
