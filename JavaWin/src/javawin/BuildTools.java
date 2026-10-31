package javawin;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

// Инструменты сборки, тоже на Java:
//   java -cp JavaWin.jar javawin.BuildTools ico <файл.ico>
//   java -cp JavaWin.jar javawin.BuildTools iso <папка> <файл.iso> <метка тома>
public final class BuildTools {
    public static void main(String[] a) throws Exception {
        switch (a[0]) {
            case "ico" -> ico(Paths.get(a[1]));
            case "iso" -> {
                // iso <папка> <файл.iso> <метка> [--boot <загрузчик> <каталог>]  (пути внутри папки, через /)
                String bootFile = null, catFile = null;
                if (a.length > 6 && a[4].equals("--boot")) { bootFile = a[5]; catFile = a[6]; }
                iso(Paths.get(a[1]), Paths.get(a[2]), a.length > 3 ? a[3] : "JAVAWIN", bootFile, catFile);
            }
            case "initrd" -> Initrd.main(Arrays.copyOfRange(a, 1, a.length));
            default -> throw new IllegalArgumentException(a[0]);
        }
    }

    // ================================================================ ICO
    // Маленькие размеры — классический 32-битный BMP с альфой, 256 — PNG внутри ICO.

    static void ico(Path out) throws IOException {
        int[] sizes = { 16, 20, 24, 32, 40, 48, 64, 128, 256 };
        List<byte[]> blobs = new ArrayList<>();
        for (int s : sizes) {
            BufferedImage img = Icons.image("logo", s);
            blobs.add(s >= 128 ? png(img) : dib(img));
        }
        ByteBuffer b = ByteBuffer.allocate(6 + 16 * sizes.length + blobs.stream().mapToInt(x -> x.length).sum())
            .order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) sizes.length);
        int off = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            b.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            b.putShort((short) 1).putShort((short) 32);
            b.putInt(blobs.get(i).length).putInt(off);
            off += blobs.get(i).length;
        }
        for (byte[] x : blobs) b.put(x);
        Files.write(out, b.array());
        System.out.println("ico: " + out + " (" + b.capacity() + " bytes)");
    }

    static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(img, "png", o);
        return o.toByteArray();
    }

    static byte[] dib(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight(), maskRow = ((w + 31) / 32) * 4;
        ByteBuffer b = ByteBuffer.allocate(40 + w * h * 4 + maskRow * h).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(40).putInt(w).putInt(h * 2).putShort((short) 1).putShort((short) 32)
            .putInt(0).putInt(w * h * 4 + maskRow * h).putInt(0).putInt(0).putInt(0).putInt(0);
        for (int y = h - 1; y >= 0; y--)
            for (int x = 0; x < w; x++) b.putInt(img.getRGB(x, y));    // ARGB little-endian = BGRA
        return b.array();                                               // маска AND — нули, прозрачность берётся из альфы
    }

    // ================================================================ ISO 9660 + Joliet

    static final int SEC = 2048;

    static class Node {
        String name, shortName;
        Path path;
        boolean dir;
        long size;
        Node parent;
        final List<Node> kids = new ArrayList<>();
        int number;                         // номер в таблице путей (только папки)
        int extP, sizeP, extJ, sizeJ;       // папки: где лежат записи для ISO и для Joliet
        int extent;                         // файлы: где лежат данные
        byte[] data;                        // подменённое содержимое (каталог загрузки, загрузчик с таблицей)
        byte[] jName() { return name.getBytes(StandardCharsets.UTF_16BE); }
    }

    static Node find(Node root, String rel) {
        Node n = root;
        for (String part : rel.split("/")) {
            Node next = null;
            for (Node k : n.kids) if (k.name.equals(part)) next = k;
            if (next == null) throw new IllegalArgumentException("нет в образе: " + rel);
            n = next;
        }
        return n;
    }

    static void iso(Path src, Path out, String label) throws IOException { iso(src, out, label, null, null); }

    static void iso(Path src, Path out, String label, String bootFile, String catFile) throws IOException {
        Node root = new Node();
        root.name = "";
        root.dir = true;
        root.path = src;
        scan(root);
        boolean boot = bootFile != null;
        Node bootNode = boot ? find(root, bootFile) : null, catNode = boot ? find(root, catFile) : null;
        if (boot) {
            bootNode.data = Files.readAllBytes(bootNode.path);
            catNode.data = new byte[SEC];
            catNode.size = SEC;
        }

        List<Node> dirs = new ArrayList<>();            // в порядке обхода в ширину — так требует таблица путей
        ArrayDeque<Node> q = new ArrayDeque<>();
        q.add(root);
        while (!q.isEmpty()) {
            Node n = q.poll();
            n.number = dirs.size() + 1;
            dirs.add(n);
            for (Node k : sortP(n)) if (k.dir) q.add(k);
        }
        List<Node> files = new ArrayList<>();
        for (Node d : dirs) for (Node k : d.kids) if (!k.dir) files.add(k);

        byte[] ptLP = pathTable(dirs, false, true), ptMP = pathTable(dirs, false, false);
        byte[] ptLJ = pathTable(dirs, true, true), ptMJ = pathTable(dirs, true, false);

        int sector = 16 + 3 + (boot ? 1 : 0);           // PVD, [El Torito], Joliet SVD, терминатор
        int lP = sector; sector += secs(ptLP.length);
        int mP = sector; sector += secs(ptMP.length);
        int lJ = sector; sector += secs(ptLJ.length);
        int mJ = sector; sector += secs(ptMJ.length);
        for (Node d : dirs) { d.sizeP = dirBytes(d, false); d.extP = sector; sector += d.sizeP / SEC; }
        for (Node d : dirs) { d.sizeJ = dirBytes(d, true); d.extJ = sector; sector += d.sizeJ / SEC; }
        for (Node f : files) {
            f.extent = f.size == 0 ? 0 : sector;
            if (f.size > 0) sector += secs(f.size);   // пустой файл места не занимает (и не пишется)
        }
        int total = sector;

        // таблицы путей содержали фиктивные номера секторов — пересобрать с настоящими
        ptLP = pathTable(dirs, false, true); ptMP = pathTable(dirs, false, false);
        ptLJ = pathTable(dirs, true, true); ptMJ = pathTable(dirs, true, false);

        if (boot) {
            elToritoCatalog(catNode.data, bootNode.extent);
            bootInfoTable(bootNode.data, bootNode.extent);
        }

        try (OutputStream o = new BufferedOutputStream(Files.newOutputStream(out), 1 << 20)) {
            o.write(new byte[16 * SEC]);
            o.write(volumeDescriptor(1, label, root, total, ptLP.length, lP, mP));
            if (boot) o.write(bootRecord(catNode.extent));
            o.write(volumeDescriptor(2, label, root, total, ptLJ.length, lJ, mJ));
            byte[] term = new byte[SEC];
            term[0] = (byte) 255;
            System.arraycopy("CD001".getBytes(StandardCharsets.US_ASCII), 0, term, 1, 5);
            term[6] = 1;
            o.write(term);
            for (byte[] pt : new byte[][] { ptLP, ptMP, ptLJ, ptMJ }) o.write(pad(pt));
            for (Node d : dirs) o.write(dirExtent(d, false));
            for (Node d : dirs) o.write(dirExtent(d, true));
            byte[] buf = new byte[1 << 16];
            for (Node f : files) {
                if (f.size == 0) continue;
                if (f.data != null) {
                    o.write(f.data);
                    int t = f.data.length % SEC;
                    if (t != 0) o.write(new byte[SEC - t]);
                    continue;
                }
                long left = f.size;
                try (InputStream in = Files.newInputStream(f.path)) {
                    int r;
                    while ((r = in.read(buf)) > 0) { o.write(buf, 0, r); left -= r; }
                }
                if (left != 0) throw new IOException("файл изменился во время записи: " + f.path);
                int tail = (int) (f.size % SEC);
                if (tail != 0) o.write(new byte[SEC - tail]);
            }
        }
        System.out.printf("iso: %s (%d dirs, %d files, %.1f MB)%n", out, dirs.size(), files.size(), total * (double) SEC / 1048576);
    }

    // ---------------------------------------------------------------- El Torito (загрузка с CD через isolinux)

    static byte[] bootRecord(int catalogSector) {
        ByteBuffer b = ByteBuffer.allocate(SEC).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) 0).put("CD001".getBytes(StandardCharsets.US_ASCII)).put((byte) 1);
        b.put(Arrays.copyOf("EL TORITO SPECIFICATION".getBytes(StandardCharsets.US_ASCII), 32));
        b.position(0x47);
        b.putInt(catalogSector);
        return b.array();
    }

    static void elToritoCatalog(byte[] cat, int bootSector) {
        ByteBuffer b = ByteBuffer.wrap(cat).order(ByteOrder.LITTLE_ENDIAN);
        // проверочная запись: платформа x86, контрольная сумма делает сумму слов нулевой
        b.put(0, (byte) 1);
        byte[] id = "JAVAOS".getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(id, 0, cat, 4, id.length);
        cat[30] = 0x55;
        cat[31] = (byte) 0xAA;
        int sum = 0;
        for (int i = 0; i < 32; i += 2) sum += b.getShort(i) & 0xffff;
        b.putShort(28, (short) (-sum & 0xffff));
        // запись по умолчанию: загрузочная, без эмуляции, 4 виртуальных сектора по 512 байт
        b.put(32, (byte) 0x88);
        b.put(33, (byte) 0);
        b.putShort(34, (short) 0);
        b.put(36, (byte) 0);
        b.putShort(38, (short) 4);
        b.putInt(40, bootSector);
    }

    // isolinux ждёт по смещению 8 таблицу: где PVD, где он сам, его длина и контрольная сумма
    static void bootInfoTable(byte[] f, int sector) {
        ByteBuffer b = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN);
        int sum = 0;
        for (int i = 64; i + 4 <= f.length; i += 4) sum += b.getInt(i);
        b.putInt(8, 16).putInt(12, sector).putInt(16, f.length).putInt(20, sum);
        for (int i = 24; i < 64; i++) f[i] = 0;
    }

    static void scan(Node d) throws IOException {
        List<Path> ps = new ArrayList<>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(d.path)) { for (Path p : s) ps.add(p); }
        Set<String> used = new HashSet<>();
        ps.sort(Comparator.comparing(p -> p.getFileName().toString()));
        for (Path p : ps) {
            Node n = new Node();
            n.parent = d;
            n.path = p;
            n.name = p.getFileName().toString();
            if (n.name.length() > 64) n.name = n.name.substring(0, 64);
            n.dir = Files.isDirectory(p);
            n.size = n.dir ? 0 : Files.size(p);
            n.shortName = shortName(n.name, n.dir, used);
            d.kids.add(n);
            if (n.dir) scan(n);
        }
    }

    // Имя 8.3 из d-символов для основного дерева ISO 9660 (Windows читает Joliet, но оно обязано быть).
    static String shortName(String name, boolean dir, Set<String> used) {
        int dot = dir ? -1 : name.lastIndexOf('.');
        String stem = clean(dot > 0 ? name.substring(0, dot) : name), ext = dot > 0 ? clean(name.substring(dot + 1)) : "";
        // ISO 9660 level 2: имя с расширением до 30 символов (isolinux читает именно это дерево)
        if (ext.length() > 8) ext = ext.substring(0, 8);
        if (stem.isEmpty()) stem = "_";
        int max = dir ? 31 : 30 - 1 - ext.length();
        String base = stem.length() > max ? stem.substring(0, max) : stem;
        String s = base + (dir ? "" : "." + ext);
        for (int i = 1; used.contains(s); i++) {
            String num = "_" + i;
            String b = (stem.length() > max - num.length() ? stem.substring(0, max - num.length()) : stem) + num;
            s = b + (dir ? "" : "." + ext);
        }
        used.add(s);
        return dir ? s : s + ";1";
    }

    static String clean(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toUpperCase(Locale.ROOT).toCharArray())
            b.append((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' ? c : '_');
        return b.toString();
    }

    static List<Node> sortP(Node d) {
        List<Node> l = new ArrayList<>(d.kids);
        l.sort(Comparator.comparing(n -> n.shortName));
        return l;
    }

    static List<Node> sortJ(Node d) {
        List<Node> l = new ArrayList<>(d.kids);
        l.sort((x, y) -> Arrays.compareUnsigned(x.jName(), y.jName()));
        return l;
    }

    static int secs(long bytes) { return (int) Math.max(1, (bytes + SEC - 1) / SEC); }

    static byte[] pad(byte[] b) { return Arrays.copyOf(b, secs(b.length) * SEC); }

    static byte[] idBytes(Node n, boolean joliet) {
        return joliet ? n.jName() : n.shortName.getBytes(StandardCharsets.US_ASCII);
    }

    static int recLen(int nameLen) { return 33 + nameLen + (nameLen % 2 == 0 ? 1 : 0); }

    static int dirBytes(Node d, boolean joliet) {
        int used = recLen(1) * 2, sectors = 1;
        for (Node k : d.kids) {
            int r = recLen(idBytes(k, joliet).length);
            if (used + r > SEC) { sectors++; used = 0; }
            used += r;
        }
        return sectors * SEC;
    }

    static byte[] dirExtent(Node d, boolean joliet) {
        int size = joliet ? d.sizeJ : d.sizeP;
        ByteBuffer b = ByteBuffer.allocate(size);
        Node par = d.parent == null ? d : d.parent;
        b.put(record(d, joliet, new byte[] { 0 }));
        b.put(record(par, joliet, new byte[] { 1 }));
        for (Node k : joliet ? sortJ(d) : sortP(d)) {
            byte[] r = record(k, joliet, idBytes(k, joliet));
            int inSector = b.position() % SEC;
            if (inSector + r.length > SEC) b.position(b.position() + SEC - inSector);
            b.put(r);
        }
        return b.array();
    }

    static byte[] record(Node n, boolean joliet, byte[] id) {
        int len = recLen(id.length);
        ByteBuffer b = ByteBuffer.allocate(len);
        int ext = n.dir ? (joliet ? n.extJ : n.extP) : n.extent;
        long size = n.dir ? (joliet ? n.sizeJ : n.sizeP) : n.size;
        b.put((byte) len).put((byte) 0);
        both32(b, ext);
        both32(b, (int) size);
        b.put(date7());
        b.put((byte) (n.dir ? 2 : 0)).put((byte) 0).put((byte) 0);
        both16(b, 1);
        b.put((byte) id.length).put(id);
        return b.array();
    }

    static byte[] pathTable(List<Node> dirs, boolean joliet, boolean little) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        for (Node d : dirs) {
            byte[] id = d.parent == null ? new byte[] { 0 } : idBytes(d, joliet);
            ByteBuffer b = ByteBuffer.allocate(8 + id.length + (id.length % 2)).order(little ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            b.put((byte) id.length).put((byte) 0);
            b.putInt(joliet ? d.extJ : d.extP);
            b.putShort((short) (d.parent == null ? 1 : d.parent.number));
            b.put(id);
            o.writeBytes(b.array());
        }
        return o.toByteArray();
    }

    static byte[] volumeDescriptor(int type, String label, Node root, int total, int ptSize, int lPath, int mPath) {
        boolean joliet = type == 2;
        ByteBuffer b = ByteBuffer.allocate(SEC);
        b.put((byte) type).put("CD001".getBytes(StandardCharsets.US_ASCII)).put((byte) 1).put((byte) 0);
        b.put(text("WIN32", 32, joliet));
        b.put(text(label, 32, joliet));
        b.position(80);
        both32(b, total);
        if (joliet) { b.position(88); b.put((byte) '%').put((byte) '/').put((byte) 'E'); }
        b.position(120);
        both16(b, 1);
        both16(b, 1);
        both16(b, SEC);
        both32(b, ptSize);
        b.order(ByteOrder.LITTLE_ENDIAN).putInt(lPath).putInt(0);
        b.order(ByteOrder.BIG_ENDIAN).putInt(mPath).putInt(0);
        b.put(record(root, joliet, new byte[] { 0 }));
        b.put(text("", 128, joliet));                     // набор томов
        b.put(text(Theme.NAME, 128, joliet));             // издатель
        b.put(text(Theme.NAME, 128, joliet));             // подготовил
        b.put(text("JAVAWIN BUILDTOOLS", 128, joliet));   // программа
        b.put(text("", 37, joliet)).put(text("", 37, joliet)).put(text("", 37, joliet));
        byte[] d = date17();
        b.put(d).put(d);
        b.put("0000000000000000".getBytes(StandardCharsets.US_ASCII)).put((byte) 0);
        b.put(d);
        b.put((byte) 1);
        return b.array();
    }

    // строка фиксированной длины, добитая пробелами (для Joliet — в UCS-2)
    static byte[] text(String s, int len, boolean joliet) {
        byte[] out = new byte[len];
        if (joliet) {
            for (int i = 0; i + 1 < len; i += 2) { out[i] = 0; out[i + 1] = ' '; }
            byte[] u = s.getBytes(StandardCharsets.UTF_16BE);
            System.arraycopy(u, 0, out, 0, Math.min(u.length, len & ~1));
        } else {
            Arrays.fill(out, (byte) ' ');
            byte[] a = s.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(a, 0, out, 0, Math.min(a.length, len));
        }
        return out;
    }

    static final LocalDateTime NOW = LocalDateTime.now();

    static byte[] date7() {
        return new byte[] { (byte) (NOW.getYear() - 1900), (byte) NOW.getMonthValue(), (byte) NOW.getDayOfMonth(),
            (byte) NOW.getHour(), (byte) NOW.getMinute(), (byte) NOW.getSecond(), 0 };
    }

    static byte[] date17() {
        String s = String.format("%04d%02d%02d%02d%02d%02d00", NOW.getYear(), NOW.getMonthValue(), NOW.getDayOfMonth(),
            NOW.getHour(), NOW.getMinute(), NOW.getSecond());
        byte[] out = Arrays.copyOf(s.getBytes(StandardCharsets.US_ASCII), 17);
        out[16] = 0;
        return out;
    }

    static void both32(ByteBuffer b, int v) {
        b.order(ByteOrder.LITTLE_ENDIAN).putInt(v);
        b.order(ByteOrder.BIG_ENDIAN).putInt(v);
    }

    static void both16(ByteBuffer b, int v) {
        b.order(ByteOrder.LITTLE_ENDIAN).putShort((short) v);
        b.order(ByteOrder.BIG_ENDIAN).putShort((short) v);
    }
}
