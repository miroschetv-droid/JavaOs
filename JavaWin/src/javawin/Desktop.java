package javawin;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

// Рабочий стол: обои со значками, окна поверх, панель задач и меню «Пуск».
public class Desktop extends JPanel {
    static final Integer WALL = 0, WINS = 100, BAR = 200, MENU = 300, TOAST = 400;

    final JLayeredPane layer = new JLayeredPane();
    final Wall wall = new Wall();
    final List<Taskbar> bars = new ArrayList<>();   // по панели задач на каждый монитор
    Taskbar taskbar;                                 // панель главного монитора
    final StartMenu startMenu;
    final Calendar calendar;
    final List<Win> wins = new ArrayList<>();
    final LinkedList<Path> recent = new LinkedList<>();
    Win active;
    int cascade;
    private AWTEventListener watcher;
    private final javax.swing.Timer deskWatch;

    public Desktop() {
        super(new BorderLayout());
        add(layer);
        layer.setLayout(null);
        layer.add(wall, WALL);
        taskbar = new Taskbar(this);
        bars.add(taskbar);
        layer.add(taskbar, BAR);
        startMenu = new StartMenu(this);
        startMenu.setVisible(false);
        layer.add(startMenu, MENU);
        calendar = new Calendar();
        calendar.setVisible(false);
        layer.add(calendar, MENU);
        layer.addComponentListener(new ComponentAdapter() {
            public void componentResized(ComponentEvent e) { relayout(); }
        });
        if (!GraphicsEnvironment.isHeadless()) {
            watcher = ev -> { if (ev.getID() == MouseEvent.MOUSE_PRESSED) pressed((MouseEvent) ev); };
            Toolkit.getDefaultToolkit().addAWTEventListener(watcher, AWTEvent.MOUSE_EVENT_MASK);
        }
        deskWatch = new javax.swing.Timer(2000, e -> wall.sync());
        deskWatch.start();
        wall.sync();
    }

    public void dispose() {
        if (watcher != null) Toolkit.getDefaultToolkit().removeAWTEventListener(watcher);
        deskWatch.stop();
        for (Taskbar b : bars) b.stop();
        for (Win w : new ArrayList<>(wins)) closeWin(w);
    }

    void pressed(MouseEvent e) {
        Component c = e.getComponent();
        if (c == null || !SwingUtilities.isDescendingFrom(c, this)) return;
        if (c instanceof javax.swing.text.JTextComponent tc && SwingUtilities.isRightMouseButton(e) && tc.getComponentPopupMenu() == null)
            SwingUtilities.invokeLater(() -> textMenu(tc).show(tc, e.getX(), e.getY()));
        Win w = c instanceof Win ww ? ww : (Win) SwingUtilities.getAncestorOfClass(Win.class, c);
        if (w != null && w != active) activate(w);
        boolean onStart = false, onClock = false;
        for (Taskbar b : bars) { onStart |= c == b.startBtn; onClock |= c == b.clock; }
        if (startMenu.isVisible() && !SwingUtilities.isDescendingFrom(c, startMenu) && !onStart)
            startMenu.close();
        if (calendar.isVisible() && !SwingUtilities.isDescendingFrom(c, calendar) && !onClock)
            calendar.setVisible(false);
    }

    // Мониторы в координатах рабочего стола. В режиме ОС окно системы закрывает все мониторы,
    // панель задач, «Пуск» и значки — на главном, развёрнутое окно — на своём мониторе.
    List<Rectangle> monitors() {
        List<Rectangle> l = new ArrayList<>();
        if (Main.osMode && !GraphicsEnvironment.isHeadless()) {
            for (Rectangle b : Main.screenRects()) {   // главный (самый большой) — первым
                b = b.intersection(new Rectangle(0, 0, layer.getWidth(), layer.getHeight()));
                if (!b.isEmpty()) l.add(b);
            }
        }
        if (l.isEmpty()) l.add(new Rectangle(0, 0, layer.getWidth(), layer.getHeight()));
        return l;
    }

    Rectangle primary() { return monitors().get(0); }

    // рабочая область главного монитора (без панели задач)
    Rectangle workArea() {
        Rectangle p = primary();
        return new Rectangle(p.x, p.y, p.width, p.height - Taskbar.H);
    }

