package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.ArrayDeque;

public class Paint extends JPanel implements AppBody {
    static final String[][] TOOLS = {
        { "brush", "Кисть" }, { "eraser", "Ластик" }, { "line", "Линия" }, { "rect", "Прямоугольник" },
        { "oval", "Овал" }, { "fill", "Заливка" }, { "pick", "Пипетка" } };
    static final int[] PALETTE = {
        0x000000, 0x7f7f7f, 0x880015, 0xed1c24, 0xff7f27, 0xfff200, 0x22b14c, 0x00a2e8, 0x3f48cc, 0xa349a4,
        0xffffff, 0xc3c3c3, 0xb97a57, 0xffaec9, 0xffc90e, 0xefe4b0, 0xb5e61d, 0x99d9ea, 0x7092be, 0xc8bfe7 };

    final Desktop d;
    BufferedImage img;
    final ArrayDeque<BufferedImage> undo = new ArrayDeque<>();
    String tool = "brush";
    Color color = Color.BLACK;
    int size = 4;
    Path file;
    boolean dirty;
    Win win;
    final Canvas canvas = new Canvas();
    final JPanel toolRow;
    final Swatch current = new Swatch(null);

    public Paint(Desktop d, Path p) {
        super(new BorderLayout());
        this.d = d;
        img = blank(900, 520);
        if (p != null) {
            try {
                BufferedImage src = ImageIO.read(p.toFile());
                if (src != null) {
                    img = blank(src.getWidth(), src.getHeight());
                    Graphics2D g = img.createGraphics();
                    g.drawImage(src, 0, 0, null);
                    g.dispose();
                    file = p;
                }
            } catch (Exception e) { d.error(e); }
        }

        toolRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        for (String[] t : TOOLS) {
            UI.Btn b = UI.button(t[1], null);
            b.flat = true;
            b.setBorder(new EmptyBorder(6, 10, 6, 10));
            b.putClientProperty("tool", t[0]);
            b.addActionListener(e -> { tool = t[0]; syncTools(); });
            toolRow.add(b);
        }
        syncTools();

        JSlider sz = new JSlider(1, 40, size);
        sz.setOpaque(false);
        sz.setPreferredSize(new Dimension(120, 24));
        UI.Label szl = UI.dim(size + " px");
        sz.addChangeListener(e -> { size = sz.getValue(); szl.setText(size + " px"); });

        JPanel pal = new JPanel(new GridLayout(2, 10, 3, 3));
        pal.setOpaque(false);
        for (int c : PALETTE) pal.add(new Swatch(new Color(c)));

        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new EmptyBorder(6, 8, 8, 8));
        JPanel left = new JPanel();
        left.setLayout(new BoxLayout(left, BoxLayout.Y_AXIS));
        left.add(toolRow);
        left.add(Box.createVerticalStrut(6));
        left.add(UI.row(8, UI.dim("Толщина"), sz, szl, Box.createHorizontalStrut(16), current, pal));
        for (Component c : left.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        bar.add(left, BorderLayout.CENTER);
        bar.add(UI.row(6,
            UI.button("Отменить", this::undoLast),
            UI.button("Новый", () -> { push(); img = blank(img.getWidth(), img.getHeight()); file = null; canvas.sized(); }),
            UI.accent("Сохранить", this::save)), BorderLayout.EAST);
        add(bar, BorderLayout.NORTH);

        JScrollPane sc = UI.scroll(canvas);
        sc.getViewport().setBackground(new Color(0xc9ced6));
        add(sc, BorderLayout.CENTER);
        canvas.sized();

        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ctrl Z"), "undo");
        getActionMap().put("undo", new AbstractAction() { public void actionPerformed(ActionEvent e) { undoLast(); } });
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ctrl S"), "save");
        getActionMap().put("save", new AbstractAction() { public void actionPerformed(ActionEvent e) { save(); } });
    }

