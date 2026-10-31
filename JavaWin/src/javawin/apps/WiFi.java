package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

// Окно «Wi-Fi»: список сетей, подключение с паролем. Всё делает /opt/javaos/wifi.sh (путь — -Djavaos.wifi).
public class WiFi extends JPanel implements AppBody {
    record Net(int signal, boolean secure, String name) {}

    final Desktop d;
    final UI.Label status = UI.title("Проверяю подключение…", 15f);
    final UI.Label sub = UI.dim(" ");
    final JPanel list = new JPanel();
    final UI.Btn refresh = UI.button("Обновить", this::scan);
    String open;   // сеть, у которой раскрыто поле пароля
    volatile boolean busy;

    public WiFi(Desktop d, java.nio.file.Path arg) {
        super(new BorderLayout());
        this.d = d;
        setBackground(Theme.window());
        JPanel head = new JPanel(new BorderLayout(10, 4));
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(16, 18, 10, 18));
        JPanel txt = new JPanel(new GridLayout(2, 1, 0, 2));
        txt.setOpaque(false);
        txt.add(status);
        txt.add(sub);
        head.add(txt, BorderLayout.CENTER);
        head.add(refresh, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(new EmptyBorder(0, 12, 12, 12));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(list, BorderLayout.NORTH);
        add(UI.scroll(top), BorderLayout.CENTER);

        if (script() == null) {
            status.setText("Wi-Fi работает только в JavaOS");
            sub.setText("Здесь интернетом управляет Windows.");
            refresh.setEnabled(false);
            return;
        }
        updateStatus();
        scan();
    }

    static String script() {
        String s = System.getProperty("javaos.wifi");
        return s != null && Files.isExecutable(Paths.get(s)) ? s : null;
    }

