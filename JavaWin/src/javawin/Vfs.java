package javawin;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

// Виртуальный диск C: — обычная папка «C» рядом с системой.
public final class Vfs {
    private Vfs() {}

    public static Path root()      { return Theme.root; }
    public static Path desktop()   { return root().resolve("Рабочий стол"); }
    public static Path docs()      { return root().resolve("Документы"); }
    public static Path pics()      { return root().resolve("Рисунки"); }
    public static Path downloads() { return root().resolve("Загрузки"); }
    public static Path trash()     { return root().resolve("Корзина"); }
    public static Path music()     { return root().resolve("Музыка"); }

    static void prepare() {
        boolean fresh = !Files.exists(root());
        try {
            for (Path p : List.of(desktop(), docs(), pics(), downloads(), trash())) Files.createDirectories(p);
            if (!Files.exists(music())) {   // папка «Музыка» с мелодией, чтобы плеер было чем проверить
                Files.createDirectories(music());
                demoTune(music().resolve("Мелодия JavaOS.wav"));
            }
            if (!fresh) return;
            Files.writeString(desktop().resolve("Прочти меня.txt"), String.join("\r\n",
                "Добро пожаловать в JavaWin!",
                "",
                "Это маленькая операционная система, написанная на Java.",
                "Всё, что ты видишь, нарисовано с нуля: окна, панель задач, меню «Пуск».",
                "",
                "Что попробовать:",
                "  • Пуск (кнопка с логотипом) — все программы и поиск",
                "  • Терминал — команды help, dir, cd, tree, start, systeminfo",
                "  • Параметры — обои, цвет акцента, светлая и тёмная тема",
                "  • Ctrl+Shift+S — снимок экрана в папку «Рисунки»",
                "  • F11 — во весь экран",
                "",
                "Диск C: — это папка «C» рядом с программой. Файлы там настоящие."), StandardCharsets.UTF_8);
            Files.writeString(docs().resolve("Список дел.txt"),
                "1. Сыграть в Сапёра\r\n2. Нарисовать что-нибудь в Paint\r\n3. Поменять обои\r\n", StandardCharsets.UTF_8);
            ImageIO.write(Wallpaper.render(4, 640, 360), "png", pics().resolve("Закулисье.png").toFile());
            ImageIO.write(Wallpaper.render(1, 640, 360), "png", pics().resolve("Закат.png").toFile());
        } catch (IOException ignored) {}
    }

    // C:\Документы\a.txt ↔ <root>/Документы/a.txt
    public static String virt(Path p) {
        Path r = root().relativize(p.toAbsolutePath().normalize());
        String s = r.toString();
        return s.isEmpty() ? "C:\\" : "C:\\" + s;
    }

    public static Path resolve(Path cwd, String s) {
        s = s.trim();
        if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
        s = s.replace('/', '\\');
        Path p;
        if (s.length() >= 2 && (s.charAt(0) == 'c' || s.charAt(0) == 'C') && s.charAt(1) == ':')
            p = root().resolve(strip(s.substring(2)));
        else if (s.startsWith("\\"))
            p = root().resolve(strip(s));
        else
            p = cwd.resolve(s.replace('\\', '/'));
        p = p.toAbsolutePath().normalize();
        return p.startsWith(root()) ? p : root();
    }

    static String strip(String s) {
        while (s.startsWith("\\")) s = s.substring(1);
        return s.replace('\\', '/');
    }

    public static String name(Path p) {
        return p.equals(root()) ? "Локальный диск (C:)" : p.getFileName().toString();
    }

