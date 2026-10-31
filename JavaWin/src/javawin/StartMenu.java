package javawin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

// Меню «Пуск»: поиск, все программы, недавние файлы, учётная запись и выключение.
public class StartMenu extends JPanel {
    static final int W = 620, H = 640;

    final Desktop d;
    final JTextField search = UI.field("");
    final JPanel grid = new JPanel(new GridLayout(0, 6, 4, 4));
    final JPanel lower = new JPanel(new GridLayout(0, 2, 6, 4));
    final UI.Label lowerTitle = UI.title("Рекомендуем", 14f);
    final List<Tile> tiles = new ArrayList<>();
    int targetY;
    Timer anim;

    StartMenu(Desktop d) {
        super(new BorderLayout());
        this.d = d;
        setOpaque(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.setBorder(new EmptyBorder(26, 32, 14, 32));
        search.putClientProperty("hint", "Поиск программ и файлов");
        search.setPreferredSize(new Dimension(10, 36));
        search.setBorder(BorderFactory.createCompoundBorder(search.getBorder(), new EmptyBorder(0, 24, 0, 0)));
        top.add(new HintField(search));
        add(top, BorderLayout.NORTH);

        JPanel mid = new JPanel();
        mid.setOpaque(false);
        mid.setLayout(new BoxLayout(mid, BoxLayout.Y_AXIS));
        mid.setBorder(new EmptyBorder(4, 32, 10, 32));
        mid.add(left(UI.title("Все программы", 14f)));
        mid.add(Box.createVerticalStrut(10));
        grid.setOpaque(false);
        for (Apps.App a : Apps.ALL) {
            Tile t = new Tile(a.title(), a.icon(), () -> d.open(a.id()));
            t.keywords = (a.title() + " " + a.id()).toLowerCase(Locale.ROOT);
            tiles.add(t);
            grid.add(t);
        }
        mid.add(left(grid));
        mid.add(Box.createVerticalStrut(18));
        mid.add(left(lowerTitle));
        mid.add(Box.createVerticalStrut(8));
        lower.setOpaque(false);
        mid.add(left(lower));
        mid.add(Box.createVerticalGlue());
        add(mid, BorderLayout.CENTER);
        add(new Footer(), BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });
        search.addActionListener(e -> {
            for (Component c : grid.getComponents()) if (c.isVisible()) { ((Tile) c).fire(); return; }
            for (Component c : lower.getComponents()) if (c instanceof Tile t) { t.fire(); return; }
        });
        search.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        search.getActionMap().put("close", new AbstractAction() { public void actionPerformed(ActionEvent e) { close(); } });
        addMouseListener(new MouseAdapter() {});   // клики по фону меню не уходят на рабочий стол
    }

