package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

// «Arch Linux» — подсистема программ Linux внутри JavaOS (как WSL в Windows).
// Корень Arch живёт на постоянном диске, программы ставит настоящий pacman, а запускаются они
// через chroot и показывают свои окна на общем экране. Вся работа с системой — через /opt/javaos/arch.sh.
public class ArchLinux extends JPanel implements AppBody {
    static final String MIRROR = "https://geo.mirror.pkgbuild.com/iso/latest/";
    static final String TARBALL = "archlinux-bootstrap-x86_64.tar.zst";
    static final String[][] PICKS = {
        { "firefox", "Firefox — полноценный браузер" },
        { "mousepad", "Mousepad — простой текстовый редактор" },
        { "galculator", "Galculator — калькулятор" },
        { "xterm", "XTerm — терминал Linux" },
        { "gimp", "GIMP — графический редактор" },
        { "vlc", "VLC — видеоплеер" },
        { "gnome-mines", "Сапёр от GNOME" },
        { "supertux", "SuperTux — платформер" },
        { "libreoffice-fresh", "LibreOffice — офисный пакет" },
        { "htop", "htop — диспетчер процессов (в XTerm)" },
    };

    final Desktop d;
    final CardLayout cards = new CardLayout();
    final JPanel body = new JPanel(cards);
    final JTextArea log = new JTextArea();
    final JProgressBar bar = new JProgressBar(0, 1000);
    final UI.Label stage = UI.title("", 15f);
    final JPanel appsGrid = new JPanel(new GridLayout(0, 5, 8, 8));
    final JPanel results = new JPanel();
    final JTextField query = UI.field("");
    final UI.Label storeStatus = UI.dim(" ");
    final JPanel logBox = new JPanel(new BorderLayout());
    final Map<String, Icon> iconCache = new HashMap<>();
    Win win;
    volatile boolean busy;

    public ArchLinux(Desktop d, Path ignored) {
        super(new BorderLayout());
        this.d = d;
        add(body);
        body.add(unavailable(), "unavailable");
        body.add(intro(), "intro");
        body.add(progress(), "progress");
        body.add(main(), "main");
        log.setEditable(false);
        log.setFont(Theme.mono(12.5f));
        log.setLineWrap(true);
        refreshState();
    }

    @Override public void attached(Win w) { win = w; }
    @Override public Dimension getPreferredSize() { return new Dimension(940, 620); }

    static String script() { return System.getProperty("javaos.arch"); }

    void refreshState() {
        if (script() == null || !Files.isExecutable(Paths.get(script()))) { cards.show(body, "unavailable"); return; }
        new Thread(() -> {
            String st = capture("status").trim();
            SwingUtilities.invokeLater(() -> {
                if (st.equals("installed")) { cards.show(body, "main"); loadApps(); showPicks(); }
                else cards.show(body, "intro");
            });
        }, "arch-status").start();
    }

    // ---------------------------------------------------------------- запуск arch.sh

