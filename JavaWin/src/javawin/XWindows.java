package javawin;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Окна настоящих программ Linux (.jar, программы Arch) — раз в секунду ищем их через xdotool, чтобы показать
// на панели задач, как окна JavaWin. Список окон у JWM (_NET_CLIENT_LIST) бывает пустым, поэтому ищем сами:
// видимые окна с заголовком, не наши и не крошечные служебные. Свёрнутое окно не видно — держим его в списке,
// пока оно существует.
public final class XWindows {
    public record XWin(String id, String title) {}

    static volatile List<XWin> list = List.of();
    static java.util.Set<String> visibleBefore = java.util.Set.of();

    // одна строка на окно: «id pid ширина высота заголовок»
    static final String SCAN = "for w in $(xdotool search --onlyvisible --name . 2>/dev/null); do "
        + "pid=$(xdotool getwindowpid $w 2>/dev/null); [ -n \"$pid\" ] || continue; "   // без процесса — внутренние окна Java и рамки JWM
        + "eval $(xdotool getwindowgeometry --shell $w 2>/dev/null); "
        + "echo \"$w $pid $WIDTH $HEIGHT $(xdotool getwindowname $w 2>/dev/null)\"; done";

    static void start() {
        Thread t = new Thread(XWindows::loop, "x-windows");
        t.setDaemon(true);
        t.start();
    }

    static void loop() {
        String self = String.valueOf(ProcessHandle.current().pid());
        Map<String, String> known = new LinkedHashMap<>();   // id -> заголовок
        while (true) {
            try {
                Map<String, String> seen = new LinkedHashMap<>();
                for (String l : lines("/bin/sh", "-c", SCAN)) {
                    String[] f = l.trim().split(" ", 5);
                    if (f.length < 5 || f[1].equals(self)) continue;
                    if (parse(f[2]) < 60 || parse(f[3]) < 40) continue;   // служебные окошки 1×1 и подсказки
                    seen.put(f[0], f[4]);
                }
                java.util.Set<String> visible = new java.util.HashSet<>(seen.keySet());   // видимые сейчас (до свёрнутых)
                boolean fresh = false;
                for (String id : seen.keySet()) fresh |= !known.containsKey(id);
                // свёрнутые (невидимые) окна, которые ещё существуют
                for (String id : List.copyOf(known.keySet())) {
                    if (seen.containsKey(id)) continue;
                    List<String> name = lines("xdotool", "getwindowname", id);
                    if (name.isEmpty()) known.remove(id); else seen.put(id, known.get(id));
                }
                known.clear();
                known.putAll(seen);
                List<XWin> now = new ArrayList<>();
                seen.forEach((id, title) -> now.add(new XWin(id, title)));
                // появилось новое окно — убираем рабочий стол под него: окно системы без оконного менеджера
                // само вниз не уходит и закрывало бы собой окна программ
                if (fresh) SwingUtilities.invokeLater(XWindows::lowerDesktop);
                // окно пропало с экрана (свернули, закрыли) — перерисовать рабочий стол под ним, иначе остаётся
                // белый прямоугольник: окно системы не получает сигнал, что его снова видно
                boolean gone = false;
                for (String id : visibleBefore) gone |= !visible.contains(id);
                if (gone) SwingUtilities.invokeLater(() -> { if (Main.frame != null) Main.frame.repaint(); });
                visibleBefore = visible;
                if (!now.equals(list)) {
                    list = List.copyOf(now);
                    SwingUtilities.invokeLater(() -> { if (Main.desktop != null) Main.desktop.refreshBars(); });
                }
                Thread.sleep(1000);
            } catch (Exception e) {
                return;   // нет xdotool (Windows) — панель просто без чужих окон
            }
        }
    }

    static int parse(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    static List<String> lines(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            for (String l; (l = r.readLine()) != null; ) if (!l.isBlank()) out.add(l);
        }
        p.waitFor();
        return out;
    }

    static void run(String... cmd) {
        new Thread(() -> {
            try { new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor(); } catch (Exception ignored) {}
        }, "xwin-cmd").start();
    }

    static void lowerDesktop() { if (Main.frame != null) Main.frame.toBack(); }

    // показать окно: развернуть из свёрнутого и поднять наверх (через оконный менеджер)
    static void activate(XWin w) {
        lowerDesktop();
        run("wmctrl", "-i", "-a", w.id());
    }

    static void close(XWin w) { run("wmctrl", "-i", "-c", w.id()); }

    // программа не закрывается (Discord прячется «в трей» и оставляет белое окно) — завершить её процесс
    static void kill(XWin w) {
        run("/bin/sh", "-c", "p=$(xdotool getwindowpid " + w.id() + ") && [ \"$p\" != " + ProcessHandle.current().pid() + " ] && kill -9 \"$p\"");
    }
}