    // рабочая область монитора, на котором стоит окно (по его середине)
    Rectangle workArea(Component c) {
        Point m = new Point(c.getX() + c.getWidth() / 2, c.getY() + c.getHeight() / 2);
        List<Rectangle> ms = monitors();
        for (int i = 1; i < ms.size(); i++) if (ms.get(i).contains(m)) {
            Rectangle r = ms.get(i);
            return new Rectangle(r.x, r.y, r.width, r.height - Taskbar.H);   // у каждого монитора своя панель задач
        }
        return workArea();
    }

    void relayout() {
        int w = layer.getWidth(), h = layer.getHeight();
        Rectangle p = primary();
        wall.setBounds(0, 0, w, h);
        List<Rectangle> ms = monitors();
        while (bars.size() < ms.size()) { Taskbar b = new Taskbar(this); bars.add(b); layer.add(b, BAR); }
        while (bars.size() > ms.size()) { Taskbar b = bars.remove(bars.size() - 1); b.stop(); layer.remove(b); }
        for (int i = 0; i < ms.size(); i++) {
            Rectangle m = ms.get(i);
            Taskbar b = bars.get(i);
            b.mon.setBounds(m);
            b.setBounds(m.x, m.y + m.height - Taskbar.H, m.width, Taskbar.H);
            b.doLayout();
        }
        taskbar = bars.get(0);
        for (Win x : wins) if (x.maximized) x.setBounds(workArea(x));
        startMenu.place(p);
        calendar.setBounds(p.x + p.width - 320 - 12, p.y + p.height - Taskbar.H - 360 - 12, 320, 360);
        wall.arrange();
    }

    void welcome() { toast("Добро пожаловать", "Привет, " + Theme.user + "! Открой «Прочти меня» на рабочем столе."); }

    // ---------------------------------------------------------------- окна

    public Win open(String id) { return open(id, null); }

    public Win open(String id, Path arg) {
        Apps.App a = Apps.get(id);
        if (a == null) return null;
        JComponent body = a.make().apply(this, arg);
        Win w = new Win(this, a.id(), a.title(), a.icon(), body, false);
        int ww = a.w(), hh = a.h();
        if (ww == 0) {
            Dimension p = body.getPreferredSize();
            ww = p.width;
            hh = p.height + Win.TB;
        }
        place(w, ww + 2 * Win.M, hh + 2 * Win.M);
        if (body instanceof AppBody ab) ab.attached(w);
        return w;
    }

    void place(Win w, int ww, int hh) {
        Rectangle a = workArea();
        ww = Math.min(ww, Math.max(300, a.width));
        hh = Math.min(hh, Math.max(200, a.height));
        int off = (cascade++ % 6) * 26 - 52;
        int x = a.x + Math.max(-Win.M, (a.width - ww) / 2 + off), y = a.y + Math.max(-Win.M, (a.height - hh) / 2 + off);
        w.setBounds(x, y, ww, hh);
        wins.add(w);
        layer.add(w, WINS);
        w.validate();
        activate(w);
        refreshBars();
    }

    public void openFile(Path p) {
        if (Files.isDirectory(p)) { open("explorer", p); return; }
        remember(p);
        String ext = Vfs.ext(p);
        if (Vfs.isImage(p)) open("paint", p);
        else if (Vfs.isAudio(p)) open("player", p);
        else if (ext.equals("html") || ext.equals("htm")) open("browser", p);   // веб-страница — в браузере, как страница
        else if (Vfs.isText(p)) open("notepad", p);
        else if (ext.equals("jar")) runJar(p);
        else if (ext.equals("exe") || ext.equals("msi"))
            toast("Это программа для Windows", "Файлы .exe работают только в Windows. В JavaOS запускаются программы .jar (на Java) и программы Linux через Arch Linux.");
        else open("notepad", p);
    }