    static JComponent left(JComponent c) {
        c.setAlignmentX(LEFT_ALIGNMENT);
        if (c instanceof JPanel) c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 400));
        return c;
    }

    void place(Rectangle p) {
        targetY = p.y + Math.max(0, p.height - Taskbar.H - H - 12);
        setBounds(p.x + (p.width - W) / 2, targetY, W, Math.min(H, p.height - Taskbar.H - 12));
    }

    void open() {
        search.setText("");
        filter();
        setVisible(true);
        validate();
        if (Main.osMode) {          // без видеоускорения анимация выезда только тормозит
            setLocation(getX(), targetY);
            d.repaintBars();
            SwingUtilities.invokeLater(search::requestFocusInWindow);
            return;
        }
        setLocation(getX(), targetY + 60);
        long t0 = System.currentTimeMillis();
        if (anim != null) anim.stop();
        anim = new Timer(10, e -> {
            double k = Math.min(1, (System.currentTimeMillis() - t0) / 180.0);
            double ease = 1 - Math.pow(1 - k, 3);
            setLocation(getX(), (int) (targetY + 60 * (1 - ease)));
            if (k >= 1) anim.stop();
        });
        anim.start();
        d.repaintBars();
        SwingUtilities.invokeLater(search::requestFocusInWindow);
    }

    void close() {
        if (!isVisible()) return;
        if (anim != null) anim.stop();
        setVisible(false);
        d.repaintBars();
    }

    void filter() {
        String q = search.getText().trim().toLowerCase(Locale.ROOT);
        for (Tile t : tiles) t.setVisible(q.isEmpty() || t.keywords.contains(q));
        lower.removeAll();
        if (q.isEmpty()) {
            lowerTitle.setText("Рекомендуем");
            List<Path> rec = new ArrayList<>(d.recent);
            if (rec.isEmpty()) rec.addAll(Vfs.list(Vfs.desktop()));
            for (Path p : rec.subList(0, Math.min(6, rec.size()))) lower.add(fileTile(p));
        } else {
            lowerTitle.setText("Файлы");
            try (Stream<Path> s = Files.walk(Vfs.root(), 6)) {
                s.filter(p -> !p.equals(Vfs.root()) && p.getFileName().toString().toLowerCase(Locale.ROOT).contains(q))
                    .limit(6).forEach(p -> lower.add(fileTile(p)));
            } catch (IOException ignored) {}
            if (lower.getComponentCount() == 0) lower.add(UI.dim("Ничего не найдено"));
        }
        revalidate();
        repaint();
    }

    Tile fileTile(Path p) {
        Tile t = new Tile(p.getFileName().toString(), Vfs.kind(p), () -> d.openFile(p));
        t.sub = Vfs.virt(p.getParent());
        t.wide = true;
        t.setPreferredSize(new Dimension(260, 48));
        return t;
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        UI.aa(g);
        g.setColor(Theme.menu());
        g.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
        g.setColor(Theme.border());
        g.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 14, 14));
        g.dispose();
    }

    // поле поиска с лупой и подсказкой
    class HintField extends JLayeredPane {
        HintField(JTextField f) {
            setPreferredSize(f.getPreferredSize());
            setLayout(null);
            add(f, Integer.valueOf(0));
            JComponent over = new JComponent() {
                protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    UI.aa(g);
                    Icons.draw(g, "search", 10, (getHeight() - 16) / 2, 16);
                    if (f.getText().isEmpty()) {
                        g.setColor(Theme.dim());
                        g.setFont(Theme.font(Font.PLAIN, 13f));
                        g.drawString((String) f.getClientProperty("hint"), 36, getHeight() / 2 + 5);
                    }
                    g.dispose();
                }
                public boolean contains(int x, int y) { return false; }
            };
            add(over, Integer.valueOf(1));
            f.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) { over.repaint(); }
                public void removeUpdate(DocumentEvent e) { over.repaint(); }
                public void changedUpdate(DocumentEvent e) { over.repaint(); }
            });
        }

        @Override public void doLayout() {
            for (Component c : getComponents()) c.setBounds(0, 0, getWidth(), getHeight());
        }
    }

    class Tile extends JComponent {
        final String name, icon;
        final Runnable action;
        String keywords = "", sub;
        boolean hover, wide;

        Tile(String name, String icon, Runnable action) {
            this.name = name;
            this.icon = icon;
            this.action = action;
            setPreferredSize(new Dimension(88, 84));
            setToolTipText(name);
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseReleased(MouseEvent e) { if (contains(e.getPoint())) fire(); }
            });
        }

        void fire() {
            close();
            action.run();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            if (hover) {
                g.setColor(Theme.dark ? new Color(255, 255, 255, 18) : new Color(0, 0, 0, 14));
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
            }
            if (wide) {
                Icons.draw(g, icon, 10, (getHeight() - 28) / 2, 28);
                g.setFont(Theme.font(Font.PLAIN, 12.5f));
                g.setColor(Theme.text());
                UI.text(g, name, 48, 21, getWidth() - 56);
                g.setFont(Theme.font(Font.PLAIN, 11f));
                g.setColor(Theme.dim());
                UI.text(g, sub, 48, 38, getWidth() - 56);
            } else {
                Icons.draw(g, icon, (getWidth() - 34) / 2, 12, 34);
                g.setFont(Theme.font(Font.PLAIN, 12f));
                g.setColor(Theme.text());
                FontMetrics fm = g.getFontMetrics();
                String s = name;
                while (fm.stringWidth(s) > getWidth() - 6 && s.length() > 2) s = s.substring(0, s.length() - 2) + "…";
                g.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, 66);
            }
            g.dispose();
        }
    }

    class Footer extends JComponent {
        int hot = -1;

        Footer() {
            setPreferredSize(new Dimension(W, 64));
            MouseAdapter m = new MouseAdapter() {
                public void mouseMoved(MouseEvent e) { int h = at(e.getX()); if (h != hot) { hot = h; repaint(); } }
                public void mouseExited(MouseEvent e) { hot = -1; repaint(); }
                public void mousePressed(MouseEvent e) {
                    int h = at(e.getX());
                    if (h == 0) { close(); d.open("settings"); }
                    if (h == 1) power().show(Footer.this, getWidth() - 160, -150);
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        int at(int x) {
            if (x > 24 && x < 260) return 0;
            if (x > getWidth() - 72 && x < getWidth() - 24) return 1;
            return -1;
        }

        JPopupMenu power() {
            JPopupMenu m = new JPopupMenu();
            m.add(Desktop.item("Заблокировать", () -> { close(); Main.lock(); }));
            m.add(Desktop.item("Выйти", () -> { close(); Main.logout(); }));
            m.addSeparator();
            m.add(Desktop.item("Перезагрузка", () -> { close(); Main.shutdown(true); }));
            m.add(Desktop.item("Завершение работы", () -> { close(); Main.shutdown(false); }));
            return m;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.dark ? new Color(0, 0, 0, 40) : new Color(0, 0, 0, 10));
            g.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 14, 14));
            g.fillRect(0, 0, w, 14);
            g.setColor(Theme.border());
            g.fillRect(0, 0, w, 1);
            if (hot == 0) {
                g.setColor(Theme.dark ? new Color(255, 255, 255, 18) : new Color(0, 0, 0, 14));
                g.fillRoundRect(24, 12, 236, 40, 8, 8);
            }
            if (hot == 1) {
                g.setColor(Theme.dark ? new Color(255, 255, 255, 18) : new Color(0, 0, 0, 14));
                g.fillRoundRect(w - 72, 12, 40, 40, 8, 8);
            }
            Icons.draw(g, "user", 34, 18, 28);
            g.setColor(Theme.text());
            g.setFont(Theme.font(Font.PLAIN, 13f));
            UI.text(g, Theme.user, 72, 37, 180);
            Icons.draw(g, "power", w - 62, 22, 20);
            g.dispose();
        }
    }
}