    static List<String> run(String... args) {
        List<String> out = new ArrayList<>();
        List<String> cmd = new ArrayList<>();
        cmd.add(script());
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getOutputStream().close();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) out.add(l);
            }
            p.waitFor();
        } catch (Exception e) {
            out.add("ERR: " + e);
        }
        return out;
    }

    void updateStatus() {
        new Thread(() -> {
            List<String> r = run("status");
            String[] s = (r.isEmpty() ? "none" : r.get(0)).split("\\|");
            SwingUtilities.invokeLater(() -> {
                if (s[0].equals("wifi")) { status.setText("Подключено: " + s[1]); sub.setText("Wi-Fi · адрес " + (s.length > 2 ? s[2] : "")); }
                else if (s[0].equals("eth")) { status.setText("Подключено по проводу"); sub.setText("Адрес " + (s.length > 2 ? s[2] : "") + ". Можно выбрать и Wi-Fi."); }
                else { status.setText("Нет подключения к интернету"); sub.setText("Выбери сеть Wi-Fi ниже или подключи кабель."); }
            });
        }, "wifi-status").start();
    }

    void scan() {
        if (busy) return;
        busy = true;
        refresh.setEnabled(false);
        list.removeAll();
        list.add(note("Ищу сети Wi-Fi…"));
        list.revalidate();
        list.repaint();
        new Thread(() -> {
            List<String> r = run("scan");
            List<Net> nets = new ArrayList<>();
            String err = null;
            for (String l : r) {
                if (l.startsWith("ERR:")) { err = l.substring(4).trim(); break; }
                String[] p = l.split("\\|", 3);
                if (p.length == 3 && !p[2].isBlank()) {
                    try { nets.add(new Net(Integer.parseInt(p[0].trim()), p[1].equals("1"), p[2])); } catch (NumberFormatException ignored) {}
                }
            }
            String e = err;
            SwingUtilities.invokeLater(() -> {
                busy = false;
                refresh.setEnabled(true);
                list.removeAll();
                if (e != null) list.add(note(e));
                else if (nets.isEmpty()) list.add(note("Сетей не найдено. Нажми «Обновить» через пару секунд."));
                for (Net n : nets) list.add(tag(row(n), n));
                list.revalidate();
                list.repaint();
            });
        }, "wifi-scan").start();
    }

    JComponent note(String s) {
        UI.Label l = UI.label(s);
        l.setBorder(new EmptyBorder(14, 8, 14, 8));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    JComponent row(Net n) {
        JPanel card = new JPanel(new BorderLayout(0, 8)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                UI.aa(g);
                g.setColor(Theme.panel());
                g.fillRoundRect(0, 2, getWidth(), getHeight() - 4, 10, 10);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(10, 12, 10, 12));
        card.setAlignmentX(LEFT_ALIGNMENT);

        JPanel line = new JPanel(new BorderLayout(10, 0));
        line.setOpaque(false);
        JComponent bars = new JComponent() {
            { setPreferredSize(new Dimension(26, 22)); }
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                UI.aa(g);
                int lit = n.signal() >= 75 ? 4 : n.signal() >= 50 ? 3 : n.signal() >= 25 ? 2 : 1;
                for (int i = 0; i < 4; i++) {
                    g.setColor(i < lit ? Theme.text() : Theme.dim());
                    int h = 5 + i * 4;
                    g.fillRoundRect(2 + i * 6, 20 - h, 4, h, 2, 2);
                }
                g.dispose();
            }
        };
        line.add(bars, BorderLayout.WEST);
        JPanel nm = new JPanel(new GridLayout(2, 1));
        nm.setOpaque(false);
        nm.add(UI.label(n.name()));
        nm.add(UI.dim(n.secure() ? "Нужен пароль" : "Открытая сеть, без пароля"));
        line.add(nm, BorderLayout.CENTER);
        UI.Btn go = n.secure() ? UI.button("Подключиться", null) : UI.accent("Подключиться", null);
        line.add(go, BorderLayout.EAST);
        card.add(line, BorderLayout.NORTH);

        if (n.secure() && n.name().equals(open)) {
            JPanel pw = new JPanel(new BorderLayout(8, 0));
            pw.setOpaque(false);
            JPasswordField f = new JPasswordField();
            f.setPreferredSize(new Dimension(220, 32));
            UI.Btn ok = UI.accent("ОК", null);
            UI.Label msg = UI.dim("Пароль от Wi-Fi обычно написан на наклейке роутера.");
            Runnable connect = () -> connect(n, new String(f.getPassword()), msg, ok, f);
            ok.addActionListener(e -> connect.run());
            f.addActionListener(e -> connect.run());
            pw.add(f, BorderLayout.CENTER);
            pw.add(ok, BorderLayout.EAST);
            JPanel box = new JPanel(new BorderLayout(0, 6));
            box.setOpaque(false);
            box.add(pw, BorderLayout.NORTH);
            box.add(msg, BorderLayout.SOUTH);
            card.add(box, BorderLayout.CENTER);
            SwingUtilities.invokeLater(f::requestFocusInWindow);
        }
        go.addActionListener(e -> {
            if (n.secure()) { open = n.name().equals(open) ? null : n.name(); rebuildRows(); }
            else connect(n, "", null, go, null);
        });
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 60));
        return card;
    }

    // перерисовать список, не сканируя заново
    void rebuildRows() {
        List<Net> nets = new ArrayList<>();
        for (Component c : list.getComponents()) if (c instanceof JComponent jc && jc.getClientProperty("net") instanceof Net n) nets.add(n);
        list.removeAll();
        for (Net n : nets) list.add(tag(row(n), n));
        list.revalidate();
        list.repaint();
    }

    JComponent tag(JComponent c, Net n) { c.putClientProperty("net", n); return c; }

    void connect(Net n, String pass, UI.Label msg, JComponent btn, JPasswordField f) {
        if (n.secure() && pass.length() < 8) { if (msg != null) msg.setText("В пароле Wi-Fi не меньше 8 символов."); return; }
        btn.setEnabled(false);
        if (f != null) f.setEnabled(false);
        if (msg != null) msg.setText("Подключаюсь… до 30 секунд");
        status.setText("Подключаюсь к «" + n.name() + "»…");
        new Thread(() -> {
            List<String> r = n.secure() ? run("connect", n.name(), pass) : run("connect", n.name());
            String last = r.isEmpty() ? "ERR: нет ответа" : r.get(r.size() - 1);
            for (String l : r) if (l.startsWith("ERR:") || l.startsWith("OK:")) last = l;
            String res = last;
            SwingUtilities.invokeLater(() -> {
                btn.setEnabled(true);
                if (f != null) f.setEnabled(true);
                if (res.startsWith("OK:")) {
                    open = null;
                    d.toast("Wi-Fi", res.substring(3).trim());
                    rebuildRows();
                } else if (msg != null) msg.setText(res.replaceFirst("^ERR:\\s*", ""));
                else d.toast("Wi-Fi", res.replaceFirst("^ERR:\\s*", ""));
                updateStatus();
            });
        }, "wifi-connect").start();
    }
}