    // .jar — программа на Java: для JavaOS это то же, что .exe для Windows
    // Всё, что программа пишет, — в журнал рядом с ней («<имя>.log.txt»; если туда писать нельзя — во временную папку).
    // Закрылась с ошибкой — показываем причину.
    void runJar(Path p) {
        try {
            Path jar = p.toAbsolutePath();
            String java = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
            Path log = jar.resolveSibling(jar.getFileName() + ".log.txt");
            try { Files.writeString(log, ""); }
            catch (IOException e) { log = Theme.base.resolve(jar.getFileName() + ".log.txt"); Files.writeString(log, ""); }   // папка только для чтения — журнал в папку системы
            // быстрый повторный запуск: при первом старте Java сохраняет разобранные классы программы
            // в архив (на диск системы), со второго раза берёт их оттуда, а не распаковывает из .jar заново
            Path cds = Paths.get(System.getProperty("javawin.home", System.getProperty("user.home")), ".cache", "jar-cds");
            Files.createDirectories(cds);
            String jsa = cds.resolve(jar.getFileName() + "-" + Files.size(jar) + ".jsa").toString();
            // рисование без видеокарты: драйвер nouveau новых NVIDIA 3D почти не умеет, и программы на
            // Compose/OpenGL (AuroraGram) падали или показывали пустое окно
            ProcessBuilder pb = new ProcessBuilder(java, "-XX:+AutoCreateSharedArchive", "-XX:SharedArchiveFile=" + jsa,
                    "-XX:TieredStopAtLevel=1", "-Dskiko.renderApi=SOFTWARE", "-Dsun.java2d.opengl=false", "-jar", jar.toString());
            pb.environment().put("LIBGL_ALWAYS_SOFTWARE", "1");
            Process proc = pb
                .directory(jar.getParent().toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();
            String name = jar.getFileName().toString();
            toast("Запуск программы", name + ". Большие программы открываются до минуты.");
            Path logFinal = log;
            Thread t = new Thread(() -> {
                try {
                    int code = proc.waitFor();
                    // как закрылась программа — в конец журнала (137 = убита системой, например не хватило памяти)
                    try { Files.writeString(logFinal, "\n[JavaOS] программа закрылась, код " + code + "\n", StandardOpenOption.APPEND); }
                    catch (IOException ignored) {}
                    if (code == 0) return;
                    String why = "код " + code;
                    List<String> lines = Files.readAllLines(logFinal);
                    for (int i = lines.size() - 1; i >= 0; i--) {   // последняя строка с ошибкой — самая понятная
                        String l = lines.get(i).trim();
                        if (l.contains("Exception") || l.contains("Error") || l.startsWith("Caused by")) { why = l; break; }
                    }
                    if (why.length() > 160) why = why.substring(0, 157) + "…";
                    String w = why;
                    SwingUtilities.invokeLater(() -> toast("Программа не запустилась", name + ": " + w));
                } catch (Exception ignored) {}
            }, "jar-watch");
            t.setDaemon(true);
            t.start();
        } catch (Exception e) { error(e); }
    }

    void remember(Path p) {
        recent.remove(p);
        recent.addFirst(p);
        while (recent.size() > 6) recent.removeLast();
    }

    public void activate(Win w) {
        Win old = active;
        if (w.minimized) {
            w.minimized = false;
            w.setVisible(true);
        }
        active = w;
        layer.moveToFront(w);
        if (old != null && old != w) old.repaint();
        w.repaint();
        repaintBars();
        Component f = w.body instanceof AppBody ab ? ab.focusTarget() : null;
        Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        if (f != null && (owner == null || !SwingUtilities.isDescendingFrom(owner, w)))
            f.requestFocusInWindow();
    }

    public void minimize(Win w) {
        w.minimized = true;
        w.setVisible(false);
        if (active == w) {
            active = null;
            Win t = top();
            if (t != null) activate(t);
        }
        repaintBars();
    }

    void showDesktop() {
        boolean any = wins.stream().anyMatch(w -> !w.minimized);
        if (any) for (Win w : wins) { if (!w.minimized) minimize(w); }
        else for (Win w : wins) activate(w);
    }

    public void closeWin(Win w) {
        if (!wins.remove(w)) return;
        Rectangle b = w.getBounds();
        layer.remove(w);
        layer.repaint(b);
        if (w.body instanceof AppBody ab) ab.closing();
        if (active == w) {
            active = null;
            Win t = top();
            if (t != null) activate(t);
        }
        refreshBars();
    }

    Win top() {
        for (Component c : layer.getComponentsInLayer(WINS))
            if (c instanceof Win w && !w.minimized) return w;
        return null;
    }

    public List<Win> windows() {
        List<Win> l = new ArrayList<>();
        for (Win w : wins) if (!w.dialog) l.add(w);
        return l;
    }

    public Win activeWindow() { return active; }

    List<Win> winsOf(String appId) {
        List<Win> l = new ArrayList<>();
        for (Win w : wins) if (!w.dialog && w.appId.equals(appId)) l.add(w);
        return l;
    }

    void toggleStart() { toggleStart(taskbar); }

    // «Пуск» открывается на мониторе той панели, где нажали
    void toggleStart(Taskbar from) {
        calendar.setVisible(false);
        if (startMenu.isVisible()) { startMenu.close(); return; }
        startMenu.place(from.mon.isEmpty() ? primary() : from.mon);
        startMenu.open();
    }

    void toggleCalendar(Taskbar from) {
        startMenu.close();
        Rectangle p = from.mon.isEmpty() ? primary() : from.mon;
        calendar.month = java.time.YearMonth.now();
        calendar.setBounds(p.x + p.width - 320 - 12, p.y + p.height - Taskbar.H - 360 - 12, 320, 360);
        calendar.setVisible(!calendar.isVisible());
        repaintBars();
    }

    void repaintBars() { for (Taskbar b : bars) b.repaint(); }
    void refreshBars() { for (Taskbar b : bars) b.refresh(); }

    // ---------------------------------------------------------------- диалоги

    Win dialog(String title, String icon, JComponent body) {
        Win w = new Win(this, "dialog", title, icon, body, true);
        Dimension p = body.getPreferredSize();
        place(w, Math.max(p.width, 360) + 2 * Win.M, p.height + Win.TB + 2 * Win.M);
        return w;
    }

    public void ask(String title, String text, String[] options, IntConsumer done) {
        JPanel p = new JPanel(new BorderLayout(0, 18));
        p.setBorder(new EmptyBorder(18, 22, 18, 22));
        JLabel l = UI.label("<html><div style='width:300px'>" + esc(text).replace("\n", "<br>") + "</div></html>");
        p.add(l, BorderLayout.CENTER);
        JPanel bs = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bs.setOpaque(false);
        p.add(bs, BorderLayout.SOUTH);
        Win[] w = new Win[1];
        for (int i = 0; i < options.length; i++) {
            int k = i;
            UI.Btn b = i == 0 ? UI.accent(options[i], null) : UI.button(options[i], null);
            b.addActionListener(e -> { closeWin(w[0]); if (done != null) done.accept(k); });
            bs.add(b);
        }
        w[0] = dialog(title, "about", p);
    }

    public void message(String title, String text) { ask(title, text, new String[] { "ОК" }, null); }

    public void error(Exception e) { message("Ошибка", e.getMessage() == null ? e.toString() : e.getMessage()); }

    public void prompt(String title, String label, String init, Consumer<String> ok) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBorder(new EmptyBorder(16, 20, 16, 20));
        p.add(UI.label(label), BorderLayout.NORTH);
        JTextField f = UI.field(init);
        f.setPreferredSize(new Dimension(340, 32));
        p.add(f, BorderLayout.CENTER);
        JPanel bs = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bs.setOpaque(false);
        bs.setBorder(new EmptyBorder(8, 0, 0, 0));
        Win[] w = new Win[1];
        Runnable go = () -> {
            String s = f.getText().trim();
            if (s.isEmpty()) return;
            closeWin(w[0]);
            ok.accept(s);
        };
        bs.add(UI.accent("ОК", go));
        bs.add(UI.button("Отмена", () -> closeWin(w[0])));
        p.add(bs, BorderLayout.SOUTH);
        f.addActionListener(e -> go.run());
        f.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancel");
        f.getActionMap().put("cancel", new AbstractAction() { public void actionPerformed(ActionEvent e) { closeWin(w[0]); } });
        w[0] = dialog(title, "notepad", p);
        int dot = init.lastIndexOf('.');
        f.select(0, dot > 0 ? dot : init.length());
        SwingUtilities.invokeLater(f::requestFocusInWindow);
    }

