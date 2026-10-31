package javawin;

import java.awt.Toolkit;
import java.awt.datatransfer.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

// Посредник общего буфера обмена Windows ↔ JavaOS. Запускается рядом с виртуалкой (Start VM.bat):
//   javaw -cp JavaWin.jar javawin.ClipHost
// Слушает только 127.0.0.1:7777 — гость QEMU попадает сюда по адресу 10.0.2.2, из сети не достучаться.
//   GET  /clip  → "<номер>\n<текст>"  (номер растёт при каждом изменении буфера Windows)
//   POST /clip  ← текст из гостя кладётся в буфер Windows
// Сам закрывается, когда не остаётся запущенных QEMU.
public final class ClipHost {
    public static final int PORT = 7777;
    static volatile String text = "";
    static volatile long seq;

    public static void main(String[] a) throws Exception {
        ServerSocket server;
        try {
            server = new ServerSocket(PORT, 16, InetAddress.getLoopbackAddress());
        } catch (BindException e) {
            return;   // уже работает
        }
        Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
        text = read(cb);

        Thread watch = new Thread(() -> {
            int noQemu = 0;
            while (true) {
                try {
                    Thread.sleep(400);
                    String t = read(cb);
                    if (t != null && !t.equals(text)) { text = t; seq++; }
                    boolean qemu = ProcessHandle.allProcesses().anyMatch(p ->
                        p.info().command().map(c -> c.toLowerCase().contains("qemu-system")).orElse(false));
                    noQemu = qemu ? 0 : noQemu + 1;
                    if (noQemu > 50) System.exit(0);   // ~20 секунд без виртуалки
                } catch (Exception ignored) {}
            }
        }, "clip-watch");
        watch.setDaemon(true);
        watch.start();

        while (true) {
            try (Socket s = server.accept()) {
                s.setSoTimeout(3000);
                handle(s, cb);
            } catch (Exception ignored) {}
        }
    }

    static final String TABLET = "javaos-tablet";
    static final int MONITOR_PORT = 4444;   // монитор QEMU из Start VM.bat, только 127.0.0.1

    // одна фиксированная команда монитору QEMU; ответ — для сообщения об ошибке
    static String monitor(String cmd) {
        try (Socket m = new Socket(InetAddress.getLoopbackAddress(), MONITOR_PORT)) {
            m.setSoTimeout(1500);
            OutputStream o = m.getOutputStream();
            o.write((cmd + "\n").getBytes(StandardCharsets.US_ASCII));
            o.flush();
            Thread.sleep(400);
            InputStream in = m.getInputStream();
            byte[] buf = in.readNBytes(Math.min(in.available(), 8192));
            return new String(buf, StandardCharsets.UTF_8).replaceAll("\u001b\\[[0-9;]*[A-Za-z]", "");
        } catch (Exception e) {
            return "Error: нет связи с монитором QEMU (" + e.getMessage() + ")";
        }
    }

    static String read(Clipboard cb) {
        try {
            if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) return (String) cb.getData(DataFlavor.stringFlavor);
        } catch (Exception ignored) {}
        return null;
    }

    static void handle(Socket s, Clipboard cb) throws IOException {
        InputStream in = new BufferedInputStream(s.getInputStream());
        String request = line(in);
        if (request == null) return;
        int length = 0;
        for (String h; (h = line(in)) != null && !h.isEmpty(); )
            if (h.toLowerCase().startsWith("content-length:")) length = Integer.parseInt(h.substring(15).trim());
        String body = "";
        if (request.startsWith("POST /clip")) {
            if (length > 4 << 20) { reply(s, "413 Too Large", ""); return; }
            byte[] b = in.readNBytes(length);
            String t = new String(b, StandardCharsets.UTF_8);
            try {
                cb.setContents(new StringSelection(t), null);
                text = t;
                seq++;
            } catch (Exception e) { reply(s, "500 Error", ""); return; }
        } else if (request.startsWith("GET /clip")) {
            body = seq + "\n" + (text == null ? "" : text);
        } else if (request.startsWith("POST /mouse/game") || request.startsWith("POST /mouse/normal")) {
            // игровой режим: убрать USB-планшет (абсолютные координаты), остаётся обычная мышь
            // с относительными движениями — так камера в играх крутится плавно
            boolean game = request.startsWith("POST /mouse/game");
            String out = monitor(game ? "device_del " + TABLET : "device_add usb-tablet,id=" + TABLET);
            boolean ok = !out.contains("rror");
            reply(s, ok ? "200 OK" : "502 Bad Gateway", ok ? (game ? "game" : "normal") : out);
            return;
        } else { reply(s, "404 Not Found", ""); return; }
        reply(s, "200 OK", body);
    }

    static void reply(Socket s, String status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        OutputStream o = s.getOutputStream();
        o.write(("HTTP/1.0 " + status + "\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: " + b.length
            + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        o.write(b);
        o.flush();
    }

    static String line(InputStream in) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1 && c != '\n') if (c != '\r') b.write(c);
        return c == -1 && b.size() == 0 ? null : b.toString(StandardCharsets.UTF_8);
    }
}