    public static String ext(Path p) {
        String n = p.getFileName() == null ? "" : p.getFileName().toString();
        int i = n.lastIndexOf('.');
        return i < 0 ? "" : n.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static final Set<String> TEXT = Set.of("txt", "md", "log", "ini", "bat", "cmd", "java", "cfg", "json",
        "xml", "csv", "properties", "html", "css", "js", "py", "cs", "gd");
    static final Set<String> IMAGE = Set.of("png", "jpg", "jpeg", "gif", "bmp");

    public static boolean isText(Path p)  { return TEXT.contains(ext(p)); }
    public static boolean isImage(Path p) { return IMAGE.contains(ext(p)); }

    // короткая мелодия: квадратная волна с затуханием, 44.1 кГц, 16 бит, моно
    static void demoTune(Path to) throws IOException {
        int[] notes = { 64, 67, 71, 76, 74, 71, 67, 69, 72, 76, 79, 76, 72, 69, 71, 74, 79, 83, 79, 74, 72, 71, 69, 67, 64, 67, 71, 64 };
        int rate = 44100, noteLen = rate / 4;
        java.io.ByteArrayOutputStream pcm = new java.io.ByteArrayOutputStream();
        for (int n : notes) {
            double f = 440 * Math.pow(2, (n - 69) / 12.0);
            for (int i = 0; i < noteLen; i++) {
                double env = Math.exp(-3.0 * i / noteLen) * Math.min(1, i / 200.0);
                double s = (Math.sin(2 * Math.PI * f * i / rate) > 0 ? 1 : -1) * 0.25 + Math.sin(2 * Math.PI * f / 2 * i / rate) * 0.15;
                int v = (int) (s * env * 32767 * 0.6);
                pcm.write(v & 0xff);
                pcm.write((v >> 8) & 0xff);
            }
        }
        byte[] data = pcm.toByteArray();
        javax.sound.sampled.AudioInputStream ais = new javax.sound.sampled.AudioInputStream(new java.io.ByteArrayInputStream(data),
            new javax.sound.sampled.AudioFormat(rate, 16, 1, true, false), data.length / 2);
        javax.sound.sampled.AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, to.toFile());
    }

    public static boolean isAudio(Path p) { return switch (ext(p)) { case "wav", "aif", "aiff", "au", "mp3", "ogg", "flac" -> true; default -> false; }; }

    public static String kind(Path p) {
        if (p.equals(root())) return "drive";
        if (p.equals(trash())) return "trash";
        if (Files.isDirectory(p)) return "folder";
        if (isImage(p)) return "image";
        if (isAudio(p)) return "music";
        if (isText(p)) return "text";
        return "file";
    }

    public static String type(Path p) {
        return switch (kind(p)) {
            case "drive" -> "Локальный диск";
            case "folder", "trash" -> "Папка с файлами";
            case "image" -> "Рисунок " + ext(p).toUpperCase(Locale.ROOT);
            case "text" -> "Текстовый документ";
            case "music" -> "Звук " + ext(p).toUpperCase(Locale.ROOT);
            default -> ext(p).isEmpty() ? "Файл" : "Файл «" + ext(p).toUpperCase(Locale.ROOT) + "»";
        };
    }

    public static List<Path> list(Path dir) {
        List<Path> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(dir)) { s.forEach(out::add); } catch (IOException ignored) {}
        out.sort(Comparator.<Path, Boolean>comparing(p -> !Files.isDirectory(p))
            .thenComparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)));
        return out;
    }

    public static Path unique(Path dir, String name) {
        Path p = dir.resolve(name);
        if (!Files.exists(p)) return p;
        int dot = name.lastIndexOf('.');
        String stem = dot > 0 ? name.substring(0, dot) : name, ext = dot > 0 ? name.substring(dot) : "";
        for (int i = 2; ; i++) {
            p = dir.resolve(stem + " (" + i + ")" + ext);
            if (!Files.exists(p)) return p;
        }
    }

    public static void toTrash(Path p) throws IOException {
        if (p.startsWith(trash())) deleteAll(p);
        else Files.move(p, unique(trash(), p.getFileName().toString()));
    }

    public static void deleteAll(Path p) throws IOException {
        if (Files.isDirectory(p)) for (Path k : list(p)) deleteAll(k);
        Files.deleteIfExists(p);
    }

    public static void copyAll(Path from, Path to) throws IOException {
        if (Files.isDirectory(from)) {
            Files.createDirectories(to);
            for (Path k : list(from)) copyAll(k, to.resolve(k.getFileName().toString()));
        } else Files.copy(from, to);
    }

    public static long sizeOf(Path p) {
        if (!Files.isDirectory(p)) {
            try { return Files.size(p); } catch (IOException e) { return 0; }
        }
        long s = 0;
        for (Path k : list(p)) s += sizeOf(k);
        return s;
    }

    public static String size(long b) {
        if (b < 1024) return b + " байт";
        if (b < 1024 * 1024) return String.format("%.1f КБ", b / 1024.0);
        if (b < 1024L * 1024 * 1024) return String.format("%.1f МБ", b / 1048576.0);
        return String.format("%.1f ГБ", b / 1073741824.0);
    }

    public static String readText(Path p) throws IOException {
        byte[] b = Files.readAllBytes(p);
        try {
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b)).toString();
        } catch (java.nio.charset.CharacterCodingException e) {
            return new String(b, java.nio.charset.Charset.forName("windows-1251"));
        }
    }
}
