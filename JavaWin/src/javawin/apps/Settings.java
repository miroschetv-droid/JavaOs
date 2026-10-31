package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.nio.file.Files;

public class Settings extends JPanel implements AppBody {
    final Desktop d;
    final CardLayout cards = new CardLayout();
    final JPanel pages = new JPanel(cards);
    final JPanel nav = new JPanel();

    public Settings(Desktop d) {
        super(new BorderLayout());
        this.d = d;
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        nav.setBorder(new EmptyBorder(14, 10, 14, 10));
        nav.add(new UserCard());
        nav.add(Box.createVerticalStrut(14));
        page("Персонализация", "paint", personalization());
        page("Учётная запись", "user", account());
        page("Система", "pc", system());
        page("Bluetooth и устройства", "bluetooth", embed(new Bluetooth(d, null)));
        page("Сеть и Интернет", "wifi", embed(new WiFi(d, null)));
        nav.add(Box.createVerticalGlue());
        JPanel navWrap = new JPanel(new BorderLayout());
        navWrap.add(nav, BorderLayout.NORTH);
        navWrap.setPreferredSize(new Dimension(250, 10));
        add(navWrap, BorderLayout.WEST);
        add(pages, BorderLayout.CENTER);
        select("Персонализация");
    }

    void page(String name, String icon, JComponent body) {
        UI.Btn b = UI.button(name, () -> select(name));
        b.flat = true;
        b.setIcon(Icons.icon(icon, 18));
        b.setIconTextGap(12);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(9, 14, 9, 10));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        b.putClientProperty("page", name);
        nav.add(b);
        nav.add(Box.createVerticalStrut(2));
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(18, 26, 18, 26));
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        UI.Label t = UI.title(name, 26f);
        t.setAlignmentX(LEFT_ALIGNMENT);
        col.add(t);
        col.add(Box.createVerticalStrut(18));
        body.setAlignmentX(LEFT_ALIGNMENT);
        col.add(body);
        p.add(col, BorderLayout.NORTH);
        pages.add(UI.scroll(p), name);
    }

    // окно-приложение внутри страницы «Параметров» (Bluetooth, Wi-Fi): своя прокрутка, фиксированная высота
    static JComponent embed(JComponent c) {
        c.setOpaque(false);
        c.setPreferredSize(new Dimension(620, 520));
        c.setMaximumSize(new Dimension(Integer.MAX_VALUE, 520));
        return c;
    }

    void select(String name) {
        cards.show(pages, name);
        for (Component c : nav.getComponents())
            if (c instanceof UI.Btn b) { b.toggled = name.equals(b.getClientProperty("page")); b.repaint(); }
    }

    void apply() {
        Main.applyTheme(this);
        d.repaint();
    }

    // карточка-раздел: заголовок, пояснение и элемент управления справа
    static JComponent card(String title, String sub, JComponent right) {
        JPanel p = new JPanel(new BorderLayout(12, 0)) {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                UI.aa(g);
                g.setColor(Theme.panel());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g.dispose();
            }
        };
        p.setOpaque(false);
        p.setBorder(new EmptyBorder(14, 18, 14, 18));
        JPanel txt = new JPanel(new GridLayout(sub == null ? 1 : 2, 1));
        txt.setOpaque(false);
        txt.add(UI.label(title));
        if (sub != null) txt.add(UI.dim(sub));
        p.add(txt, BorderLayout.CENTER);
        if (right != null) {
            JPanel r = new JPanel(new GridBagLayout());
            r.setOpaque(false);
            r.add(right);
            p.add(r, BorderLayout.EAST);
        }
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, p.getPreferredSize().height));
        return p;
    }

    static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        return p;
    }

    static void gap(JPanel p, int h) { p.add(Box.createVerticalStrut(h)); }

    // ---------------------------------------------------------------- персонализация

    JComponent personalization() {
        JPanel col = column();
        Preview preview = new Preview();
        preview.setAlignmentX(LEFT_ALIGNMENT);
        col.add(preview);
        gap(col, 18);
        UI.Label wl = UI.title("Фон рабочего стола", 14f);
        wl.setAlignmentX(LEFT_ALIGNMENT);
        col.add(wl);
        gap(col, 10);
        // сетка по 3 в ряд: в одну строку пять превью не влезали по ширине и вылезали за окно
        JPanel walls = new JPanel(new GridLayout(0, 3, 10, 10));
        walls.setAlignmentX(LEFT_ALIGNMENT);
        for (int i = 0; i < Wallpaper.NAMES.length; i++) walls.add(new Thumb(i));
        walls.setMaximumSize(walls.getPreferredSize());
        col.add(walls);
        gap(col, 18);
        JPanel accents = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        accents.setOpaque(false);
        for (Color c : Theme.ACCENTS) accents.add(new Dot(c));
        col.add(card("Цвет акцента", "Кнопки, выделение, значки и логотип", accents));
        gap(col, 8);
        UI.Switch sw = new UI.Switch(Theme.dark, null);
        col.add(card("Тёмная тема", "Окна, меню и панель задач в тёмных тонах", sw));
        sw.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { Theme.dark = sw.on; apply(); }
        });
        return col;
    }

    class Preview extends JComponent {
        Preview() {
            setPreferredSize(new Dimension(320, 180));
            setMaximumSize(new Dimension(320, 180));
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            int w = getWidth(), h = getHeight();
            g.setClip(new java.awt.geom.RoundRectangle2D.Double(0, 0, w, h, 12, 12));
            g.drawImage(Wallpaper.get(Theme.wallpaper, 1280, 720), 0, 0, w, h, null);
            g.setColor(Theme.window());
            g.fillRoundRect(w / 2 - 70, h / 2 - 50, 140, 84, 6, 6);
            g.setColor(Theme.accent);
            g.fillRoundRect(w / 2 - 58, h / 2 + 12, 46, 12, 4, 4);
            g.setColor(Theme.dim());
            g.fillRoundRect(w / 2 - 58, h / 2 - 36, 90, 6, 3, 3);
            g.fillRoundRect(w / 2 - 58, h / 2 - 24, 64, 6, 3, 3);
            g.setColor(Theme.shell());
            g.fillRect(0, h - 16, w, 16);
            Icons.draw(g, "logo", w / 2 - 6, h - 14, 12);
            g.dispose();
        }
    }

    class Thumb extends JComponent {
        final int idx;
        BufferedImage img;

        Thumb(int idx) {
            this.idx = idx;
            setPreferredSize(new Dimension(150, 104));
            setToolTipText(Wallpaper.NAMES[idx]);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { Theme.wallpaper = idx; apply(); }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            if (img == null) img = Wallpaper.render(idx, 288, 162);
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            boolean sel = Theme.wallpaper == idx;
            Shape clip = new java.awt.geom.RoundRectangle2D.Double(3, 3, 144, 81, 8, 8);
            g.setClip(clip);
            g.drawImage(img, 3, 3, 144, 81, null);
            g.setClip(null);
            if (sel) {
                g.setColor(Theme.accent);
                g.setStroke(new BasicStroke(3f));
                g.drawRoundRect(1, 1, 147, 84, 10, 10);
            }
            g.setFont(Theme.font(Font.PLAIN, 12f));
            g.setColor(sel ? Theme.text() : Theme.dim());
            g.drawString(Wallpaper.NAMES[idx], 4, 102);
            g.dispose();
        }
    }

    class Dot extends JComponent {
        final Color c;

        Dot(Color c) {
            this.c = c;
            setPreferredSize(new Dimension(30, 30));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { Theme.accent = c; apply(); }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            g.setColor(c);
            g.fillOval(4, 4, 22, 22);
            if (Theme.accent.getRGB() == c.getRGB()) {
                g.setColor(Theme.text());
                g.setStroke(new BasicStroke(2f));
                g.drawOval(1, 1, 27, 27);
            }
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- учётная запись

    JComponent account() {
        JPanel col = column();
        JTextField name = UI.field(Theme.user);
        name.setColumns(18);
        Runnable save = () -> {
            String s = name.getText().trim();
            if (s.isEmpty()) return;
            Theme.user = s;
            apply();
            d.toast("Учётная запись", "Имя изменено на «" + s + "»");
        };
        name.addActionListener(e -> save.run());
        col.add(card("Имя пользователя", "Показывается на экране входа и в меню «Пуск»", UI.row(8, name, UI.accent("Сохранить", save))));
        gap(col, 8);
        col.add(card("Вход в систему", "Без пароля — это локальная учётная запись", null));
        gap(col, 8);
        col.add(card("Выйти из системы", "Все окна будут закрыты", UI.button("Выйти", Main::logout)));
        return col;
    }

    class UserCard extends JComponent {
        UserCard() {
            setPreferredSize(new Dimension(220, 70));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
            setAlignmentX(LEFT_ALIGNMENT);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            Icons.draw(g, "user", 6, 6, 56);
            g.setColor(Theme.text());
            g.setFont(Theme.font(Font.BOLD, 14f));
            UI.text(g, Theme.user, 74, 30, getWidth() - 80);
            g.setColor(Theme.dim());
            g.setFont(Theme.font(Font.PLAIN, 12f));
            g.drawString("Локальная учётная запись", 74, 50);
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- система

    JComponent system() {
        JPanel col = column();
        Runtime rt = Runtime.getRuntime();
        long total = 0;
        try {
            total = ((com.sun.management.OperatingSystemMXBean) java.lang.management.ManagementFactory.getOperatingSystemMXBean()).getTotalMemorySize();
        } catch (Throwable ignored) {}
        long free = 0;
        try { free = Files.getFileStore(Theme.root).getUsableSpace(); } catch (Exception ignored) {}
        String host = System.getenv("COMPUTERNAME");
        col.add(card("Имя устройства", host == null ? "JAVAWIN-PC" : host, null));
        gap(col, 8);
        col.add(card("Система", Theme.NAME + " " + Theme.VERSION + " · ядро Java " + Runtime.version(), UI.button("О системе", () -> d.open("about"))));
        gap(col, 8);
        col.add(card("Процессор", rt.availableProcessors() + " логических ядер · " + System.getProperty("os.arch"), null));
        gap(col, 8);
        col.add(card("Оперативная память", total > 0 ? Vfs.size(total) : "неизвестно", UI.button("Диспетчер задач", () -> d.open("taskmgr"))));
        gap(col, 8);
        col.add(card("Диск C:", Theme.root + " · свободно " + Vfs.size(free), UI.button("Открыть", () -> d.open("explorer", Vfs.root()))));
        gap(col, 8);
        col.add(card("Снимок экрана", "Сохраняется в «Рисунки». Горячая клавиша: Ctrl+Shift+S", UI.button("Сделать", d::screenshot)));
        return col;
    }
}
