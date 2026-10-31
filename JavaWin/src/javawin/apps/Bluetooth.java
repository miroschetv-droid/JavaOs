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

// Окно «Bluetooth»: поиск устройств, подключение и отключение. Всё делает /opt/javaos/bt.sh (путь — -Djavaos.bt).
public class Bluetooth extends JPanel implements AppBody {
    record Dev(String mac, boolean connected, boolean paired, String name) {}

    final Desktop d;
    final UI.Label status = UI.title("Проверяю Bluetooth…", 15f);
    final UI.Label sub = UI.dim(" ");
    final JPanel list = new JPanel();
    final UI.Btn search = UI.button("Найти устройства", this::scan);
    volatile boolean busy;
    final java.util.Map<String, String> modes = new java.util.concurrent.ConcurrentHashMap<>();   // MAC -> music|headset|none

    public Bluetooth(Desktop d, java.nio.file.Path arg) {
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
        head.add(search, BorderLayout.EAST);
        add(head, BorderLayout.NORTH);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        list.setBorder(new EmptyBorder(0, 12, 12, 12));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(list, BorderLayout.NORTH);
        add(UI.scroll(top), BorderLayout.CENTER);

        if (script() == null) {
            status.setText("Bluetooth работает только в JavaOS");
            sub.setText("Здесь Bluetooth управляет Windows.");
            search.setEnabled(false);
            return;
        }
        refresh("start");
    }

    static String script() {
        String s = System.getProperty("javaos.bt");
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
                for (String l; (l = r.readLine()) != null; ) out.add(l);
            }
            p.waitFor();
        } catch (Exception e) {
            out.add("ERR: " + e);
        }
        return out;
    }

    // first: «start» (включить и показать знакомые устройства) или «scan» (искать новые)
    void refresh(String first) {
        if (busy) return;
        busy = true;
        search.setEnabled(false);
        list.removeAll();
        list.add(note(first.equals("scan") ? "Ищу устройства… 10 секунд. Включи на устройстве режим сопряжения." : "Включаю Bluetooth…"));
        list.revalidate();
        list.repaint();
        new Thread(() -> {
            List<String> r = run(first);
            List<String> devs = first.equals("scan") ? r : run("list");
            String st = run("status").stream().findFirst().orElse("none");
            String err = null;
            List<Dev> found = new ArrayList<>();
            for (String l : r) if (l.startsWith("ERR:")) err = l.substring(4).trim();
            for (String l : devs) {
                String[] p = l.split("\\|", 4);
                if (p.length == 4) found.add(new Dev(p[0], p[1].equals("1"), p[2].equals("1"), p[3]));
            }
            for (Dev dv : found)
                if (dv.connected()) { List<String> m = run("mode", dv.mac()); modes.put(dv.mac(), m.isEmpty() ? "none" : m.get(0).trim()); }
            found.sort((a, b) -> Boolean.compare(b.connected() || b.paired(), a.connected() || a.paired()));
            String e = err;
            SwingUtilities.invokeLater(() -> {
                busy = false;
                search.setEnabled(true);
                if (st.equals("none")) { status.setText("Bluetooth-адаптер не найден"); sub.setText("Возможно, для этого адаптера нет драйвера."); }
                else if (st.startsWith("on")) { status.setText("Bluetooth включён"); sub.setText("Имя компьютера: " + (st.contains("|") ? st.substring(3) : "JavaOS")); }
                else { status.setText("Bluetooth выключен"); sub.setText("Нажми «Найти устройства» — он включится."); }
                list.removeAll();
                if (e != null) list.add(note(e));
                else if (found.isEmpty()) list.add(note("Устройств нет. Включи на наушниках, мыши или геймпаде режим сопряжения и нажми «Найти устройства»."));
                for (Dev dv : found) list.add(row(dv));
                list.revalidate();
                list.repaint();
            });
        }, "bt-refresh").start();
    }

    void scan() { refresh("scan"); }

    JComponent note(String s) {
        UI.Label l = UI.label(s);
        l.setBorder(new EmptyBorder(14, 8, 14, 8));
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    JComponent row(Dev dv) {
        JPanel card = new JPanel(new BorderLayout(10, 0)) {
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
        JPanel nm = new JPanel(new GridLayout(2, 1));
        nm.setOpaque(false);
        nm.add(UI.label(dv.name()));
        String mode = modes.getOrDefault(dv.mac(), "none");
        nm.add(UI.dim(!dv.connected() ? (dv.paired() ? "Сопряжено, не подключено" : "Новое устройство")
            : mode.equals("music") ? "Подключено · режим «Музыка» (без микрофона)"
            : mode.equals("headset") ? "Подключено · режим «Гарнитура» (с микрофоном)" : "Подключено"));
        card.add(nm, BorderLayout.CENTER);
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        btns.setOpaque(false);
        if (dv.connected() && mode.equals("music")) btns.add(UI.button("Включить микрофон", () -> setMode(dv, "headset")));
        if (dv.connected() && mode.equals("headset")) btns.add(UI.button("Режим «Музыка»", () -> setMode(dv, "music")));
        if (dv.connected()) btns.add(UI.button("Отключить", () -> act(dv, "disconnect")));
        else btns.add(UI.accent("Подключить", () -> act(dv, "connect")));
        if (dv.paired()) btns.add(UI.button("Забыть", () -> act(dv, "remove")));
        card.add(btns, BorderLayout.EAST);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, card.getPreferredSize().height + 10));
        return card;
    }

    void setMode(Dev dv, String mode) {
        if (busy) return;
        busy = true;
        new Thread(() -> {
            List<String> r = run("mode", dv.mac(), mode);
            String last = r.isEmpty() ? "OK" : r.get(r.size() - 1);
            SwingUtilities.invokeLater(() -> {
                busy = false;
                d.toast("Bluetooth", dv.name() + ": " + last.replaceFirst("^(ERR|OK):\\s*", ""));
                refresh("list");
            });
        }, "bt-mode").start();
    }

    void act(Dev dv, String what) {
        if (busy) return;
        busy = true;
        status.setText(what.equals("connect") ? "Подключаю «" + dv.name() + "»… до 30 секунд" : "Подожди…");
        new Thread(() -> {
            List<String> r = run(what, dv.mac());
            String last = r.isEmpty() ? "OK" : r.get(r.size() - 1);
            SwingUtilities.invokeLater(() -> {
                busy = false;
                d.toast("Bluetooth", dv.name() + ": " + last.replaceFirst("^(ERR|OK):\\s*", ""));
                refresh("list");
            });
        }, "bt-act").start();
    }
}