    static String esc(String s) { return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }

    // ---------------------------------------------------------------- уведомления

    public void toast(String title, String text) {
        JComponent t = new JComponent() {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                UI.aa(g);
                g.setColor(Theme.menu());
                g.fill(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
                g.setColor(Theme.border());
                g.draw(new RoundRectangle2D.Double(0, 0, getWidth() - 1, getHeight() - 1, 12, 12));
                Icons.draw(g, "logo", 16, 18, 28);
                g.setColor(Theme.text());
                g.setFont(Theme.font(Font.BOLD, 13.5f));
                g.drawString(title, 58, 30);
                g.setColor(Theme.dim());
                g.setFont(Theme.font(Font.PLAIN, 12.5f));
                drawWrapped(g, text, 58, 50, getWidth() - 74);
                g.dispose();
            }
        };
        t.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        int w = 360, h = 96;
        Rectangle pm = primary();
        int x = pm.x + pm.width - w - 14, y = pm.y + pm.height - Taskbar.H - h - 14;
        t.setBounds(Main.osMode ? x : x + 40, y, w, h);
        layer.add(t, TOAST);
        javax.swing.Timer slide = new javax.swing.Timer(12, null);
        long t0 = System.currentTimeMillis();
        slide.addActionListener(e -> {
            double k = Math.min(1, (System.currentTimeMillis() - t0) / 220.0);
            t.setLocation((int) (x + 40 * (1 - k) * (1 - k)), y);
            if (k >= 1) slide.stop();
        });
        if (!Main.osMode) slide.start();
        Runnable kill = () -> { layer.remove(t); layer.repaint(t.getBounds()); };
        t.addMouseListener(new MouseAdapter() { public void mousePressed(MouseEvent e) { kill.run(); } });
        javax.swing.Timer life = new javax.swing.Timer(4500, e -> kill.run());
        life.setRepeats(false);
        life.start();
    }