    static BufferedImage blank(int w, int h) {
        BufferedImage b = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = b.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, h);
        g.dispose();
        return b;
    }

    void syncTools() {
        for (Component c : toolRow.getComponents())
            if (c instanceof UI.Btn b) { b.toggled = tool.equals(b.getClientProperty("tool")); b.repaint(); }
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }

    @Override public void attached(Win w) { win = w; title(); }
    @Override public Component focusTarget() { return canvas; }

    void title() {
        if (win != null) win.setTitle((dirty ? "*" : "") + (file == null ? "Безымянный" : file.getFileName().toString()) + " — Paint");
    }

    void push() {
        BufferedImage copy = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(img, 0, 0, null);
        g.dispose();
        undo.push(copy);
        while (undo.size() > 30) undo.removeLast();
        if (!dirty) { dirty = true; title(); }
    }

    void undoLast() {
        if (undo.isEmpty()) return;
        img = undo.pop();
        canvas.sized();
    }

    void save() {
        if (file != null) { write(file); return; }
        d.prompt("Сохранить рисунок", "Имя файла (в папке «Рисунки»):", "Рисунок.png", s -> {
            Path p = Vfs.resolve(Vfs.pics(), s.toLowerCase().endsWith(".png") ? s : s + ".png");
            write(p);
        });
    }

    void write(Path p) {
        try {
            ImageIO.write(img, "png", p.toFile());
            file = p;
            dirty = false;
            title();
            d.toast("Paint", "Рисунок сохранён: " + Vfs.virt(p));
        } catch (Exception e) { d.error(e); }
    }

    @Override public boolean beforeClose(Runnable doClose) {
        if (!dirty) return false;
        d.ask("Paint", "Сохранить изменения в рисунке?", new String[] { "Сохранить", "Не сохранять", "Отмена" }, i -> {
            if (i == 0) { save(); if (!dirty) doClose.run(); }
            else if (i == 1) doClose.run();
        });
        return true;
    }

    // ---------------------------------------------------------------- холст

    class Canvas extends JComponent {
        Point start, last, now;

        Canvas() {
            setFocusable(true);
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    Point p = e.getPoint();
                    boolean alt = SwingUtilities.isRightMouseButton(e);
                    if (tool.equals("pick")) {
                        if (in(p)) { color = new Color(img.getRGB(p.x, p.y)); current.repaint(); }
                        return;
                    }
                    push();
                    if (tool.equals("fill")) {
                        if (in(p)) fill(p.x, p.y, (alt ? Color.WHITE : color).getRGB());
                        repaint();
                        return;
                    }
                    start = last = now = p;
                    if (tool.equals("brush") || tool.equals("eraser")) stroke(p, p);
                }

                public void mouseDragged(MouseEvent e) {
                    if (start == null) return;
                    now = e.getPoint();
                    if (tool.equals("brush") || tool.equals("eraser")) {
                        stroke(last, now);
                        last = now;
                    }
                    repaint();
                }

                public void mouseReleased(MouseEvent e) {
                    if (start == null) return;
                    now = e.getPoint();
                    if (!tool.equals("brush") && !tool.equals("eraser")) {
                        Graphics2D g = gfx();
                        shape(g, start, now);
                        g.dispose();
                    }
                    start = null;
                    repaint();
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        boolean in(Point p) { return p.x >= 0 && p.y >= 0 && p.x < img.getWidth() && p.y < img.getHeight(); }

        void sized() {
            setPreferredSize(new Dimension(img.getWidth() + 20, img.getHeight() + 20));
            revalidate();
            repaint();
        }

        Graphics2D gfx() {
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(tool.equals("eraser") ? Color.WHITE : color);
            g.setStroke(new BasicStroke(tool.equals("eraser") ? size * 3 : size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            return g;
        }

        void stroke(Point a, Point b) {
            Graphics2D g = gfx();
            g.draw(new Line2D.Double(a, b));
            g.dispose();
            repaint();
        }

        void shape(Graphics2D g, Point a, Point b) {
            int x = Math.min(a.x, b.x), y = Math.min(a.y, b.y), w = Math.abs(a.x - b.x), h = Math.abs(a.y - b.y);
            switch (tool) {
                case "line" -> g.draw(new Line2D.Double(a, b));
                case "rect" -> g.drawRect(x, y, w, h);
                case "oval" -> g.drawOval(x, y, w, h);
            }
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(new Color(0, 0, 0, 40));
            g.fillRect(3, 3, img.getWidth(), img.getHeight());
            g.drawImage(img, 0, 0, null);
            if (start != null && now != null && !tool.equals("brush") && !tool.equals("eraser")) {
                Graphics2D p = (Graphics2D) g.create();
                p.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                p.setColor(color);
                p.setStroke(new BasicStroke(size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                shape(p, start, now);
                p.dispose();
            }
            g.dispose();
        }
    }

    // заливка областью одного цвета (по строкам, без рекурсии)
    void fill(int x0, int y0, int rgb) {
        int w = img.getWidth(), h = img.getHeight();
        int target = img.getRGB(x0, y0);
        if (target == rgb) return;
        int[] px = img.getRGB(0, 0, w, h, null, 0, w);
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.push(new int[] { x0, y0 });
        while (!q.isEmpty()) {
            int[] s = q.pop();
            int x = s[0], y = s[1];
            while (x > 0 && px[y * w + x - 1] == target) x--;
            boolean up = false, down = false;
            for (; x < w && px[y * w + x] == target; x++) {
                px[y * w + x] = rgb;
                if (y > 0) {
                    boolean t = px[(y - 1) * w + x] == target;
                    if (t && !up) q.push(new int[] { x, y - 1 });
                    up = t;
                }
                if (y < h - 1) {
                    boolean t = px[(y + 1) * w + x] == target;
                    if (t && !down) q.push(new int[] { x, y + 1 });
                    down = t;
                }
            }
        }
        img.setRGB(0, 0, w, h, px, 0, w);
    }

    class Swatch extends JComponent {
        final Color c;   // null — «текущий цвет»

        Swatch(Color c) {
            this.c = c;
            setPreferredSize(c == null ? new Dimension(38, 38) : new Dimension(18, 18));
            setToolTipText(c == null ? "Текущий цвет" : null);
            if (c != null) {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                addMouseListener(new MouseAdapter() {
                    public void mousePressed(MouseEvent e) {
                        color = c;
                        if (tool.equals("eraser") || tool.equals("pick")) { tool = "brush"; syncTools(); }
                        current.repaint();
                    }
                });
            }
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            g.setColor(c == null ? color : c);
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g.setColor(Theme.border());
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            g.dispose();
        }
    }
}
