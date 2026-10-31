package javawin;

import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// Гостевая половина общего буфера обмена (JavaOS в QEMU). Раз в 0,7 с сверяет буфер обмена X
// (его же видят Firefox и прочие программы Linux) с буфером Windows через ClipHost на 10.0.2.2.
// Посредник не запущен — молча ждёт, ничего не ломая.
public final class ClipSync implements Runnable {
    static final String URL = "http://10.0.2.2:" + ClipHost.PORT + "/clip";
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(400)).build();
    final Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
    long hostSeq = -1;
    String lastGuest, lastHost;

    public static volatile boolean gameMouse;

    // переключить мышь виртуалки: true — обычная (относительная, для игр), false — планшет.
    // Возвращает null при успехе или текст ошибки.
    public static String mouse(boolean game) {
        try {
            HttpResponse<String> r = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build().send(
                HttpRequest.newBuilder(URI.create("http://10.0.2.2:" + ClipHost.PORT + "/mouse/" + (game ? "game" : "normal")))
                    .timeout(Duration.ofSeconds(4)).POST(HttpRequest.BodyPublishers.noBody()).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (r.statusCode() == 200) { gameMouse = game; return null; }
            String b = r.body();
            if (b.contains("not found") && game) { gameMouse = true; return null; }     // планшета и так нет
            if (b.contains("Duplicate") && !game) { gameMouse = false; return null; }   // уже подключён
            return b.isBlank() ? "код " + r.statusCode() : b.trim();
        } catch (Exception e) {
            return "нет связи с Windows — запусти JavaOS через Start VM.bat (ярлык JavaOS)";
        }
    }

    static void start() {
        Thread t = new Thread(new ClipSync(), "clip-sync");
        t.setDaemon(true);
        t.start();
    }

    public void run() {
        lastGuest = read();
        while (true) {
            try {
                Thread.sleep(700);
                // гость → Windows
                String g = read();
                if (g != null && !g.equals(lastGuest)) {
                    lastGuest = g;
                    if (!g.equals(lastHost)) {
                        http.send(HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(2))
                            .POST(HttpRequest.BodyPublishers.ofString(g, StandardCharsets.UTF_8)).build(),
                            HttpResponse.BodyHandlers.discarding());
                        lastHost = g;
                    }
                }
                // Windows → гость
                HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofSeconds(2)).build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (r.statusCode() != 200) continue;
                String body = r.body();
                int nl = body.indexOf('\n');
                if (nl < 0) continue;
                long seq = Long.parseLong(body.substring(0, nl).trim());
                String text = body.substring(nl + 1);
                if (seq != hostSeq) {
                    boolean first = hostSeq < 0;
                    hostSeq = seq;
                    lastHost = text;
                    // при первом подключении буфер гостя не затираем — берём только новые копирования
                    if (!first && !text.equals(read())) {
                        cb.setContents(new StringSelection(text), null);
                        lastGuest = text;
                    }
                }
            } catch (InterruptedException e) {
                return;
            } catch (Exception ignored) {
                // посредника нет или он перезапускается — попробуем позже
            }
        }
    }

    String read() {
        try {
            if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) return (String) cb.getData(DataFlavor.stringFlavor);
        } catch (Exception ignored) {}
        return null;
    }
}
