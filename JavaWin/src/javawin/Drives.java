package javawin;

import javax.swing.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// Флешки в JavaOS: раз в 3 секунды /opt/javaos/usb.sh подключает новые USB-диски, а мы кладём в «Этот компьютер»
// папку-ссылку «Флешка (ИМЯ)». Вставили — уведомление и Проводник; вынули — папка исчезает.
public final class Drives {
    static final Map<String, Path> links = new HashMap<>();   // раздел -> ссылка в C:\

    static void start() {
        String s = System.getProperty("javaos.usb");
        if (s == null) return;
        Thread t = new Thread(() -> loop(s), "usb-drives");
        t.setDaemon(true);
        t.start();
    }

    static void loop(String script) {
        // ссылки, оставшиеся с прошлого раза (флешку вынули при выключенном компьютере)
        try (var st = Files.list(Vfs.root())) {
            st.filter(p -> Files.isSymbolicLink(p) && p.getFileName().toString().startsWith("Флешка (")).forEach(p -> {
                try { Files.delete(p); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
        boolean first = true;
        while (true) {
            try {
                Map<String, String[]> now = new HashMap<>();
                Process p = new ProcessBuilder(script).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                    for (String l; (l = r.readLine()) != null; ) {
                        String[] f = l.split("\\|", 3);
                        if (f.length == 3) now.put(f[0], f);
                    }
                }
                p.waitFor();
                for (String dev : Set.copyOf(links.keySet())) {
                    if (now.containsKey(dev)) continue;
                    Path l = links.remove(dev);
                    try { Files.deleteIfExists(l); } catch (Exception ignored) {}
                    String name = l.getFileName().toString();
                    SwingUtilities.invokeLater(() -> { if (Main.desktop != null) Main.desktop.toast("Флешка", "«" + name + "» отключена"); });
                }
                Set<String> taken = new HashSet<>();
                for (Path l : links.values()) taken.add(l.getFileName().toString());
                for (String[] f : now.values()) {
                    if (links.containsKey(f[0])) continue;
                    String name = "Флешка (" + f[1].replaceAll("[/\\\\:*?\"<>|]", "_") + ")";
                    if (taken.contains(name)) name = "Флешка (" + f[1] + ", " + f[0] + ")";
                    Path l = Vfs.root().resolve(name);
                    try {
                        Files.deleteIfExists(l);
                        Files.createSymbolicLink(l, Path.of(f[2]));
                    } catch (Exception e) { continue; }
                    links.put(f[0], l);
                    taken.add(name);
                    boolean announce = !first;   // при запуске системы окна не открываем — только по вставке
                    String n = name;
                    SwingUtilities.invokeLater(() -> {
                        if (Main.desktop == null) return;
                        Main.desktop.toast("Флешка подключена", n + " — открываю в Проводнике");
                        if (announce) Main.desktop.open("explorer", l);
                    });
                }
                first = false;
                Thread.sleep(3000);
            } catch (Exception e) {
                return;
            }
        }
    }
}