    static void drawWrapped(Graphics2D g, String text, int x, int y, int maxW) {
        FontMetrics fm = g.getFontMetrics();
        StringBuilder line = new StringBuilder();
        int lines = 0;
        for (String word : text.split(" ")) {
            if (fm.stringWidth(line + word) > maxW && line.length() > 0) {
                g.drawString(line.toString(), x, y);
                y += fm.getHeight();
                line.setLength(0);
                if (++lines == 2) return;
            }
            line.append(word).append(' ');
        }
        g.drawString(line.toString(), x, y);
    }

    public void screenshot() {
        BufferedImage img = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        paint(g);
        g.dispose();
        String name = "Снимок экрана " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmmss")) + ".png";
        try {
            Path p = Vfs.pics().resolve(name);
            ImageIO.write(img, "png", p.toFile());
            toast("Снимок экрана сохранён", Vfs.virt(p));
        } catch (IOException e) { error(e); }
    }

    // ---------------------------------------------------------------- обои и значки

    record Item(String name, String icon, Path path, Runnable open) {}

    class Wall extends JPanel {
        final List<DeskIcon> icons = new ArrayList<>();
        String sig = "";

        Wall() {
            super(null);
            setOpaque(true);
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    select(null);
                    requestFocusInWindow();
                    if (SwingUtilities.isRightMouseButton(e)) menu().show(Wall.this, e.getX(), e.getY());
                }
            });
            setFocusable(true);
        }

        List<Item> items() {
            List<Item> l = new ArrayList<>();
            l.add(new Item("Этот компьютер", "pc", null, () -> open("explorer", Vfs.root())));
            l.add(new Item("Корзина", "trash", null, () -> open("explorer", Vfs.trash())));
            l.add(new Item("Терминал", "cmd", null, () -> open("cmd")));
            l.add(new Item("Сапёр", "mines", null, () -> open("mines")));
            l.add(new Item("Змейка", "snake", null, () -> open("snake")));
            for (Path p : Vfs.list(Vfs.desktop())) l.add(new Item(p.getFileName().toString(), Vfs.kind(p), p, () -> openFile(p)));
            return l;
        }

        void sync() {
            List<Path> files = Vfs.list(Vfs.desktop());
            String s = files.toString();
            if (s.equals(sig) && !icons.isEmpty()) return;
            sig = s;
            for (DeskIcon d : icons) remove(d);
            icons.clear();
            for (Item it : items()) {
                DeskIcon d = new DeskIcon(it);
                icons.add(d);
                add(d);
            }
            arrange();
            repaint();
        }

        // сетка значков; куда пользователь перетащил значок — запоминается в icons.properties
        static final int CW = 88, CH = 96;
        final java.util.Properties pos = loadPos();

        java.util.Properties loadPos() {
            java.util.Properties p = new java.util.Properties();
            try (java.io.Reader r = Files.newBufferedReader(Theme.base.resolve("icons.properties"), java.nio.charset.StandardCharsets.UTF_8)) { p.load(r); }
            catch (Exception ignored) {}
            return p;
        }

        void savePos() {
            try (java.io.Writer w = Files.newBufferedWriter(Theme.base.resolve("icons.properties"), java.nio.charset.StandardCharsets.UTF_8)) { pos.store(w, "JavaWin desktop icons"); }
            catch (Exception ignored) {}
        }

        // значки — на главном мониторе
        int rows() { return Math.max(1, Math.max(CH, primary().height - Taskbar.H - 8) / CH); }
        int cols() { return Math.max(1, (primary().width - 6) / CW); }
        Point cellAt(Point p) {
            Rectangle m = primary();
            return new Point(Math.max(0, Math.min(cols() - 1, Math.round((p.x - m.x - 6) / (float) CW))),
                             Math.max(0, Math.min(rows() - 1, Math.round((p.y - m.y - 6) / (float) CH))));
        }
        void put(DeskIcon d, Point c) { Rectangle m = primary(); d.cell = c; d.setBounds(m.x + 6 + c.x * CW, m.y + 6 + c.y * CH, CW - 4, CH - 4); }
        DeskIcon iconIn(Point c, DeskIcon except) {
            for (DeskIcon d : icons) if (d != except && c.equals(d.cell)) return d;
            return null;
        }

        // значок отпустили: встаёт в ближайшую клетку, а стоявший там переезжает на его старое место
        void drop(DeskIcon d, Point old) {
            Point c = cellAt(d.getLocation());
            DeskIcon other = iconIn(c, d);
            if (other != null) { put(other, old); pos.setProperty(other.it.name(), old.x + "," + old.y); }
            put(d, c);
            pos.setProperty(d.it.name(), c.x + "," + c.y);
            savePos();
            repaint();
        }

        void arrange() {
            int rows = rows(), cols = cols();
            List<DeskIcon> free = new ArrayList<>();
            for (DeskIcon d : icons) d.cell = null;
            for (DeskIcon d : icons) {
                Point c = null;
                try {
                    String[] v = pos.getProperty(d.it.name(), "").split(",");
                    c = new Point(Integer.parseInt(v[0].trim()), Integer.parseInt(v[1].trim()));
                } catch (Exception ignored) {}
                if (c != null && c.x < cols && c.y < rows && iconIn(c, d) == null) put(d, c);
                else free.add(d);
            }
            int i = 0;
            for (DeskIcon d : free) {
                Point c;
                do { c = new Point(i / rows, i % rows); i++; } while (iconIn(c, d) != null);
                put(d, c);
            }
        }

        void select(DeskIcon d) {
            for (DeskIcon x : icons) {
                boolean s = x == d;
                if (x.sel != s) { x.sel = s; x.repaint(); }
            }
        }

        JPopupMenu menu() {
            JPopupMenu m = new JPopupMenu();
            m.add(item("Обновить", () -> { sig = ""; sync(); }));
            m.addSeparator();
            m.add(item("Создать папку", () -> prompt("Новая папка", "Имя папки:", "Новая папка", n -> {
                try { Files.createDirectories(Vfs.unique(Vfs.desktop(), n)); sync(); } catch (IOException ex) { error(ex); }
            })));
            m.add(item("Создать текстовый документ", () -> prompt("Новый документ", "Имя файла:", "Новый текстовый документ.txt", n -> {
                try {
                    Files.createFile(Vfs.unique(Vfs.desktop(), n.contains(".") ? n : n + ".txt"));
                    sync();
                } catch (IOException ex) { error(ex); }
            })));
            m.addSeparator();
            m.add(item("Открыть в терминале", () -> open("cmd", Vfs.desktop())));
            m.add(item("Персонализация", () -> open("settings")));
            return m;
        }

        @Override protected void paintComponent(Graphics g) {
            // свои обои на каждом мониторе, а не одна картинка через оба
            for (Rectangle m : monitors()) g.drawImage(Wallpaper.get(Theme.wallpaper, m.width, m.height), m.x, m.y, null);
        }
    }

    // правый щелчок по любому полю ввода, Блокноту, терминалу
    static JPopupMenu textMenu(javax.swing.text.JTextComponent tc) {
        JPopupMenu m = new JPopupMenu();
        boolean sel = tc.getSelectionStart() != tc.getSelectionEnd();
        JMenuItem cut = item("Вырезать", tc::cut), copy = item("Копировать", tc::copy), paste = item("Вставить", tc::paste);
        cut.setAccelerator(KeyStroke.getKeyStroke("ctrl X"));
        copy.setAccelerator(KeyStroke.getKeyStroke("ctrl C"));
        paste.setAccelerator(KeyStroke.getKeyStroke("ctrl V"));
        cut.setEnabled(sel && tc.isEditable());
        copy.setEnabled(sel);
        paste.setEnabled(tc.isEditable());
        m.add(cut);
        m.add(copy);
        m.add(paste);
        m.addSeparator();
        m.add(item("Выделить всё", () -> { tc.requestFocusInWindow(); tc.selectAll(); }));
        return m;
    }

    static JMenuItem item(String text, Runnable r) {
        JMenuItem i = new JMenuItem(text);
        i.addActionListener(e -> r.run());
        return i;
    }

    class DeskIcon extends JComponent {
        final Item it;
        boolean sel, hover, dragging;
        Point grab, cell;

        DeskIcon(Item it) {
            this.it = it;
            setToolTipText(it.path() != null ? Vfs.virt(it.path()) : it.name());
            MouseAdapter ma = new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }

                public void mousePressed(MouseEvent e) {
                    wall.select(DeskIcon.this);
                    if (SwingUtilities.isRightMouseButton(e)) menu().show(DeskIcon.this, e.getX(), e.getY());
                    else if (e.getClickCount() == 2) it.open().run();
                    else if (SwingUtilities.isLeftMouseButton(e)) { grab = e.getPoint(); dragging = false; }
                }

                public void mouseReleased(MouseEvent e) {
                    if (dragging) wall.drop(DeskIcon.this, cell);
                    grab = null; dragging = false;
                }

                public void mouseDragged(MouseEvent e) {
                    if (grab == null) return;
                    // сдвиг меньше 5 пикселей — это ещё щелчок, а не перетаскивание
                    if (!dragging && grab.distance(e.getPoint()) < 5) return;
                    if (!dragging) { dragging = true; wall.setComponentZOrder(DeskIcon.this, 0); }
                    Point p = SwingUtilities.convertPoint(DeskIcon.this, e.getPoint(), wall);
                    setLocation(p.x - grab.x, p.y - grab.y);
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
        }

        JPopupMenu menu() {
            JPopupMenu m = new JPopupMenu();
            m.add(item("Открыть", it.open()));
            Path p = it.path();
            if (p != null) {
                m.addSeparator();
                m.add(item("Переименовать", () -> prompt("Переименовать", "Новое имя:", p.getFileName().toString(), n -> {
                    try { Files.move(p, p.resolveSibling(n)); wall.sync(); } catch (IOException ex) { error(ex); }
                })));
                m.add(item("Удалить", () -> {
                    try { Vfs.toTrash(p); wall.sync(); } catch (IOException ex) { error(ex); }
                }));
            }
            return m;
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            int w = getWidth();
            if (sel || hover) {
                g.setColor(new Color(255, 255, 255, sel ? 60 : 30));
                g.fillRoundRect(0, 0, w, getHeight(), 8, 8);
                if (sel) {
                    g.setColor(new Color(255, 255, 255, 90));
                    g.drawRoundRect(0, 0, w - 1, getHeight() - 1, 8, 8);
                }
            }
            Icons.draw(g, it.icon(), (w - 40) / 2, 6, 40);
            g.setFont(Theme.font(Font.PLAIN, 12f));
            FontMetrics fm = g.getFontMetrics();
            List<String> lines = wrap(it.name(), fm, w - 6, 2);
            int y = 60;
            for (String s : lines) {
                int x = (w - fm.stringWidth(s)) / 2;
                g.setColor(new Color(0, 0, 0, 170));
                g.drawString(s, x + 1, y + 1);
                g.setColor(Color.WHITE);
                g.drawString(s, x, y);
                y += fm.getHeight() - 1;
            }
            g.dispose();
        }
    }

    static List<String> wrap(String s, FontMetrics fm, int maxW, int maxLines) {
        List<String> out = new ArrayList<>();
        String rest = s;
        while (!rest.isEmpty() && out.size() < maxLines) {
            if (fm.stringWidth(rest) <= maxW) { out.add(rest); break; }
            int cut = rest.length();
            while (cut > 1 && fm.stringWidth(rest.substring(0, cut)) > maxW) cut--;
            int sp = rest.lastIndexOf(' ', cut);
            if (sp > 0 && out.size() < maxLines - 1) cut = sp;
            if (out.size() == maxLines - 1 && cut < rest.length()) {
                String t = rest;
                while (t.length() > 1 && fm.stringWidth(t + "…") > maxW) t = t.substring(0, t.length() - 1);
                out.add(t + "…");
                break;
            }
            out.add(rest.substring(0, cut).trim());
            rest = rest.substring(cut).trim();
        }
        return out;
    }

    // ---------------------------------------------------------------- календарь из трея

    class Calendar extends JComponent {
        java.time.YearMonth month = java.time.YearMonth.now();

        Calendar() {
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    if (e.getY() < 100 && e.getY() > 64) {
                        if (e.getX() > getWidth() - 50) month = month.plusMonths(1);
                        else if (e.getX() > getWidth() - 90) month = month.minusMonths(1);
                        repaint();
                    }
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.menu());
            g.fill(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 12, 12));
            g.setColor(Theme.border());
            g.draw(new RoundRectangle2D.Double(0, 0, w - 1, h - 1, 12, 12));
            java.time.LocalDate today = java.time.LocalDate.now();
            g.setColor(Theme.text());
            g.setFont(Theme.font(Font.BOLD, 15f));
            g.drawString(today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.of("ru"))), 18, 34);
            g.setFont(Theme.font(Font.PLAIN, 13.5f));
            String mName = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.of("ru")));
            g.drawString(Character.toUpperCase(mName.charAt(0)) + mName.substring(1), 18, 86);
            g.setColor(Theme.dim());
            g.setFont(Theme.font(Font.BOLD, 15f));
            g.drawString("‹", w - 76, 87);
            g.drawString("›", w - 36, 87);
            g.setFont(Theme.font(Font.PLAIN, 12f));
            String[] days = { "Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс" };
            int cw = (w - 24) / 7;
            for (int i = 0; i < 7; i++) {
                FontMetrics fm = g.getFontMetrics();
                g.drawString(days[i], 12 + i * cw + (cw - fm.stringWidth(days[i])) / 2, 118);
            }
            int first = month.atDay(1).getDayOfWeek().getValue() - 1;
            g.setFont(Theme.font(Font.PLAIN, 13f));
            FontMetrics fm = g.getFontMetrics();
            for (int d = 1; d <= month.lengthOfMonth(); d++) {
                int idx = first + d - 1, cx = 12 + (idx % 7) * cw, cy = 128 + (idx / 7) * 36;
                String s = String.valueOf(d);
                boolean isToday = month.equals(java.time.YearMonth.from(today)) && d == today.getDayOfMonth();
                if (isToday) {
                    g.setColor(Theme.accent);
                    g.fillOval(cx + (cw - 32) / 2, cy, 32, 32);
                }
                g.setColor(isToday ? Color.WHITE : idx % 7 >= 5 ? Theme.dim() : Theme.text());
                g.drawString(s, cx + (cw - fm.stringWidth(s)) / 2, cy + 21);
            }
            g.dispose();
        }
    }
}
