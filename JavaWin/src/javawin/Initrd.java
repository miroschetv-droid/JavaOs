package javawin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

// Сборка initrd для Linux: архив cpio (формат newc), сжатый gzip.
// Ядро распаковывает его поверх основного initrd, так в систему попадают Java и JavaWin.
//
//   initrd <out.gz> [--tar <архив.tar.gz> <папка>] [--file <файл> <путь> <права>] [--dir <путь>]
//                   [--symlink <путь> <цель>]
//
// --tar берёт содержимое архива, отбрасывает первый каталог и кладёт под <папка>, сохраняя
// права и симлинки (на Windows их иначе не сохранить — поэтому прямо из tar, без распаковки).
public final class Initrd {
    final OutputStream out;
    final Set<String> dirs = new HashSet<>();
    final Map<String, byte[]> files = new HashMap<>();   // для жёстких ссылок внутри tar
    int ino = 1000, count;
    long bytes;

    Initrd(OutputStream out) { this.out = out; }

    public static void main(String[] a) throws IOException {
        Path outPath = Paths.get(a[0]);
        try (OutputStream o = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(outPath), 1 << 20), 1 << 16)) {
            Initrd c = new Initrd(o);
            for (int i = 1; i < a.length; i++) {
                switch (a[i]) {
                    case "--tar" -> c.tar(Paths.get(a[++i]), a[++i]);
                    case "--file" -> {
                        Path src = Paths.get(a[++i]);
                        String dest = a[++i];
                        int mode = Integer.parseInt(a[++i], 8);
                        c.parents(dest);
                        c.file(dest, mode, Files.readAllBytes(src));
                    }
                    case "--dir" -> c.mkdirs(a[++i]);
                    case "--symlink" -> {
                        String dest = a[++i];
                        c.parents(dest);
                        c.symlink(dest, a[++i]);
                    }
                    default -> throw new IllegalArgumentException(a[i]);
                }
            }
            c.entry("TRAILER!!!", 0, new byte[0], 1);
            System.out.printf("initrd: %s (%d entries, %.1f MB before gzip)%n", outPath, c.count, c.bytes / 1048576.0);
        }
        System.out.printf("        %.1f MB after gzip%n", Files.size(outPath) / 1048576.0);
    }

    // ---------------------------------------------------------------- cpio newc

    void entry(String name, int mode, byte[] data, int nlink) throws IOException {
        byte[] n = (name + "\0").getBytes(StandardCharsets.UTF_8);
        String h = String.format("070701%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X%08X",
            name.equals("TRAILER!!!") ? 0 : ino++, mode, 0, 0, nlink, 0, data.length, 0, 0, 0, 0, n.length, 0);
        write(h.getBytes(StandardCharsets.US_ASCII));
        write(n);
        pad(110 + n.length);
        write(data);
        pad(data.length);
        count++;
    }

    void write(byte[] b) throws IOException {
        out.write(b);
        bytes += b.length;
    }

    void pad(long len) throws IOException {
        int p = (int) ((4 - len % 4) % 4);
        if (p > 0) write(new byte[p]);
    }

    void mkdirs(String path) throws IOException {
        parents(path + "/x");
    }

    void parents(String path) throws IOException {
        String[] parts = path.split("/");
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) {
            if (b.length() > 0) b.append('/');
            b.append(parts[i]);
            dir(b.toString(), 0755);
        }
    }

    void dir(String path, int perm) throws IOException {
        if (dirs.add(path)) entry(path, 0040000 | perm, new byte[0], 2);
    }

    void file(String path, int perm, byte[] data) throws IOException {
        entry(path, 0100000 | perm, data, 1);
    }

    void symlink(String path, String target) throws IOException {
        entry(path, 0120777, target.getBytes(StandardCharsets.UTF_8), 1);
    }

    // ---------------------------------------------------------------- tar.gz → cpio

    void tar(Path archive, String destDir) throws IOException {
        mkdirs(destDir);
        try (InputStream in = new BufferedInputStream(new GZIPInputStream(Files.newInputStream(archive), 1 << 16), 1 << 16)) {
            byte[] h = new byte[512];
            String longName = null, longLink = null;
            Map<String, String> pax = new HashMap<>();
            while (true) {
                if (!readFully(in, h)) break;
                if (isZero(h)) break;
                String name = str(h, 0, 100);
                String prefix = str(h, 345, 155);
                if (!prefix.isEmpty() && h[257] == 'u') name = prefix + "/" + name;
                int mode = (int) octal(h, 100, 8) & 07777;
                long size = octal(h, 124, 12);
                char type = (char) h[156];
                String link = str(h, 157, 100);
                byte[] data = readData(in, size);

                if (type == 'L') { longName = cstr(data); continue; }
                if (type == 'K') { longLink = cstr(data); continue; }
                if (type == 'x') { pax = parsePax(data); continue; }
                if (type == 'g') continue;
                if (longName != null) name = longName;
                if (longLink != null) link = longLink;
                if (pax.containsKey("path")) name = pax.get("path");
                if (pax.containsKey("linkpath")) link = pax.get("linkpath");
                longName = longLink = null;
                pax = new HashMap<>();

                String rel = strip(name);
                if (rel.isEmpty()) continue;
                String dest = destDir + "/" + rel;
                parents(dest);
                switch (type) {
                    case '5' -> dir(dest, mode == 0 ? 0755 : mode);
                    case '2' -> symlink(dest, link);
                    case '1' -> {
                        byte[] target = files.get(destDir + "/" + strip(link));
                        file(dest, mode == 0 ? 0644 : mode, target == null ? new byte[0] : target);
                    }
                    case '0', '\0', '7' -> {
                        file(dest, mode, data);
                        files.put(dest, data);
                    }
                    default -> {}
                }
            }
        }
        files.clear();
    }

    static String strip(String name) {
        while (name.startsWith("./")) name = name.substring(2);
        if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
        int slash = name.indexOf('/');
        return slash < 0 ? "" : name.substring(slash + 1);
    }

    static boolean readFully(InputStream in, byte[] b) throws IOException {
        int off = 0;
        while (off < b.length) {
            int r = in.read(b, off, b.length - off);
            if (r < 0) return false;
            off += r;
        }
        return true;
    }

    static byte[] readData(InputStream in, long size) throws IOException {
        byte[] d = new byte[(int) size];
        if (!readFully(in, d)) throw new EOFException();
        long padding = (512 - size % 512) % 512;
        in.skipNBytes(padding);
        return d;
    }

    static boolean isZero(byte[] h) {
        for (byte x : h) if (x != 0) return false;
        return true;
    }

    static String str(byte[] h, int off, int len) {
        int end = off;
        while (end < off + len && h[end] != 0) end++;
        return new String(h, off, end - off, StandardCharsets.UTF_8);
    }

    static String cstr(byte[] d) {
        int end = 0;
        while (end < d.length && d[end] != 0) end++;
        return new String(d, 0, end, StandardCharsets.UTF_8);
    }

    static long octal(byte[] h, int off, int len) {
        if ((h[off] & 0x80) != 0) {             // двоичный формат GNU для больших чисел
            long v = 0;
            for (int i = off + 1; i < off + len; i++) v = (v << 8) | (h[i] & 0xff);
            return v;
        }
        String s = str(h, off, len).trim();
        return s.isEmpty() ? 0 : Long.parseLong(s, 8);
    }

    // записи вида "<длина> ключ=значение\n"
    static Map<String, String> parsePax(byte[] d) {
        Map<String, String> m = new HashMap<>();
        int i = 0;
        while (i < d.length) {
            int sp = i;
            while (sp < d.length && d[sp] != ' ') sp++;
            if (sp >= d.length) break;
            int len = Integer.parseInt(new String(d, i, sp - i, StandardCharsets.US_ASCII));
            String rec = new String(d, sp + 1, len - (sp - i) - 2, StandardCharsets.UTF_8);
            int eq = rec.indexOf('=');
            if (eq > 0) m.put(rec.substring(0, eq), rec.substring(eq + 1));
            i += len;
        }
        return m;
    }
}
