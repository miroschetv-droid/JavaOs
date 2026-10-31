package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.FormSubmitEvent;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Браузер с вкладками: качает страницу, вычищает скрипты и сложные стили (см. HtmlClean) и показывает
// упрощённую вёрстку в JEditorPane. Ссылки, картинки и GET/POST-формы работают. Новая вкладка — своя страница на Swing.
public class Browser extends JPanel implements AppBody {
    static final String HOME = "about:home";
    static final String UA = "Mozilla/5.0 (X11; Linux x86_64) JavaWin/1.0 (like Gecko)";
    static { System.setProperty("http.agent", UA); }   // картинки грузит URLConnection — пусть тоже представляется браузером
    static final HttpClient HTTP = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(15))
        .build();
    static final String[][] SITES = {
        { "https://ru.wikipedia.org", "Википедия", "W", "636466" },
        { "https://html.duckduckgo.com/html/", "DuckDuckGo", "D", "de5833" },
        { "https://news.ycombinator.com", "Hacker News", "Y", "ff6600" },
        { "https://archlinux.org", "Arch Linux", "A", "1793d1" },
        { "https://lite.cnn.com", "CNN Lite", "C", "cc0000" },
        { "https://example.com", "example.com", "E", "6b7280" },
    };

    final Desktop d;
    final List<Tab> tabs = new ArrayList<>();
    final CardLayout cards = new CardLayout();
    final JPanel pages = new JPanel(cards);
    final TabStrip strip = new TabStrip();
    final Omnibox addr = new Omnibox();
    final Progress progress = new Progress();
    final UI.Label status = UI.dim(" ");
    final UI.Btn backBtn, fwdBtn;
    Tab cur;
    Win win;
    int seq;

    public Browser(Desktop d, java.nio.file.Path file) {
        super(new BorderLayout());
        this.d = d;
        setBackground(chrome());

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(strip, BorderLayout.NORTH);

        JPanel bar = new JPanel(new BorderLayout(8, 0)) {
            @Override protected void paintComponent(Graphics g) { g.setColor(Theme.window()); g.fillRect(0, 0, getWidth(), getHeight()); }
        };
        bar.setBorder(new EmptyBorder(6, 8, 6, 10));
        backBtn = UI.icon("back", "Назад (Alt+←)", () -> cur.goBack());
        fwdBtn = UI.icon("fwd", "Вперёд (Alt+→)", () -> cur.goFwd());
        bar.add(UI.row(2, backBtn, fwdBtn,
            UI.icon("refresh", "Обновить (F5)", () -> cur.reload()),
            UI.icon("home", "Домашняя страница", () -> cur.go(HOME))), BorderLayout.WEST);
        bar.add(addr, BorderLayout.CENTER);
        top.add(bar, BorderLayout.CENTER);
        top.add(progress, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        add(pages, BorderLayout.CENTER);

        JPanel st = new JPanel(new BorderLayout()) {
            @Override protected void paintComponent(Graphics g) { g.setColor(Theme.window()); g.fillRect(0, 0, getWidth(), getHeight()); }
        };
        st.setBorder(new EmptyBorder(3, 12, 4, 12));
        st.add(status);
        add(st, BorderLayout.SOUTH);

        addr.field.addActionListener(e -> { cur.go(addr.field.getText()); cur.focus(); });
        key(KeyEvent.VK_F5, 0, () -> cur.reload());
        key(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK, () -> cur.goBack());
        key(KeyEvent.VK_RIGHT, InputEvent.ALT_DOWN_MASK, () -> cur.goFwd());
        key(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK, () -> { addr.field.requestFocusInWindow(); addr.field.selectAll(); });
        key(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK, () -> newTab(HOME));
        key(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK, () -> closeTab(cur));
        key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK, () -> select(tabs.get((tabs.indexOf(cur) + 1) % tabs.size())));
        key(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK,
            () -> select(tabs.get((tabs.indexOf(cur) - 1 + tabs.size()) % tabs.size())));
        // открыли .html из Проводника — показываем эту страницу
        newTab(file != null && java.nio.file.Files.isRegularFile(file) ? file.toUri().toString() : HOME);
    }

    static Color chrome() { return Theme.dark ? new Color(0x1a1a1a) : new Color(0xdfe3e8); }

    void key(int code, int mods, Runnable r) {
        String id = "b" + code + "_" + mods;
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke(code, mods), id);
        getActionMap().put(id, new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); } });
    }

    @Override public void attached(Win w) { win = w; updateChrome(); }
    @Override public Component focusTarget() { return cur != null && cur.url.equals(HOME) ? cur.home.search : addr.field; }
    @Override public void closing() { for (Tab t : tabs) t.stop(); progress.timer.stop(); }
    @Override public Dimension getPreferredSize() { return new Dimension(1040, 680); }

    // ---------------------------------------------------------------- вкладки

    Tab newTab(String u) {
        Tab t = new Tab("t" + (seq++));
        tabs.add(t);
        pages.add(t.card, t.id);
        select(t);
        t.go(u);
        return t;
    }

    void select(Tab t) {
        cur = t;
        cards.show(pages, t.id);
        updateChrome();
        SwingUtilities.invokeLater(t::focus);
    }

    void closeTab(Tab t) {
        if (tabs.size() == 1) { t.stop(); t.go(HOME); return; }   // последнюю вкладку не закрываем, а очищаем
        int i = tabs.indexOf(t);
        t.stop();
        tabs.remove(t);
        pages.remove(t.card);
        if (t == cur) select(tabs.get(Math.min(i, tabs.size() - 1)));
        else updateChrome();
    }

    // строка адреса, кнопки, полоска загрузки и заголовок окна — по текущей вкладке
    void updateChrome() {
        if (cur == null) return;
        addr.setUrl(cur.url.equals(HOME) ? "" : cur.url);
        backBtn.setEnabled(!cur.back.isEmpty());
        fwdBtn.setEnabled(!cur.fwd.isEmpty());
        progress.setLoading(cur.loading);
        if (win != null) win.setTitle(cur.title + " — Браузер");
        strip.revalidate();
        strip.repaint();
    }

    class Tab {
        final String id;
        final JPanel card = new JPanel(new BorderLayout());
        final JEditorPane view = new JEditorPane();
        final JScrollPane scroll;
        final HomePage home = new HomePage(this);
        final Deque<String> back = new ArrayDeque<>(), fwd = new ArrayDeque<>();
        String url = "", title = "Новая вкладка";
        boolean loading;
        Thread loader;

        Tab(String id) {
            this.id = id;
            view.setEditable(false);
            view.setContentType("text/html");
            HTMLEditorKit kit = new HTMLEditorKit();
            kit.setAutoFormSubmission(false);
            view.setEditorKit(kit);
            view.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
            view.setBorder(new EmptyBorder(0, 0, 0, 0));
            view.addHyperlinkListener(e -> {
                if (e instanceof FormSubmitEvent f) {
                    String target = f.getURL().toString();
                    if (f.getMethod() == FormSubmitEvent.MethodType.POST) nav(target, true, f.getData());
                    else go(target.replaceAll("\\?.*$", "") + "?" + f.getData());
                    return;
                }
                String href = e.getURL() != null ? e.getURL().toString() : e.getDescription();
                if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) status.setText(href == null ? " " : href);
                else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) status.setText(" ");
                else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED && href != null) {
                    if (href.startsWith("#")) view.scrollToReference(href.substring(1));
                    // Ctrl+щелчок — открыть в новой вкладке, как в настоящих браузерах
                    else if (e.getInputEvent() != null && e.getInputEvent().isControlDown()) newTab(href);
                    else go(href);
                }
            });
            scroll = UI.scroll(view);
            card.add(home, BorderLayout.CENTER);
        }

        void focus() { if (url.equals(HOME)) home.search.requestFocusInWindow(); else view.requestFocusInWindow(); }
        void stop() { if (loader != null) loader.interrupt(); loading = false; }
        void reload() { nav(url, false, null); }

        void go(String target) {
            String u = normalize(target);
            if (!url.isEmpty() && !u.equals(url)) { back.push(url); fwd.clear(); }
            nav(u, false, null);
        }

        void goBack() { if (!back.isEmpty()) { fwd.push(url); nav(back.pop(), false, null); } }
        void goFwd() { if (!fwd.isEmpty()) { back.push(url); nav(fwd.pop(), false, null); } }

        void showHome() {
            card.removeAll();
            card.add(home, BorderLayout.CENTER);
            card.revalidate();
            card.repaint();
        }

        void nav(String u, boolean post, String body) {
            url = u;
            if (loader != null) loader.interrupt();
            if (u.equals(HOME)) {
                loading = false;
                title = "Новая вкладка";
                showHome();
                if (this == cur) updateChrome();
                return;
            }
            loading = true;
            title = host(u);
            if (this == cur) { updateChrome(); status.setText("Загрузка " + u + " …"); }
            loader = new Thread(() -> {
                try {
                    HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(u))
                        .timeout(Duration.ofSeconds(30))
                        .header("User-Agent", UA)
                        .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
                        .header("Accept-Language", "ru,en;q=0.8");
                    if (post) rb.header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                    if (u.startsWith("file:")) {   // страница с диска (.html из Проводника)
                        java.nio.file.Path fp = java.nio.file.Paths.get(URI.create(u));
                        byte[] raw = java.nio.file.Files.readAllBytes(fp);
                        HtmlClean c = new HtmlClean(new String(raw, charset("text/html", raw)), u);
                        String page = c.clean(), tt = c.title != null && !c.title.isBlank() ? c.title.trim() : fp.getFileName().toString();
                        SwingUtilities.invokeLater(() -> { if (url.equals(u)) show(page, u, tt); });
                        return;
                    }
                    HttpResponse<byte[]> r = HTTP.send(rb.build(), HttpResponse.BodyHandlers.ofByteArray());
                    if (Thread.currentThread().isInterrupted()) return;
                    String finalUrl = r.uri().toString();
                    String ctype = r.headers().firstValue("Content-Type").orElse("text/html").toLowerCase(Locale.ROOT);
                    String html, t = host(finalUrl);
                    if (ctype.startsWith("image/")) {
                        html = "<p><img src=\"" + HtmlClean.attr(finalUrl) + "\"></p>";
                    } else if (ctype.startsWith("text/plain")) {
                        html = "<pre>" + HtmlClean.text(new String(r.body(), charset(ctype, r.body()))) + "</pre>";
                    } else if (ctype.contains("html") || ctype.contains("xml")) {
                        HtmlClean c = new HtmlClean(new String(r.body(), charset(ctype, r.body())), finalUrl);
                        html = c.clean();
                        if (c.title != null && !c.title.isBlank()) t = c.title.trim();
                    } else {
                        // не страница — значит файл: сохраняем в «Загрузки»
                        String name = fileName(r.headers().firstValue("Content-Disposition").orElse(""), finalUrl);
                        java.nio.file.Path saved = Vfs.unique(Vfs.downloads(), name);
                        java.nio.file.Files.write(saved, r.body());
                        String shown = saved.getFileName().toString();
                        SwingUtilities.invokeLater(() -> d.toast("Файл скачан", shown + " — в папке «Загрузки»"));
                        html = "<h2>Файл скачан</h2><p><b>" + HtmlClean.text(shown) + "</b> (" + Vfs.size(r.body().length)
                            + ") сохранён в папку «Загрузки»:<br>" + HtmlClean.text(Vfs.virt(saved)) + "</p>"
                            + "<p class=dim>Открой Проводник → «Загрузки».</p>";
                        t = "Файл скачан";
                    }
                    String page = html, tt = t;
                    int code = r.statusCode();
                    SwingUtilities.invokeLater(() -> {
                        if (!url.equals(u)) return;
                        url = finalUrl;
                        show(code >= 400 ? "<p class=err>Сервер ответил: " + code + "</p>" + page : page, finalUrl, tt);
                    });
                } catch (Exception ex) {
                    if (Thread.currentThread().isInterrupted()) return;
                    String msg = ex.getClass().getSimpleName() + (ex.getMessage() == null ? "" : ": " + ex.getMessage());
                    SwingUtilities.invokeLater(() -> {
                        if (!url.equals(u)) return;
                        show("<h1>Не удалось открыть страницу</h1><p>" + HtmlClean.text(u) + "</p><p class=err>"
                            + HtmlClean.text(msg) + "</p><p>Проверь адрес и подключение к интернету (значок Wi-Fi внизу справа).</p>",
                            null, "Ошибка");
                    });
                }
            }, "browser-load");
            loader.setDaemon(true);
            loader.start();
        }

        void show(String body, String base, String newTitle) {
            title = newTitle;
            loading = false;
            HTMLEditorKit kit = (HTMLEditorKit) view.getEditorKit();
            HTMLDocument doc = (HTMLDocument) kit.createDefaultDocument();
            StyleSheet ss = doc.getStyleSheet();
            for (String rule : css()) ss.addRule(rule);
            doc.putProperty("IgnoreCharsetDirective", Boolean.TRUE);
            try { if (base != null) doc.setBase(URI.create(base).toURL()); } catch (Exception ignored) {}
            view.setDocument(doc);
            try {
                view.setText("<html><body>" + body + "</body></html>");
            } catch (Exception e) {
                view.setText("<html><body><h2>Страницу не удалось показать</h2><p>" + HtmlClean.text(String.valueOf(e)) + "</p></body></html>");
            }
            view.setCaretPosition(0);
            card.removeAll();
            card.add(scroll, BorderLayout.CENTER);
            card.revalidate();
            card.repaint();
            SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
            if (this == cur) { status.setText(" "); updateChrome(); }
            else strip.repaint();
        }
    }

    // ---------------------------------------------------------------- полоса вкладок

    class TabStrip extends JComponent {
        static final int H = 40, MAXW = 230, MINW = 90;
        int hover = -1, hoverX = -1;
        boolean hoverPlus;

        TabStrip() {
            setPreferredSize(new Dimension(10, H));
            MouseAdapter m = new MouseAdapter() {
                public void mouseMoved(MouseEvent e) { track(e.getPoint()); }
                public void mouseExited(MouseEvent e) { hover = hoverX = -1; hoverPlus = false; repaint(); }
                public void mousePressed(MouseEvent e) {
                    if (plus().contains(e.getPoint())) { newTab(HOME); return; }
                    int i = at(e.getX());
                    if (i < 0) return;
                    if (SwingUtilities.isMiddleMouseButton(e) || close(i).contains(e.getPoint())) closeTab(tabs.get(i));
                    else select(tabs.get(i));
                }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        void track(Point p) {
            int h = at(p.x), hx = h >= 0 && close(h).contains(p) ? h : -1;
            boolean hp = plus().contains(p);
            if (h != hover || hx != hoverX || hp != hoverPlus) { hover = h; hoverX = hx; hoverPlus = hp; repaint(); }
        }

        int tabW() { return Math.max(MINW, Math.min(MAXW, (getWidth() - 56) / Math.max(1, tabs.size()))); }
        int at(int x) { int i = (x - 8) / tabW(); return x >= 8 && i < tabs.size() ? i : -1; }
        Rectangle close(int i) { return new Rectangle(8 + (i + 1) * tabW() - 28, 12, 20, 20); }
        Rectangle plus() { return new Rectangle(8 + tabs.size() * tabW() + 6, 8, 28, 28); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            g.setColor(chrome());
            g.fillRect(0, 0, getWidth(), getHeight());
            int w = tabW();
            for (int i = 0; i < tabs.size(); i++) {
                Tab t = tabs.get(i);
                int x = 8 + i * w;
                boolean sel = t == cur;
                if (sel) {
                    g.setColor(Theme.window());
                    g.fill(new RoundRectangle2D.Float(x, 6, w - 2, H, 12, 12));
                } else if (i == hover) {
                    g.setColor(new Color(128, 128, 128, 40));
                    g.fill(new RoundRectangle2D.Float(x + 2, 8, w - 6, H - 14, 10, 10));
                } else if (i + 1 < tabs.size() && tabs.get(i + 1) != cur) {
                    g.setColor(new Color(128, 128, 128, 70));
                    g.fillRect(x + w - 2, 14, 1, 16);
                }
                // «значок сайта»: кружок с буквой, пока страница грузится — вращающаяся дуга
                int cx = x + 12, cy = 13;
                if (t.loading) {
                    g.setColor(Theme.accent);
                    g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawArc(cx + 1, cy + 1, 14, 14, (int) (System.currentTimeMillis() / 3 % 360), 270);
                } else {
                    g.setColor(t.url.equals(HOME) ? Theme.accent : siteColor(t.url));
                    g.fillOval(cx, cy, 16, 16);
                    g.setColor(Color.WHITE);
                    g.setFont(Theme.font(Font.BOLD, 10f));
                    String L = t.url.equals(HOME) ? "+" : letter(t.url);
                    FontMetrics fm = g.getFontMetrics();
                    g.drawString(L, cx + 8 - fm.stringWidth(L) / 2, cy + 12);
                }
                g.setFont(Theme.font(Font.PLAIN, 12.5f));
                g.setColor(sel ? Theme.text() : Theme.dim());
                Shape old = g.getClip();
                g.clipRect(cx + 22, 0, w - 22 - 40, H);
                g.drawString(t.title, cx + 22, 26);
                g.setClip(old);
                Rectangle c = close(i);
                if (i == hoverX) { g.setColor(new Color(128, 128, 128, 70)); g.fillOval(c.x, c.y, c.width, c.height); }
                if (sel || i == hover) {
                    g.setColor(Theme.dim());
                    g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g.drawLine(c.x + 6, c.y + 6, c.x + 14, c.y + 14);
                    g.drawLine(c.x + 14, c.y + 6, c.x + 6, c.y + 14);
                }
            }
            Rectangle p = plus();
            if (hoverPlus) { g.setColor(new Color(128, 128, 128, 50)); g.fillOval(p.x, p.y, p.width, p.height); }
            g.setColor(Theme.text());
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.drawLine(p.x + 9, p.y + 14, p.x + 19, p.y + 14);
            g.drawLine(p.x + 14, p.y + 9, p.x + 14, p.y + 19);
            g.dispose();
            boolean any = false;
            for (Tab t : tabs) any |= t.loading;
            if (any) { Timer tm = new Timer(40, e -> repaint()); tm.setRepeats(false); tm.start(); }   // крутим значок загрузки
        }
    }

    static String letter(String u) {
        String h = host(u);
        if (h == null || h.isEmpty()) return "?";
        h = h.replaceFirst("^(www|m|html|lite)\\.", "").replaceFirst("^[a-z]{2}\\.(?=.+\\.)", "");   // ru.wikipedia.org -> W
        return h.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    static Color siteColor(String u) {
        String h = String.valueOf(host(u));
        for (String[] s : SITES) if (s[0].contains(h)) return new Color(Integer.parseInt(s[3], 16));
        float hue = (h.hashCode() & 0xffff) / 65535f;
        return Color.getHSBColor(hue, 0.55f, 0.75f);
    }

    // ---------------------------------------------------------------- строка адреса

    class Omnibox extends JPanel {
        final JTextField field = new JTextField();
        boolean secure;

        Omnibox() {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(0, 34, 0, 14));
            setPreferredSize(new Dimension(200, 36));
            field.setOpaque(false);
            field.setBorder(new EmptyBorder(0, 0, 0, 0));
            field.setFont(Theme.font(Font.PLAIN, 14f));
            field.setForeground(Theme.text());
            field.setCaretColor(Theme.text());
            field.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); SwingUtilities.invokeLater(field::selectAll); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
            add(field);
        }

        void setUrl(String u) {
            field.setText(u);
            field.setCaretPosition(0);
            secure = u.startsWith("https://");
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            boolean f = field.hasFocus();
            g.setColor(f ? Theme.field() : (Theme.dark ? new Color(0x2c2c2c) : new Color(0xeef1f5)));
            g.fill(new RoundRectangle2D.Float(0, 1, getWidth() - 1, getHeight() - 3, getHeight() - 3, getHeight() - 3));
            if (f) {
                g.setColor(Theme.accent);
                g.setStroke(new BasicStroke(2f));
                g.draw(new RoundRectangle2D.Float(1, 2, getWidth() - 3, getHeight() - 5, getHeight() - 5, getHeight() - 5));
            }
            int cy = getHeight() / 2 - 1;
            if (field.getText().isEmpty() || f) {
                Icons.draw(g, "search", 12, cy - 8, 16);
            } else {   // замочек — защищённое соединение, иначе кружок «i»
                g.setColor(secure ? Theme.text() : Theme.dim());
                g.setStroke(new BasicStroke(1.6f));
                if (secure) {
                    g.drawArc(15, cy - 8, 8, 9, 0, 180);
                    g.fillRoundRect(13, cy - 3, 12, 9, 3, 3);
                } else {
                    g.drawOval(12, cy - 7, 14, 14);
                    g.fillRect(18, cy - 1, 2, 5);
                    g.fillRect(18, cy - 4, 2, 2);
                }
            }
            if (field.getText().isEmpty() && !f) {
                g.setFont(field.getFont());
                g.setColor(Theme.dim());
                g.drawString("Найди в интернете или введи адрес", 34, cy + 5);
            }
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- полоска загрузки

    class Progress extends JComponent {
        boolean loading;
        final Timer timer = new Timer(30, e -> repaint());
        Progress() { setPreferredSize(new Dimension(10, 3)); }
        void setLoading(boolean l) { loading = l; if (l) timer.start(); else timer.stop(); repaint(); }

        @Override protected void paintComponent(Graphics g) {
            g.setColor(Theme.window());
            g.fillRect(0, 0, getWidth(), getHeight());
            if (!loading) return;
            int w = getWidth(), seg = Math.max(80, w / 4);
            int x = (int) (System.currentTimeMillis() / 2 % (w + seg)) - seg;
            g.setColor(Theme.accent);
            g.fillRect(x, 0, seg, 3);
        }
    }

    // ---------------------------------------------------------------- новая вкладка

    class HomePage extends JPanel {
        final Tab tab;
        final JTextField search = new JTextField();

        HomePage(Tab tab) {
            super(new GridBagLayout());
            this.tab = tab;
            setBackground(Theme.field());
            JPanel col = new JPanel();
            col.setOpaque(false);
            col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));

            UI.Label hi = UI.title(greeting(), 30f);
            hi.setAlignmentX(CENTER_ALIGNMENT);
            col.add(hi);
            col.add(Box.createVerticalStrut(22));

            JPanel box = new JPanel(new BorderLayout()) {
                @Override protected void paintComponent(Graphics g0) {
                    Graphics2D g = (Graphics2D) g0.create();
                    UI.aa(g);
                    g.setColor(Theme.dark ? new Color(0x303134) : new Color(0xf1f3f4));
                    g.fill(new RoundRectangle2D.Float(0, 0, getWidth() - 1, getHeight() - 1, getHeight() - 1, getHeight() - 1));
                    if (search.hasFocus()) {
                        g.setColor(Theme.accent);
                        g.setStroke(new BasicStroke(2f));
                        g.draw(new RoundRectangle2D.Float(1, 1, getWidth() - 3, getHeight() - 3, getHeight() - 3, getHeight() - 3));
                    }
                    Icons.draw(g, "search", 18, getHeight() / 2 - 9, 18);
                    if (search.getText().isEmpty()) {
                        g.setFont(search.getFont());
                        g.setColor(Theme.dim());
                        g.drawString("Поиск в DuckDuckGo или адрес сайта", 48, getHeight() / 2 + 6);
                    }
                    g.dispose();
                }
            };
            box.setOpaque(false);
            box.setBorder(new EmptyBorder(0, 48, 0, 20));
            box.setMaximumSize(new Dimension(600, 50));
            box.setPreferredSize(new Dimension(600, 50));
            box.setAlignmentX(CENTER_ALIGNMENT);
            search.setOpaque(false);
            search.setBorder(new EmptyBorder(0, 0, 0, 0));
            search.setFont(Theme.font(Font.PLAIN, 16f));
            search.setForeground(Theme.text());
            search.setCaretColor(Theme.text());
            search.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { box.repaint(); }
                public void focusLost(FocusEvent e) { box.repaint(); }
            });
            search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                public void insertUpdate(javax.swing.event.DocumentEvent e) { box.repaint(); }
                public void removeUpdate(javax.swing.event.DocumentEvent e) { box.repaint(); }
                public void changedUpdate(javax.swing.event.DocumentEvent e) { }
            });
            search.addActionListener(e -> { String q = search.getText(); if (!q.isBlank()) { search.setText(""); tab.go(q); } });
            box.add(search);
            col.add(box);
            col.add(Box.createVerticalStrut(34));

            JPanel tiles = new JPanel(new GridLayout(1, 0, 14, 0));
            tiles.setOpaque(false);
            for (String[] s : SITES) tiles.add(new Tile(s));
            tiles.setAlignmentX(CENTER_ALIGNMENT);
            tiles.setMaximumSize(tiles.getPreferredSize());
            col.add(tiles);
            col.add(Box.createVerticalStrut(30));
            UI.Label note = UI.dim("Ctrl+T — новая вкладка · Ctrl+W — закрыть · Ctrl+щелчок по ссылке — в новой вкладке");
            note.setAlignmentX(CENTER_ALIGNMENT);
            col.add(note);
            add(col);
        }

        @Override protected void paintComponent(Graphics g) { g.setColor(Theme.field()); g.fillRect(0, 0, getWidth(), getHeight()); }

        String greeting() {
            int h = LocalTime.now().getHour();
            String part = h < 6 ? "Доброй ночи" : h < 12 ? "Доброе утро" : h < 18 ? "Добрый день" : "Добрый вечер";
            return part + ", " + Theme.user;
        }

        class Tile extends JComponent {
            final String[] s;
            boolean hover;

            Tile(String[] s) {
                this.s = s;
                setPreferredSize(new Dimension(96, 100));
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText(s[0]);
                addMouseListener(new MouseAdapter() {
                    public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                    public void mouseClicked(MouseEvent e) { if (e.isControlDown()) newTab(s[0]); else tab.go(s[0]); }
                });
            }

            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                UI.aa(g);
                if (hover) {
                    g.setColor(new Color(128, 128, 128, 40));
                    g.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 14, 14));
                }
                int cx = getWidth() / 2;
                g.setColor(Theme.dark ? new Color(0x303134) : new Color(0xf1f3f4));
                g.fillOval(cx - 26, 12, 52, 52);
                g.setColor(new Color(Integer.parseInt(s[3], 16)));
                g.fillOval(cx - 16, 22, 32, 32);
                g.setColor(Color.WHITE);
                g.setFont(Theme.font(Font.BOLD, 15f));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(s[2], cx - fm.stringWidth(s[2]) / 2, 43);
                g.setFont(Theme.font(Font.PLAIN, 12f));
                g.setColor(Theme.text());
                fm = g.getFontMetrics();
                String n = s[1];
                while (fm.stringWidth(n) > getWidth() - 8 && n.length() > 3) n = n.substring(0, n.length() - 2) + "…";
                g.drawString(n, cx - fm.stringWidth(n) / 2, 86);
                g.dispose();
            }
        }
    }

    // ---------------------------------------------------------------- адреса и страницы

    // адрес, домен или поисковый запрос
    static String normalize(String s) {
        s = s.trim();
        if (s.isEmpty() || s.equals(HOME)) return HOME;
        if (s.matches("(?i)^(https?|about|file):.*")) return s;
        if (!s.contains(" ") && (s.contains(".") || s.startsWith("localhost")) && !s.endsWith("."))
            return "https://" + s;
        return "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // имя файла: из Content-Disposition, иначе последний кусок адреса
    static String fileName(String disposition, String url) {
        Matcher m = Pattern.compile("(?i)filename\\*=UTF-8''([^;]+)").matcher(disposition);
        String n = null;
        if (m.find()) n = java.net.URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
        else {
            m = Pattern.compile("(?i)filename=\"?([^\";]+)\"?").matcher(disposition);
            if (m.find()) n = m.group(1);
        }
        if (n == null || n.isBlank()) {
            try {
                String p = URI.create(url).getPath();
                n = p == null ? "" : java.net.URLDecoder.decode(p.substring(p.lastIndexOf('/') + 1), StandardCharsets.UTF_8);
            } catch (Exception e) { n = ""; }
        }
        n = n.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").trim();
        return n.isEmpty() ? "download" : n;
    }

    static String host(String u) {
        try {
            if (u.startsWith("file:")) return java.nio.file.Paths.get(URI.create(u)).getFileName().toString();
            String h = URI.create(u).getHost(); return h == null ? u : h;
        } catch (Exception e) { return u; }
    }

    static Charset charset(String ctype, byte[] body) {
        Matcher m = Pattern.compile("charset=([\\w-]+)").matcher(ctype);
        String cs = m.find() ? m.group(1) : null;
        if (cs == null) {
            String head = new String(body, 0, Math.min(body.length, 4096), StandardCharsets.ISO_8859_1);
            Matcher mm = Pattern.compile("(?i)<meta[^>]+charset=[\"']?([\\w-]+)").matcher(head);
            if (mm.find()) cs = mm.group(1);
        }
        try { return cs == null ? StandardCharsets.UTF_8 : Charset.forName(cs); }
        catch (Exception e) { return StandardCharsets.UTF_8; }
    }

    static String hex(Color c) { return String.format("#%06x", c.getRGB() & 0xffffff); }

    // CSS страниц: читаемый размер шрифта, поля по бокам, мягкие цвета под тему
    static String[] css() {
        String bg = hex(Theme.field()), fg = hex(Theme.text()), dim = hex(Theme.dim()), acc = hex(Theme.accent),
            pnl = hex(Theme.panel()), brd = hex(Theme.border());
        return new String[] {
            "body { background: " + bg + "; color: " + fg + "; font-family: 'Segoe UI', 'DejaVu Sans', sans-serif; font-size: 14pt; margin: 22px 48px; }",
            "a { color: " + acc + "; text-decoration: none; }",
            "h1 { font-size: 25pt; margin-top: 12px; margin-bottom: 10px; }",
            "h2 { font-size: 19pt; margin-top: 16px; margin-bottom: 6px; }",
            "h3 { font-size: 16pt; margin-top: 12px; margin-bottom: 4px; }",
            "h4, h5, h6 { font-size: 14pt; }",
            "p { margin-top: 7px; margin-bottom: 7px; }",
            "pre { background: " + pnl + "; font-family: monospace; font-size: 12pt; padding: 10px; }",
            "code { font-family: monospace; background: " + pnl + "; }",
            "blockquote { color: " + dim + "; margin-left: 18px; }",
            "td, th { padding: 4px; border-color: " + brd + "; }",
            "th { background: " + pnl + "; }",
            "li { margin-top: 3px; }",
            "hr { color: " + brd + "; }",
            ".err { color: #e5484d; }",
            ".dim { color: " + dim + "; }",
        };
    }
}