    static int run(Consumer<String> out, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add(script());
        cmd.addAll(Arrays.asList(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) out.accept(line);
            }
            return p.waitFor();
        } catch (Exception e) {
            out.accept("Ошибка: " + explain(e));
            return -1;
        }
    }

    static String capture(String... args) {
        StringBuilder b = new StringBuilder();
        run(l -> b.append(l).append('\n'), args);
        return b.toString();
    }

    static Path root() { return Paths.get(capture("root").trim()); }

    void logLine(String s) {
        SwingUtilities.invokeLater(() -> {
            log.append(s.replace("\r", "") + "\n");
            log.setCaretPosition(log.getDocument().getLength());
        });
    }

    // ---------------------------------------------------------------- экраны

    JComponent unavailable() {
        JPanel p = new JPanel(new GridBagLayout());
        JPanel c = Settings.column();
        c.add(UI.title("Arch Linux", 26f));
        Settings.gap(c, 10);
        c.add(UI.label("Программы Linux работают только внутри JavaOS — загрузочной системы с ядром Linux."));
        c.add(UI.dim("Запусти JavaOS (F:\\JavaOS\\Start VM.bat) и открой это приложение там."));
        p.add(c);
        return p;
    }

    JComponent intro() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(30, 40, 30, 40));
        JPanel c = Settings.column();
        JComponent logo = new JComponent() {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                UI.aa(g);
                Icons.draw(g, "linux", 0, 0, 72);
                g.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(72, 72));
        logo.setMaximumSize(new Dimension(72, 72));
        logo.setAlignmentX(LEFT_ALIGNMENT);
        c.add(logo);
        Settings.gap(c, 14);
        for (JComponent x : new JComponent[] {
            UI.title("Программы Linux в JavaOS", 24f),
            UI.label("Ставит официальный Arch Linux рядом с JavaWin — как WSL в Windows."),
            UI.label("Дальше любые программы из репозиториев Arch (Firefox, GIMP, VLC, LibreOffice…)"),
            UI.label("ставятся через «Магазин» и открываются обычными окнами на этом же экране."),
        }) { x.setAlignmentX(LEFT_ALIGNMENT); c.add(x); Settings.gap(c, 4); }
        Settings.gap(c, 10);
        boolean disk = Files.isDirectory(Paths.get("/mnt/javaos")) && mounted("/mnt/javaos");
        UI.Label where = UI.dim(disk ? "Место: постоянный диск JavaOS — всё сохранится после выключения."
            : "Внимание: у виртуалки нет диска — Arch встанет в оперативную память и пропадёт после выключения.");
        where.setAlignmentX(LEFT_ALIGNMENT);
        c.add(where);
        c.add(Box.createVerticalStrut(4));
        UI.Label size = UI.dim("Скачается ~125 МБ с geo.mirror.pkgbuild.com, займёт ~600 МБ.");
        size.setAlignmentX(LEFT_ALIGNMENT);
        c.add(size);
        Settings.gap(c, 20);
        UI.Btn go = UI.accent("Установить Arch Linux", this::install);
        go.setAlignmentX(LEFT_ALIGNMENT);
        c.add(go);
        p.add(c, BorderLayout.NORTH);
        return p;
    }

    static boolean mounted(String path) {
        try { return Files.readString(Paths.get("/proc/mounts")).contains(" " + path + " "); }
        catch (Exception e) { return false; }
    }

    JComponent progress() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBorder(new EmptyBorder(24, 30, 20, 30));
        JPanel top = Settings.column();
        stage.setAlignmentX(LEFT_ALIGNMENT);
        top.add(stage);
        Settings.gap(top, 10);
        bar.setAlignmentX(LEFT_ALIGNMENT);
        bar.setStringPainted(true);
        top.add(bar);
        p.add(top, BorderLayout.NORTH);
        return p;
    }

    JComponent main() {
        JPanel p = new JPanel(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(6, 8, 0, 8));

        JPanel apps = new JPanel(new BorderLayout());
        appsGrid.setBorder(new EmptyBorder(14, 14, 14, 14));
        JPanel gridWrap = new JPanel(new BorderLayout());
        gridWrap.add(appsGrid, BorderLayout.NORTH);
        apps.add(UI.scroll(gridWrap));
        JPanel appsBar = new JPanel(new BorderLayout());
        appsBar.setBorder(new EmptyBorder(6, 12, 6, 12));
        appsBar.add(UI.dim("Щёлкни — запустить. Правая кнопка — удалить."), BorderLayout.WEST);
        appsBar.add(UI.row(6, UI.button("Обновить список", this::loadApps), UI.button("Обновить систему", this::upgrade)), BorderLayout.EAST);
        apps.add(appsBar, BorderLayout.SOUTH);
        tabs.addTab("Приложения", apps);

        JPanel store = new JPanel(new BorderLayout());
        JPanel search = new JPanel(new BorderLayout(8, 0));
        search.setBorder(new EmptyBorder(10, 12, 10, 12));
        search.add(query, BorderLayout.CENTER);
        search.add(UI.accent("Найти", this::search), BorderLayout.EAST);
        query.addActionListener(e -> search());
        store.add(search, BorderLayout.NORTH);
        results.setLayout(new BoxLayout(results, BoxLayout.Y_AXIS));
        results.setBorder(new EmptyBorder(0, 12, 10, 12));
        JPanel resWrap = new JPanel(new BorderLayout());
        resWrap.add(results, BorderLayout.NORTH);
        store.add(UI.scroll(resWrap), BorderLayout.CENTER);
        JPanel sb = new JPanel(new BorderLayout());
        sb.setBorder(new EmptyBorder(4, 12, 6, 12));
        sb.add(storeStatus);
        store.add(sb, BorderLayout.SOUTH);
        tabs.addTab("Магазин", store);

        p.add(tabs, BorderLayout.CENTER);
        logBox.setPreferredSize(new Dimension(10, 170));
        logBox.setBorder(new EmptyBorder(0, 8, 8, 8));
        logBox.setVisible(false);
        p.add(logBox, BorderLayout.SOUTH);
        return p;
    }

    JScrollPane logScroll;

    // журнал один — переносим его туда, где он нужен сейчас (экран установки или низ главного окна)
    void showLogIn(JPanel where) {
        if (logScroll == null) logScroll = UI.scroll(log);
        if (logScroll.getParent() != null) logScroll.getParent().remove(logScroll);
        where.removeAll();
        where.add(logScroll);
        where.setVisible(true);
        where.revalidate();
    }

    // ---------------------------------------------------------------- установка Arch

    void install() {
        if (busy) return;
        busy = true;
        log.setText("");
        JPanel prog = (JPanel) body.getComponent(2);
        JPanel lb = new JPanel(new BorderLayout());
        prog.add(lb, BorderLayout.CENTER);
        showLogIn(lb);
        cards.show(body, "progress");
        new Thread(() -> {
            try {
                String rs = capture("root").trim();
                if (rs.isEmpty() || !rs.startsWith("/")) throw new IOException("системе некуда установить Arch Linux (" + (rs.isEmpty() ? "arch.sh не ответил" : rs) + ")");
                Path root = Paths.get(rs);
                Path dataDir = root.getParent();
                Files.createDirectories(dataDir);
                Path tar = dataDir.resolve(TARBALL);
                stage("Скачивание Arch Linux…", 0);
                download(MIRROR + TARBALL, tar);
                stage("Проверка контрольной суммы…", -1);
                String sums = new String(get(MIRROR + "sha256sums.txt"), StandardCharsets.UTF_8);
                String want = null;
                for (String l : sums.split("\n")) if (l.trim().endsWith(" " + TARBALL)) want = l.trim().split("\\s+")[0];
                String have = sha256(tar);
                logLine("SHA-256: " + have);
                if (want == null || !want.equalsIgnoreCase(have)) throw new IOException("контрольная сумма не совпала (ожидалось " + want + ")");
                logLine("Контрольная сумма совпала.");
                stage("Распаковка (минута-другая)…", -1);
                if (run(this::logLine, "extract", tar.toString()) != 0) throw new IOException("распаковка не удалась");
                Files.deleteIfExists(tar);
                stage("Настройка: ключи pacman, зеркало, шрифты…", -1);
                if (run(this::logLine, "setup") != 0) throw new IOException("настройка не удалась — см. журнал");
                stage("Arch Linux установлен", 1000);
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    d.toast("Arch Linux", "Установлен. Программы — во вкладке «Магазин».");
                    cards.show(body, "main");
                    loadApps();
                    showPicks();
                });
            } catch (Exception e) {
                String why = explain(e);
                logLine("ОШИБКА: " + why + "  [" + e + "]");
                stage("Не получилось: " + why, 0);
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    JPanel pr = (JPanel) body.getComponent(2);
                    JPanel again = new JPanel(new FlowLayout(FlowLayout.RIGHT));
                    again.add(UI.accent("Попробовать снова", () -> { pr.remove(again); install(); }));
                    pr.add(again, BorderLayout.SOUTH);
                    pr.revalidate();
                });
            }
        }, "arch-install").start();
    }

    void stage(String s, int value) {
        SwingUtilities.invokeLater(() -> {
            stage.setText(s);
            bar.setIndeterminate(value < 0);
            if (value >= 0) bar.setValue(value);
            bar.setString(value < 0 ? "" : null);
        });
        logLine("== " + s);
    }

    // понятный текст ошибки: у многих исключений (нет сети, обрыв) сообщения нет совсем — раньше на экране было «null»
    // есть ли маршрут в интернет (в Linux — строка «по умолчанию» в таблице маршрутов)
    static boolean online() {
        try {
            for (String l : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/proc/net/route")))
                if (l.split("\\s+").length > 1 && l.split("\\s+")[1].equals("00000000")) return true;
            return false;
        } catch (Exception e) { return true; }   // не Linux — не знаем, считаем что есть
    }

    static String explain(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException
                    || t instanceof java.nio.channels.UnresolvedAddressException || t instanceof java.net.NoRouteToHostException)
                return "нет интернета. Подключись к Wi-Fi (значок сети справа внизу) или вставь кабель и попробуй снова";
            if (t instanceof java.net.http.HttpTimeoutException || t instanceof java.net.SocketTimeoutException)
                return "интернет слишком медленный или сервер не отвечает. Попробуй ещё раз";
            String m = t.getMessage();
            if (m != null && m.contains("No space left")) return "не хватает места. Arch Linux нужно около 1,5 ГБ";
        }
        String m = e.getMessage();
        return m != null && !m.isBlank() ? m : "неизвестная ошибка (" + e.getClass().getSimpleName() + ")";
    }

    static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(20)).build();

    static byte[] get(String url) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).build(),
            HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    void download(String url, Path to) throws Exception {
        HttpResponse<InputStream> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofInputStream());
        if (r.statusCode() != 200) throw new IOException("сервер ответил " + r.statusCode());
        long total = r.headers().firstValueAsLong("Content-Length").orElse(-1);
        long got = 0, t0 = System.currentTimeMillis(), last = 0;
        try (InputStream in = r.body(); OutputStream out = new BufferedOutputStream(Files.newOutputStream(to), 1 << 20)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                got += n;
                long now = System.currentTimeMillis();
                if (now - last > 250) {
                    last = now;
                    double mb = got / 1048576.0, speed = mb / Math.max(0.001, (now - t0) / 1000.0);
                    long g = got;
                    SwingUtilities.invokeLater(() -> {
                        if (total > 0) {
                            bar.setValue((int) (g * 1000 / total));
                            bar.setString(String.format("%.1f из %.1f МБ — %.1f МБ/с", mb, total / 1048576.0, speed));
                        } else bar.setString(String.format("%.1f МБ", mb));
                    });
                }
            }
        }
        logLine(String.format("Скачано %.1f МБ", got / 1048576.0));
    }

    static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new BufferedInputStream(Files.newInputStream(p), 1 << 20)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder b = new StringBuilder();
        for (byte x : md.digest()) b.append(String.format("%02x", x));
        return b.toString();
    }

    // ---------------------------------------------------------------- установленные программы

    record Entry(String name, String exec, String icon, Path file) {}

    void loadApps() {
        new Thread(() -> {
            List<Entry> list = scanDesktopFiles(root());
            SwingUtilities.invokeLater(() -> {
                appsGrid.removeAll();
                for (Entry e : list) appsGrid.add(new AppTile(e));
                if (list.isEmpty()) appsGrid.add(UI.dim("Пока пусто. Найди программу во вкладке «Магазин»."));
                appsGrid.revalidate();
                appsGrid.repaint();
            });
        }, "arch-apps").start();
    }

    public static List<Entry> scanDesktopFiles(Path root) {
        List<Entry> out = new ArrayList<>();
        Path dir = root.resolve("usr/share/applications");
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.list(dir)) {
            for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".desktop")).sorted()::iterator) {
                Map<String, String> m = new HashMap<>();
                boolean main = false;
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (line.startsWith("[")) { main = line.equals("[Desktop Entry]"); continue; }
                    int eq = line.indexOf('=');
                    if (main && eq > 0) m.putIfAbsent(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
                }
                if ("true".equalsIgnoreCase(m.get("NoDisplay")) || "true".equalsIgnoreCase(m.get("Hidden"))) continue;
                if (!"Application".equals(m.getOrDefault("Type", "Application")) || m.get("Exec") == null) continue;
                String name = m.getOrDefault("Name[ru]", m.getOrDefault("Name", f.getFileName().toString()));
                String exec = m.get("Exec").replaceAll("%[fFuUdDnNickvm]", "").replace("%%", "%").trim();
                if ("true".equalsIgnoreCase(m.get("Terminal"))) exec = "xterm -fa 'DejaVu Sans Mono' -fs 11 -e " + exec;
                out.add(new Entry(name, exec, m.getOrDefault("Icon", ""), f));
            }
        } catch (IOException ignored) {}
        out.sort(Comparator.comparing(e -> e.name().toLowerCase(Locale.ROOT)));
        return out;
    }

    // иконка из темы hicolor или pixmaps; svg Java не умеет — тогда общий значок
    public static Icon appIcon(Path root, String icon, int size, Map<String, Icon> cache) {
        String key = icon + "@" + size;
        if (cache != null && cache.containsKey(key)) return cache.get(key);
        Icon result = Icons.icon("linux", size);
        try {
            Path p = null;
            if (icon.startsWith("/")) p = root.resolve(icon.substring(1));
            else {
                for (String sz : new String[] { "64x64", "48x48", "128x128", "96x96", "256x256", "32x32" }) {
                    Path c = root.resolve("usr/share/icons/hicolor/" + sz + "/apps/" + icon + ".png");
                    if (Files.exists(c)) { p = c; break; }
                }
                if (p == null && Files.exists(root.resolve("usr/share/pixmaps/" + icon + ".png"))) p = root.resolve("usr/share/pixmaps/" + icon + ".png");
            }
            if (p != null && Files.exists(p) && p.toString().endsWith(".png")) {
                BufferedImage img = ImageIO.read(p.toFile());
                if (img != null) result = new ImageIcon(img.getScaledInstance(size, size, Image.SCALE_SMOOTH));
            }
        } catch (Exception ignored) {}
        if (cache != null) cache.put(key, result);
        return result;
    }

    public static void launch(String exec) {
        new Thread(() -> run(l -> {}, "run", exec), "arch-run").start();
    }

    class AppTile extends JComponent {
        final Entry e;
        final Icon icon;
        boolean hover;

        AppTile(Entry e) {
            this.e = e;
            this.icon = appIcon(root(), e.icon(), 40, iconCache);
            setPreferredSize(new Dimension(150, 100));
            setToolTipText(e.exec());
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent ev) { hover = true; repaint(); }
                public void mouseExited(MouseEvent ev) { hover = false; repaint(); }
                public void mousePressed(MouseEvent ev) {
                    if (SwingUtilities.isRightMouseButton(ev)) {
                        JPopupMenu m = new JPopupMenu();
                        m.add(Explorer.item("Запустить", () -> start()));
                        m.add(Explorer.item("Удалить программу", () -> removeOwner(e)));
                        m.show(AppTile.this, ev.getX(), ev.getY());
                    } else start();
                }
            });
        }

        void start() {
            launch(e.exec());
            d.toast(e.name(), "Запускается… первый запуск может занять несколько секунд.");
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            if (hover) {
                g.setColor(Theme.hover());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            }
            icon.paintIcon(this, g, (getWidth() - 40) / 2, 12);
            g.setColor(Theme.text());
            g.setFont(Theme.font(Font.PLAIN, 12.5f));
            FontMetrics fm = g.getFontMetrics();
            String s = e.name();
            while (fm.stringWidth(s) > getWidth() - 10 && s.length() > 3) s = s.substring(0, s.length() - 2) + "…";
            g.drawString(s, (getWidth() - fm.stringWidth(s)) / 2, 74);
            g.dispose();
        }
    }

    void removeOwner(Entry e) {
        String rel = "/" + root().relativize(e.file()).toString().replace('\\', '/');
        String pkg = capture("exec", "pacman", "-Qqo", rel).trim();
        if (pkg.isEmpty() || pkg.contains(" ")) { d.message("Arch Linux", "Не удалось понять, какой пакет удалить."); return; }
        d.ask("Удаление", "Удалить «" + e.name() + "» (пакет " + pkg + ") вместе с ненужными зависимостями?",
            new String[] { "Удалить", "Отмена" }, i -> { if (i == 0) pacman("Удаление " + pkg, "-Rns", "--noconfirm", pkg); });
    }

    // ---------------------------------------------------------------- магазин

    void showPicks() {
        results.removeAll();
        UI.Label t = UI.title("Популярное", 15f);
        t.setBorder(new EmptyBorder(4, 0, 8, 0));
        results.add(t);
        for (String[] p : PICKS) results.add(new PkgRow("extra", p[0], "", p[1], false));
        results.revalidate();
        results.repaint();
    }

    void search() {
        String q = query.getText().trim();
        if (q.isEmpty()) { showPicks(); return; }
        storeStatus.setText("Поиск «" + q + "»…");
        new Thread(() -> {
            List<String[]> found = new ArrayList<>();
            String[] last = null;
            for (String line : capture("exec", "pacman", "-Ss", q).split("\n")) {
                if (line.isBlank()) continue;
                if (!line.startsWith(" ")) {
                    // extra/firefox 131.0-1 [installed]
                    String[] parts = line.trim().split("\\s+");
                    if (parts.length < 2 || !parts[0].contains("/")) continue;
                    String[] rn = parts[0].split("/", 2);
                    last = new String[] { rn[0], rn[1], parts[1], "", line.contains("[installed") ? "1" : "" };
                    found.add(last);
                } else if (last != null) last[3] += line.trim();
            }
            SwingUtilities.invokeLater(() -> {
                results.removeAll();
                int shown = 0;
                for (String[] f : found) {
                    if (shown++ >= 150) break;
                    results.add(new PkgRow(f[0], f[1], f[2], f[3], !f[4].isEmpty()));
                }
                if (found.isEmpty()) results.add(UI.dim("Ничего не найдено (или нет интернета). Список пакетов обновляется кнопкой «Обновить систему»."));
                storeStatus.setText("Найдено: " + found.size() + (found.size() > 150 ? " (показаны первые 150)" : ""));
                results.revalidate();
                results.repaint();
            });
        }, "arch-search").start();
    }

    class PkgRow extends JPanel {
        PkgRow(String repo, String name, String ver, String desc, boolean installed) {
            super(new BorderLayout(10, 0));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
            setAlignmentX(LEFT_ALIGNMENT);
            JPanel txt = new JPanel(new GridLayout(2, 1));
            txt.setOpaque(false);
            UI.Label n = UI.label(name + (ver.isEmpty() ? "" : "   " + ver) + "   ·  " + repo);
            n.setFont(Theme.font(Font.BOLD, 13f));
            txt.add(n);
            txt.add(UI.dim(desc));
            add(txt, BorderLayout.CENTER);
            UI.Btn b = installed ? UI.button("Удалить", () -> confirmRemove(name)) : UI.accent("Установить", () -> pacman("Установка " + name, "-S", "--noconfirm", "--needed", name));
            JPanel bw = new JPanel(new GridBagLayout());
            bw.setOpaque(false);
            bw.add(b);
            add(bw, BorderLayout.EAST);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            g.setColor(Theme.panel());
            g.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 10, 10);
            g.dispose();
        }
    }

    void confirmRemove(String name) {
        d.ask("Удаление", "Удалить пакет " + name + "?", new String[] { "Удалить", "Отмена" },
            i -> { if (i == 0) pacman("Удаление " + name, "-Rns", "--noconfirm", name); });
    }

    void upgrade() { pacman("Обновление системы", "-Syu", "--noconfirm"); }

    void pacman(String what, String... args) {
        if (busy) { d.message("Arch Linux", "Подожди — идёт другая операция."); return; }
        busy = true;
        log.setText("");
        showLogIn(logBox);
        storeStatus.setText(what + "…");
        List<String> a = new ArrayList<>(List.of("exec", "pacman"));
        a.addAll(Arrays.asList(args));
        new Thread(() -> {
            logLine("$ pacman " + String.join(" ", args));
            int code = run(this::logLine, a.toArray(new String[0]));
            SwingUtilities.invokeLater(() -> {
                busy = false;
                storeStatus.setText(code == 0 ? what + " — готово" : what + " — ошибка (см. журнал)");
                if (code != 0 && !online()) {
                    storeStatus.setText("Нет интернета — подключись к Wi-Fi");
                    d.ask("Нет интернета", "Магазину нужен интернет, а компьютер не подключён к сети. Открыть Wi-Fi?",
                        new String[] { "Открыть Wi-Fi", "Отмена" }, i -> { if (i == 0) d.open("wifi"); });
                } else d.toast("Arch Linux", code == 0 ? what + " — готово" : what + " — не получилось");
                loadApps();
                if (!query.getText().isBlank()) search();
            });
        }, "arch-pacman").start();
    }
}
