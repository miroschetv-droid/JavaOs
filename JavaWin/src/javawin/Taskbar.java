package javawin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Панель задач: «Пуск» и закреплённые программы по центру, часы справа.
public class Taskbar extends JPanel {
    public static final int H = 48;
    static final String[] PINNED = { "explorer", "browser", "notepad", "cmd", "paint", "settings" };

    final Desktop d;
    final JPanel center = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 4));
    final TBtn startBtn;
    final Clock clock = new Clock();
    final JPanel tray = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 4));
    final Timer tick;
    final Rectangle mon = new Rectangle();   // монитор этой панели (у каждого монитора своя)

    Taskbar(Desktop d) {
        super(null);
        this.d = d;
        setOpaque(false);
        center.setOpaque(false);
        tray.setOpaque(false);
        startBtn = new TBtn("logo", null, null);
        startBtn.setToolTipText("Пуск");
        add(center);
        add(tray);
        if (Main.osMode && Main.inVM()) tray.add(new MouseToggle());   // «мышь для игр» — только для виртуалки
        tray.add(new TrayIcons());
        tray.add(clock);
        tray.add(new ShowDesktop());
        tick = new Timer(1000, e -> clock.repaint());
        tick.start();
        refresh();
    }

    void stop() { tick.stop(); }

    @Override public void doLayout() {
        Dimension cp = center.getPreferredSize(), tp = tray.getPreferredSize();
        center.setBounds((getWidth() - cp.width) / 2, 0, cp.width, H);
        tray.setBounds(getWidth() - tp.width, 0, tp.width, H);
    }

    void refresh() {
        center.removeAll();
        center.add(startBtn);
        for (String id : PINNED) center.add(new TBtn(Apps.get(id).icon(), id, null));
        for (Win w : d.wins) {
            if (w.dialog) continue;
            boolean pinned = false;
            for (String id : PINNED) pinned |= id.equals(w.appId);
            if (!pinned) center.add(new TBtn(w.icon, null, w));
        }
        for (XWindows.XWin x : XWindows.list) center.add(new XBtn(x));
        doLayout();
        center.revalidate();
        repaint();
    }

    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(Theme.shell());
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Theme.dark ? new Color(255, 255, 255, 22) : new Color(0, 0, 0, 30));
        g.fillRect(0, 0, getWidth(), 1);
        g.dispose();
    }

    static void hoverBg(Graphics2D g, JComponent c, boolean hover, boolean pressed) {
        if (!hover && !pressed) return;
        g.setColor(Theme.dark ? new Color(255, 255, 255, pressed ? 14 : 22) : new Color(255, 255, 255, pressed ? 120 : 190));
        g.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 8, 8);
    }

    // Кнопка на панели: закреплённая программа (appId) или окно незакреплённой (win), либо «Пуск».
    class TBtn extends JComponent {
        final String icon, appId;
        final Win win;
        boolean hover, down;

        TBtn(String icon, String appId, Win win) {
            this.icon = icon;
            this.appId = appId;
            this.win = win;
            setPreferredSize(new Dimension(44, 40));
            if (appId != null) setToolTipText(Apps.get(appId).title());
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; down = false; repaint(); }
                public void mousePressed(MouseEvent e) { down = true; repaint(); }
                public void mouseReleased(MouseEvent e) {
                    down = false;
                    repaint();
                    if (contains(e.getPoint())) click(e);
                }
            });
        }

        @Override public String getToolTipText() { return win != null ? win.getTitle() : super.getToolTipText(); }

        List<Win> windows() {
            if (win != null) return List.of(win);
            if (appId != null) return d.winsOf(appId);
            return List.of();
        }

        void click(MouseEvent e) {
            if (this == startBtn) { d.toggleStart(Taskbar.this); return; }
            d.startMenu.close();
            List<Win> ws = windows();
            if (SwingUtilities.isRightMouseButton(e)) {
                JPopupMenu m = new JPopupMenu();
                if (appId != null) m.add(Desktop.item("Новое окно: " + Apps.get(appId).title(), () -> d.open(appId)));
                if (!ws.isEmpty()) m.add(Desktop.item(ws.size() > 1 ? "Закрыть все окна" : "Закрыть окно",
                    () -> { for (Win w : List.copyOf(ws)) w.close(); }));
                m.show(this, e.getX(), e.getY() - m.getPreferredSize().height);
                return;
            }
            if (ws.isEmpty()) { d.open(appId); return; }
            if (ws.size() == 1) {
                Win w = ws.get(0);
                if (w == d.active && !w.minimized) d.minimize(w);
                else d.activate(w);
                return;
            }
            JPopupMenu m = new JPopupMenu();
            for (Win w : ws) {
                JMenuItem it = Desktop.item(w.getTitle(), () -> d.activate(w));
                it.setIcon(Icons.icon(w.icon, 16));
                m.add(it);
            }
            m.show(this, 0, -m.getPreferredSize().height - 4);
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            List<Win> ws = windows();
            boolean act = d.active != null && ws.contains(d.active) && !d.active.minimized;
            boolean open = this == startBtn ? d.startMenu.isVisible() : act;
            hoverBg(g, this, hover || open, down);
            int s = 24, off = down ? 1 : 0;
            Icons.draw(g, icon, (getWidth() - s) / 2, (getHeight() - s) / 2 - 1 + off, s);
            if (!ws.isEmpty()) {
                int bw = act ? 16 : 6;
                g.setColor(act ? Theme.accent : Theme.dim());
                g.fillRoundRect((getWidth() - bw) / 2, getHeight() - 4, bw, 3, 3, 3);
            }
            g.dispose();
        }
    }

    // окно программы Linux (не JavaWin): щелчок — показать поверх, правая кнопка — закрыть
    class XBtn extends JComponent {
        final XWindows.XWin x;
        boolean hover;

        XBtn(XWindows.XWin x) {
            this.x = x;
            setPreferredSize(new Dimension(44, 40));
            setToolTipText(x.title());
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseReleased(MouseEvent e) {
                    if (!contains(e.getPoint())) return;
                    d.startMenu.close();
                    if (SwingUtilities.isRightMouseButton(e)) {
                        JPopupMenu m = new JPopupMenu();
                        m.add(Desktop.item("Показать окно", () -> XWindows.activate(x)));
                        m.add(Desktop.item("Закрыть окно", () -> XWindows.close(x)));
                        m.add(Desktop.item("Завершить принудительно", () -> XWindows.kill(x)));
                        m.show(XBtn.this, e.getX(), e.getY() - m.getPreferredSize().height);
                    } else XWindows.activate(x);
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            hoverBg(g, this, hover, false);
            int s = 24;
            Icons.draw(g, "linux", (getWidth() - s) / 2, (getHeight() - s) / 2 - 1, s);
            g.setColor(Theme.dim());
            g.fillRoundRect((getWidth() - 6) / 2, getHeight() - 4, 6, 3, 3, 3);
            g.dispose();
        }
    }

    // «мышь для игр»: в JavaOS переключает мышь виртуалки между планшетом и обычной (см. ClipHost)
    class MouseToggle extends JComponent {
        boolean hover, busy;

        MouseToggle() {
            setPreferredSize(new Dimension(36, 40));
            setToolTipText("мышь");   // регистрирует подсказку, сам текст — в getToolTipText()
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e) { toggle(); }
            });
        }

        @Override public String getToolTipText() {
            return ClipSync.gameMouse ? "Мышь для игр включена — щёлкни, чтобы вернуть обычную"
                : "Мышь для игр (плавная камера в Minecraft и других играх)";
        }

        void toggle() {
            if (busy) return;
            busy = true;
            boolean want = !ClipSync.gameMouse;
            new Thread(() -> {
                String err = ClipSync.mouse(want);
                SwingUtilities.invokeLater(() -> {
                    busy = false;
                    repaint();
                    if (err != null) d.message("Мышь для игр", "Не получилось: " + err);
                    else if (want) d.toast("Мышь для игр включена", "Щёлкни в окно игры — мышь захватится. Отпустить: Ctrl+Alt+G.");
                    else d.toast("Обычная мышь", "Курсор снова свободно ходит между Windows и JavaOS.");
                });
            }, "mouse-toggle").start();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            hoverBg(g, this, hover, false);
            int cx = getWidth() / 2, cy = getHeight() / 2;
            boolean on = ClipSync.gameMouse;
            if (on) {
                g.setColor(Theme.alpha(Theme.accent, 70));
                g.fillRoundRect(3, 6, getWidth() - 6, getHeight() - 12, 8, 8);
            }
            g.setColor(on ? Theme.accent : Theme.text());
            g.setStroke(new BasicStroke(1.6f));
            g.drawRoundRect(cx - 6, cy - 9, 12, 18, 12, 12);
            g.drawLine(cx, cy - 9, cx, cy - 3);
            g.drawLine(cx - 6, cy - 3, cx + 6, cy - 3);
            g.dispose();
        }
    }

    class TrayIcons extends JComponent {
        boolean hover;
        // язык раскладки: X-сервер переключает его сам (Alt+Shift), а узнаём по последней набранной букве
        String lang = "ENG";
        boolean xkbReady;

        TrayIcons() {
            Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
                if (ev.getID() != KeyEvent.KEY_TYPED) return;
                char c = ((KeyEvent) ev).getKeyChar();
                String l = Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CYRILLIC ? "РУС"
                         : (c < 128 && Character.isLetter(c)) ? "ENG" : null;
                if (l != null && !l.equals(lang)) { lang = l; repaint(); }
            }, AWTEvent.KEY_EVENT_MASK);
            setPreferredSize(new Dimension(112, 40));
            setToolTipText(Main.osMode ? "Язык: Alt+Shift или щелчок по ENG/РУС · Сеть и Wi-Fi — щелчок по значкам" : "Сеть: подключено · Звук: 70% · Заряд: 100%");
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e) {
                    if (e.getX() < 38) { switchLang(); return; }   // щелчок по ENG/РУС — сменить язык
                    d.open(Main.osMode ? "wifi" : "settings");
                }
            });
        }

        // X-сервер переключает раскладку по Alt+Shift — нажимаем их за пользователя (через XTest).
        // Помогает, когда Alt+Shift перехватывает Windows (JavaOS в окне QEMU).
        void switchLang() {
            if (!Main.osMode) return;
            try {
                // у «виртуальной клавиатуры» XTest, через которую жмёт Robot, своя раскладка — только us и без Alt+Shift.
                // Перед первым щелчком даём ей раскладку настоящей клавиатуры (us,ru с Alt+Shift)
                if (!xkbReady) {
                    new ProcessBuilder("/bin/sh", "-c", "xkbcomp -xkb :0 /tmp/km.xkb && xkbcomp -w0 -i 5 /tmp/km.xkb :0")
                        .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
                        .waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
                    xkbReady = true;
                }
                Robot r = new Robot();
                r.keyPress(KeyEvent.VK_ALT);
                r.keyPress(KeyEvent.VK_SHIFT);
                r.keyRelease(KeyEvent.VK_SHIFT);
                r.keyRelease(KeyEvent.VK_ALT);
                lang = lang.equals("ENG") ? "РУС" : "ENG";
                repaint();
            } catch (Exception ignored) {}
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            hoverBg(g, this, hover, false);
            g.setFont(Theme.font(Font.PLAIN, 11.5f));
            g.setColor(Theme.text());
            g.drawString(lang, 8, 25);
            Icons.draw(g, "wifi", 42, 12, 16);
            Icons.draw(g, "sound", 64, 12, 16);
            Icons.draw(g, "battery", 86, 12, 16);
            g.dispose();
        }
    }

    class Clock extends JComponent {
        boolean hover;

        Clock() {
            setPreferredSize(new Dimension(82, 40));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mousePressed(MouseEvent e) {
                    d.toggleCalendar(Taskbar.this);
                }
            });
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            hoverBg(g, this, hover || d.calendar.isVisible(), false);
            LocalDateTime now = LocalDateTime.now();
            g.setFont(Theme.font(Font.PLAIN, 12f));
            g.setColor(Theme.text());
            FontMetrics fm = g.getFontMetrics();
            String t = now.format(DateTimeFormatter.ofPattern("HH:mm")), dt = now.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
            g.drawString(t, getWidth() - 10 - fm.stringWidth(t), 18);
            g.drawString(dt, getWidth() - 10 - fm.stringWidth(dt), 34);
            g.dispose();
        }
    }

    class ShowDesktop extends JComponent {
        boolean hover;

        ShowDesktop() {
            setPreferredSize(new Dimension(10, 40));
            setToolTipText("Свернуть все окна");
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mousePressed(MouseEvent e) { d.showDesktop(); }
            });
        }

        @Override protected void paintComponent(Graphics g) {
            g.setColor(hover ? Theme.dim() : Theme.border());
            g.fillRect(5, 10, 1, 20);
        }
    }
}
