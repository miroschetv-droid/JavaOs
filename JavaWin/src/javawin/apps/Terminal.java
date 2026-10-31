package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

// Командная строка по образцу cmd.exe, работает с виртуальным диском C:.
public class Terminal extends JPanel implements AppBody {
    final Desktop d;
    final JTextArea area = new JTextArea();
    final List<String> history = new ArrayList<>();
    int hIdx, inputStart;
    boolean sys;
    Path cwd;
    Win win;
    Color fg = new Color(0xcccccc);

    public Terminal(Desktop d, Path start) {
        super(new BorderLayout());
        this.d = d;
        cwd = start != null && Files.isDirectory(start) ? start : Vfs.root();
        area.setBackground(new Color(0x0c0c0c));
        area.setForeground(fg);
        area.setCaretColor(Color.WHITE);
        area.setFont(Theme.mono(14.5f));
        area.setLineWrap(true);
        area.setBorder(new EmptyBorder(8, 10, 8, 10));
        area.setSelectionColor(new Color(0x264f78));
        area.setSelectedTextColor(Color.WHITE);
        JScrollPane sc = UI.scroll(area);
        sc.getViewport().setBackground(area.getBackground());
        add(sc);

        ((AbstractDocument) area.getDocument()).setDocumentFilter(new DocumentFilter() {
            public void insertString(FilterBypass fb, int off, String s, AttributeSet a) throws BadLocationException {
                if (sys) { fb.insertString(off, s, a); return; }
                if (off < inputStart) { off = fb.getDocument().getLength(); area.setCaretPosition(off); }
                fb.insertString(off, s.replace("\n", " "), a);
            }

            public void replace(FilterBypass fb, int off, int len, String s, AttributeSet a) throws BadLocationException {
                if (sys) { fb.replace(off, len, s, a); return; }
                if (off < inputStart) {
                    off = fb.getDocument().getLength();
                    len = 0;
                }
                fb.replace(off, len, s == null ? "" : s.replace("\n", " "), a);
            }

            public void remove(FilterBypass fb, int off, int len) throws BadLocationException {
                if (sys) { fb.remove(off, len); return; }
                if (off < inputStart) {
                    int end = off + len;
                    if (end <= inputStart) return;
                    len = end - inputStart;
                    off = inputStart;
                }
                fb.remove(off, len);
            }
        });

        bind(KeyEvent.VK_ENTER, 0, this::enter);
        bind(KeyEvent.VK_UP, 0, () -> hist(-1));
        bind(KeyEvent.VK_DOWN, 0, () -> hist(1));
        bind(KeyEvent.VK_HOME, 0, () -> area.setCaretPosition(inputStart));
        bind(KeyEvent.VK_ESCAPE, 0, () -> setInput(""));
        bind(KeyEvent.VK_TAB, 0, this::complete);
        bind(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK, () -> { cls(); prompt(); });

        print(Theme.NAME + " [Версия " + Theme.VERSION + "." + Runtime.version().feature() + "]\n");
        print("(c) " + Theme.NAME + ". Все права защищены. Введите help для списка команд.\n\n");
        prompt();
    }

    void bind(int code, int mods, Runnable r) {
        String id = "t" + code + "_" + mods;
        area.getInputMap().put(KeyStroke.getKeyStroke(code, mods), id);
        area.getActionMap().put(id, new AbstractAction() { public void actionPerformed(ActionEvent e) { r.run(); } });
    }

    @Override public void attached(Win w) { win = w; }
    @Override public Component focusTarget() { return area; }

    // ---------------------------------------------------------------- ввод-вывод

    void print(String s) {
        sys = true;
        try {
            Document doc = area.getDocument();
            doc.insertString(doc.getLength(), s, null);
        } catch (BadLocationException ignored) {}
        sys = false;
        area.setCaretPosition(area.getDocument().getLength());
    }

    void println(String s) { print(s + "\n"); }

    void prompt() {
        print(Vfs.virt(cwd) + ">");
        inputStart = area.getDocument().getLength();
    }

    String input() {
        try { return area.getText(inputStart, area.getDocument().getLength() - inputStart); }
        catch (BadLocationException e) { return ""; }
    }

    void setInput(String s) {
        sys = true;
        try {
            Document doc = area.getDocument();
            doc.remove(inputStart, doc.getLength() - inputStart);
            doc.insertString(inputStart, s, null);
        } catch (BadLocationException ignored) {}
        sys = false;
        area.setCaretPosition(area.getDocument().getLength());
    }

    void cls() {
        sys = true;
        area.setText("");
        sys = false;
        inputStart = 0;
    }

    void hist(int dir) {
        if (history.isEmpty()) return;
        hIdx = Math.max(0, Math.min(history.size(), hIdx + dir));
        setInput(hIdx == history.size() ? "" : history.get(hIdx));
    }

    void complete() {
        String in = input();
        int sp = in.lastIndexOf(' ');
        String head = in.substring(0, sp + 1), part = in.substring(sp + 1);
        int slash = part.lastIndexOf('\\');
        Path dir = slash >= 0 ? Vfs.resolve(cwd, part.substring(0, slash + 1)) : cwd;
        String pre = part.substring(slash + 1).toLowerCase(Locale.ROOT);
        for (Path p : Vfs.list(dir)) {
            String n = p.getFileName().toString();
            if (n.toLowerCase(Locale.ROOT).startsWith(pre)) {
                String full = part.substring(0, slash + 1) + n;
                setInput(head + (full.contains(" ") ? "\"" + full + "\"" : full));
                return;
            }
        }
    }

    void enter() {
        String line = input();
        print("\n");
        if (!line.isBlank()) {
            history.add(line);
            hIdx = history.size();
        }
        try {
            if (!exec(line.trim())) return;
        } catch (Exception e) {
            println("Ошибка: " + (e.getMessage() == null ? e : e.getMessage()));
        }
        if (!line.isBlank()) print("\n");
        prompt();
    }

    // ---------------------------------------------------------------- команды

    static final String HELP = String.join("\n",
        "Команды:",
        "  HELP           этот список",
        "  DIR [путь]     содержимое папки",
        "  CD [путь]      сменить папку (CD .. — на уровень вверх)",
        "  TREE [путь]    дерево папок",
        "  TYPE файл      показать текстовый файл",
        "  ECHO текст     вывести текст (ECHO текст > файл — записать в файл)",
        "  MD имя         создать папку",
        "  RD имя         удалить пустую папку",
        "  DEL файл       удалить файл",
        "  REN старое новое   переименовать",
        "  COPY откуда куда   скопировать",
        "  MOVE откуда куда   переместить",
        "  START прог|файл    запустить программу или открыть файл",
        "                 программы: explorer notepad calc paint mines taskmgr settings",
        "  CLS            очистить экран",
        "  COLOR цвет     цвет текста: 7 серый, a зелёный, b голубой, e жёлтый, c красный",
        "  DATE, TIME, VER, WHOAMI, HOSTNAME, SYSTEMINFO, TASKLIST",
        "  SH команда     выполнить команду в настоящей системе (в JavaOS — Linux)",
        "  ARCH команда   выполнить команду внутри Arch Linux (ARCH START прог — открыть окно)",
        "  PACMAN ...     менеджер пакетов Arch: pacman -S firefox, pacman -Ss редактор",
        "  MOUSE GAME / MOUSE NORMAL   мышь для игр (плавная камера) или обычная",
        "  TASKKILL номер     закрыть окно по номеру из TASKLIST",
        "  SHUTDOWN [/r]  выключить (с /r — перезагрузить)",
        "  Приколы: JOKE, COWSAY текст, MATRIX, HACK, SL, 8BALL вопрос, DICE, COIN, FIRE",
        "  EXIT           закрыть терминал");

    // false — не печатать приглашение (терминал закрыт или система выключается)
    boolean exec(String line) throws IOException {
        if (line.isEmpty()) return true;
        String low = line.toLowerCase(Locale.ROOT);
        if (low.equals("cd..")) low = line = "cd ..";
        if (low.startsWith("echo")) { echo(line.length() > 4 ? line.substring(5) : ""); return true; }
        List<String> a = split(line);
        String cmd = a.get(0).toLowerCase(Locale.ROOT);
        String arg = a.size() > 1 ? a.get(1) : null;
        switch (cmd) {
            case "help", "?", "/?" -> println(HELP);
            case "cls", "clear" -> cls();
            case "dir", "ls" -> dir(arg == null ? cwd : Vfs.resolve(cwd, arg));
            case "cd", "chdir" -> {
                if (arg == null) { println(Vfs.virt(cwd)); break; }
                Path p = Vfs.resolve(cwd, arg);
                if (Files.isDirectory(p)) cwd = p;
                else println("Системе не удаётся найти указанный путь.");
            }
            case "tree" -> {
                Path p = arg == null ? cwd : Vfs.resolve(cwd, arg);
                println(Vfs.virt(p));
                tree(p, "");
            }
            case "type", "cat" -> {
                Path p = need(arg);
                if (p == null) break;
                if (Files.isDirectory(p)) println("Отказано в доступе.");
                else println(Vfs.readText(p).replace("\r\n", "\n"));
            }
            case "md", "mkdir" -> {
                if (arg == null) { println("Неверный синтаксис команды."); break; }
                Path p = Vfs.resolve(cwd, arg);
                if (Files.exists(p)) println("Подпапка или файл " + arg + " уже существует.");
                else Files.createDirectories(p);
            }
            case "rd", "rmdir" -> {
                Path p = need(arg);
                if (p == null) break;
                if (!Files.isDirectory(p)) println("Неверно задано имя папки.");
                else if (!Vfs.list(p).isEmpty()) println("Папка не пуста.");
                else Files.delete(p);
            }
            case "del", "erase", "rm" -> {
                Path p = need(arg);
                if (p == null) break;
                if (Files.isDirectory(p)) println("Это папка. Используйте RD.");
                else Files.delete(p);
            }
            case "ren", "rename" -> {
                if (a.size() < 3) { println("Неверный синтаксис команды."); break; }
                Path p = need(arg);
                if (p != null) Files.move(p, p.resolveSibling(a.get(2)));
            }
            case "copy", "move" -> {
                if (a.size() < 3) { println("Неверный синтаксис команды."); break; }
                Path from = need(arg);
                if (from == null) break;
                Path to = Vfs.resolve(cwd, a.get(2));
                if (Files.isDirectory(to)) to = to.resolve(from.getFileName().toString());
                if (cmd.equals("copy")) Vfs.copyAll(from, to);
                else Files.move(from, to);
                println(cmd.equals("copy") ? "Скопировано файлов: 1." : "Перемещено файлов: 1.");
            }
            case "start", "open" -> start(arg);
            case "notepad", "calc", "paint", "mspaint", "explorer", "mines", "taskmgr", "settings", "control", "winver" -> {
                String id = switch (cmd) {
                    case "mspaint" -> "paint";
                    case "control" -> "settings";
                    case "winver" -> "about";
                    default -> cmd;
                };
                d.open(id, arg == null ? (id.equals("explorer") ? cwd : null) : Vfs.resolve(cwd, arg));
            }
            case "color" -> {
                Map<String, Color> cs = Map.of("7", new Color(0xcccccc), "a", new Color(0x16c60c), "b", new Color(0x61d6d6),
                    "e", new Color(0xf9f1a5), "c", new Color(0xe74856), "f", Color.WHITE, "9", new Color(0x3b78ff));
                Color c = arg == null ? cs.get("7") : cs.get(arg.toLowerCase(Locale.ROOT).substring(arg.length() - 1));
                if (c == null) println("Неизвестный цвет.");
                else { fg = c; area.setForeground(c); }
            }
            case "date" -> println("Текущая дата: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
            case "time" -> println("Текущее время: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss,SS")));
            case "ver" -> println("\n" + Theme.NAME + " [Версия " + Theme.VERSION + "] на Java " + Runtime.version());
            case "whoami" -> println(host().toLowerCase(Locale.ROOT) + "\\" + Theme.user);
            case "hostname" -> println(host());
            case "systeminfo" -> systeminfo();
            case "tasklist" -> {
                println(String.format("%-4s %-40s %s", "№", "Окно", "Состояние"));
                println("==== ======================================== ==========");
                int i = 1;
                for (Win w : wins()) println(String.format("%-4d %-40s %s", i++, cut(w.getTitle(), 40), w == activeWin() ? "Активно" : "Работает"));
            }
            case "taskkill" -> {
                List<Win> ws = wins();
                try {
                    int n = Integer.parseInt(arg == null ? "" : arg.replace("/pid", "").trim());
                    Win w = ws.get(n - 1);
                    if (w == win) { println("Нельзя закрыть сам терминал этой командой. Используйте EXIT."); break; }
                    w.close();
                    println("Успешно: окно «" + w.getTitle() + "» закрыто.");
                } catch (Exception e) { println("Ошибка: окно не найдено. Номера окон — в TASKLIST."); }
            }
            case "java" -> println("openjdk version \"" + Runtime.version() + "\"\n" + System.getProperty("java.vm.name"));
            case "shutdown" -> {
                boolean r = line.toLowerCase(Locale.ROOT).contains("/r") || line.contains("-r");
                println(r ? "Перезагрузка…" : "Завершение работы…");
                javax.swing.Timer t = new javax.swing.Timer(700, e -> Main.shutdown(r));
                t.setRepeats(false);
                t.start();
                return false;
            }
            case "exit" -> {
                if (win != null) win.close();
                return false;
            }
            case "sh", "linux" -> { return realShell(line.substring(a.get(0).length()).trim()); }
            case "arch" -> {
                String rest = line.substring(a.get(0).length()).trim();
                if (rest.isEmpty()) { println("Использование: ARCH команда | ARCH START программа   (внутри Arch Linux)"); break; }
                if (rest.toLowerCase(Locale.ROOT).startsWith("start ")) {
                    if (!archReady()) break;
                    ArchLinux.launch(rest.substring(6).trim());
                    println("Запускается: " + rest.substring(6).trim());
                    break;
                }
                return archAsync(List.of("/bin/sh", "-c", rest));
            }
            case "mouse" -> {
                if (!Main.osMode) { println("Переключение мыши работает только в JavaOS."); break; }
                boolean game = arg != null && arg.equalsIgnoreCase("game");
                if (arg == null || !(game || arg.equalsIgnoreCase("normal"))) {
                    println("MOUSE GAME   — мышь для игр (плавная камера; отпустить: Ctrl+Alt+G)");
                    println("MOUSE NORMAL — обычная мышь (курсор свободно ходит между Windows и JavaOS)");
                    println("Сейчас: " + (ClipSync.gameMouse ? "для игр" : "обычная"));
                    break;
                }
                String err = ClipSync.mouse(game);
                println(err == null ? (game ? "Мышь для игр включена. Щёлкни в окно, чтобы захватить мышь." : "Обычная мышь включена.") : "Ошибка: " + err);
            }
            case "pacman" -> {
                List<String> pa = new ArrayList<>(List.of("pacman"));
                pa.addAll(a.subList(1, a.size()));
                if (a.size() > 1 && a.get(1).matches("-[SRU].*") && !pa.contains("--noconfirm")) pa.add("--noconfirm");
                return archAsync(pa);
            }
            case "bench" -> {
                // сколько раз в секунду перерисовывается весь рабочий стол
                int n = 20;
                d.paintImmediately(0, 0, d.getWidth(), d.getHeight());
                long t = System.nanoTime();
                for (int i = 0; i < n; i++) d.paintImmediately(0, 0, d.getWidth(), d.getHeight());
                double ms = (System.nanoTime() - t) / 1e6 / n;
                println(String.format("Полная перерисовка %dx%d: %.1f мс — %.1f кадров/с", d.getWidth(), d.getHeight(), ms, 1000 / ms));
                println("Java2D: pmoffscreen=" + System.getProperty("sun.java2d.pmoffscreen") + ", " + java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().getDefaultConfiguration().getColorModel().getPixelSize() + " бит");
            }
            case "joke", "шутка" -> println(JOKES[new Random().nextInt(JOKES.length)]);
            case "cowsay" -> {
                String s = line.length() > 6 ? line.substring(7).trim() : "Му-у! Я корова из JavaOS.";
                if (s.isEmpty()) s = "Му-у!";
                println(" " + "_".repeat(s.length() + 2) + "\n< " + s + " >\n " + "-".repeat(s.length() + 2)
                    + "\n        \\   ^__^\n         \\  (oo)\\_______\n            (__)\\       )\\/\\\n                ||----w |\n                ||     ||");
            }
            case "8ball", "шар" -> {
                if (arg == null) { println("Задай вопрос: 8BALL Я сегодня выиграю?"); break; }
                println("" + BALL[new Random().nextInt(BALL.length)]);
            }
            case "dice", "кубик" -> {
                int v = 1 + new Random().nextInt(6);
                String[] faces = { "", "|     |\n|  o  |\n|     |", "|o    |\n|     |\n|    o|", "|o    |\n|  o  |\n|    o|",
                    "|o   o|\n|     |\n|o   o|", "|o   o|\n|  o  |\n|o   o|", "|o   o|\n|o   o|\n|o   o|" };
                println(" _____\n" + faces[v] + "\n ‾‾‾‾‾\nВыпало: " + v);
            }
            case "coin", "монетка" -> println(new Random().nextBoolean() ? "Орёл!" : "Решка!");
            case "matrix" -> { return animate(matrix(), 60); }
            case "hack", "взлом" -> { return animate(hack(), 220); }
            case "sl", "поезд" -> { return animate(train(), 70); }
            case "fire", "огонь" -> { return animate(fire(), 90); }
            case "ping" -> {
                String host = arg == null ? "localhost" : arg;
                println("Обмен пакетами с " + host + " по 32 байтам данных:");
                Random r = new Random();
                for (int i = 0; i < 4; i++) println("Ответ от " + host + ": число байт=32 время=" + (1 + r.nextInt(30)) + "мс TTL=64");
            }
            default -> {
                Path p = Vfs.resolve(cwd, line);
                if (Files.isRegularFile(p) && !line.isBlank()) d.openFile(p);
                else println("\"" + a.get(0) + "\" не является внутренней или внешней\nкомандой, исполняемой программой или пакетным файлом.");
            }
        }
        return true;
    }

    static final String[] JOKES = {
        "Программист ставит на тумбочку два стакана: с водой — если захочет пить, и пустой — если не захочет.",
        "— Сколько программистов нужно, чтобы вкрутить лампочку?\n— Ни одного, это аппаратная проблема.",
        "Java и JavaScript похожи так же, как кот и котлета.",
        "Компьютер сказал: «Мне нужен перерыв». И ушёл в спящий режим.",
        "Почему программисты путают Хэллоуин и Рождество? Потому что 31 OCT = 25 DEC.",
        "Я бы рассказал шутку про UDP, но не уверен, что она до тебя дойдёт.",
        "JavaOS загрузилась с первого раза. Это не шутка, это чудо!",
        "— Алло, это техподдержка? У меня клавиатура не работает.\n— А вы её включили?\n— Как я вам напечатаю, что не включил?",
        "Змейка из JavaOS выросла такой длинной, что ей выдали отдельный монитор.",
    };
    static final String[] BALL = { "Бесспорно.", "Скорее всего, да.", "Знаки говорят — да.", "Даже не думай.",
        "Спроси позже.", "Мой ответ — нет.", "Весьма сомнительно.", "Лучше не рассказывать сейчас.", "Однозначно да!",
        "Сначала сделай уроки, потом спроси." };

    // анимация: строки по одной с паузой, потом приглашение
    boolean animate(List<String> frames, int ms) {
        area.setEditable(false);
        int[] i = { 0 };
        javax.swing.Timer tm = new javax.swing.Timer(ms, null);
        tm.addActionListener(e -> {
            if (i[0] >= frames.size() || !isShowing()) {
                tm.stop();
                area.setEditable(true);
                print("\n");
                prompt();
                area.requestFocusInWindow();
                return;
            }
            String f = frames.get(i[0]++);
            if (f.equals("\f")) cls(); else println(f);
        });
        tm.start();
        return false;
    }

    static List<String> matrix() {
        Random r = new Random();
        String chars = "01アイウエオカキクケコサシスセソJAVAOS#$%&*";
        List<String> out = new ArrayList<>();
        for (int y = 0; y < 60; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < 70; x++) sb.append(r.nextInt(4) == 0 ? chars.charAt(r.nextInt(chars.length())) : ' ');
            out.add(sb.toString());
        }
        out.add("\nWake up, Neo... JavaOS has you.");
        return out;
    }

    static List<String> hack() {
        Random r = new Random();
        List<String> out = new ArrayList<>(List.of("> Подключение к суперкомпьютеру Пентагона…", "> Обход файрвола…"));
        for (int p = 0; p <= 100; p += 10 + r.nextInt(10)) {
            int n = Math.min(p, 100) / 5;
            out.add("[" + "█".repeat(n) + "░".repeat(20 - n) + "] " + Math.min(p, 100) + "%  0x" + Long.toHexString(r.nextLong()).toUpperCase());
        }
        out.addAll(List.of("[████████████████████] 100%", "> Доступ получен!", "> Скачивание секретных файлов: «рецепт_блинов_бабушки.txt»…",
            "", "Шутка! Это просто анимация, никто ничего не взламывал."));
        return out;
    }

    static List<String> train() {
        String[] t = {
            "      ====        ________                ___________ ",
            "  _D _|  |_______/        \\__I_I_____===__|_________| ",
            "   |(_)---  |   H\\________/ |   |        =|___ ___|   ",
            "   /     |  |   H  |  |     |   |         ||_| |_||   ",
            "  |      |  |   H  |__--------------------| [___] |   ",
            "  | ________|___H__/__|_____/[][]~\\_______|       |   ",
            "  |/ |   |-----------I_____I [][] []  D   |=======|__ ",
            "__/ =| o |=-~~\\  /~~\\  /~~\\  /~~\\ ____Y___________|__ ",
            " |/-=|___|=    ||    ||    ||    |_____/~\\___/        ",
            "  \\_/      \\O=====O=====O=====O_/      \\_/            " };
        List<String> out = new ArrayList<>();
        for (int s = 60; s >= -60; s -= 4) {
            out.add("\f");
            StringBuilder sb = new StringBuilder("\n\n");
            for (String l : t) {
                String row = s >= 0 ? " ".repeat(s) + l : (-s < l.length() ? l.substring(-s) : "");
                sb.append(row).append("\n");
            }
            out.add(sb.toString());
        }
        out.add("\f");
        out.add("Чух-чух!  Поезд JavaOS уехал.");
        return out;
    }

    static List<String> fire() {
        Random r = new Random();
        String pal = " .:*sS#$";
        int w = 64, h = 14;
        int[][] f = new int[h + 1][w];
        List<String> out = new ArrayList<>();
        for (int k = 0; k < 50; k++) {
            for (int x = 0; x < w; x++) f[h][x] = r.nextInt(8);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                int s = f[y + 1][x] + f[y + 1][(x + 1) % w] + f[y + 1][(x + w - 1) % w] + f[Math.min(h, y + 2 > h ? h : y + 2)][x];
                f[y][x] = Math.max(0, s / 4 - (r.nextInt(3) == 0 ? 1 : 0));
            }
            out.add("\f");
            StringBuilder sb = new StringBuilder();
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) sb.append(pal.charAt(Math.min(7, f[y][x])));
                sb.append("\n");
            }
            out.add(sb.toString());
        }
        out.add("\f");
        out.add("Огонь погас. Не играй со спичками!");
        return out;
    }

    boolean archReady() {
        if (ArchLinux.script() == null) { println("Arch Linux работает только в JavaOS."); return false; }
        if (!ArchLinux.capture("status").trim().equals("installed")) {
            println("Arch Linux не установлен — открой приложение «Arch Linux» и нажми «Установить».");
            return false;
        }
        return true;
    }

    // команда внутри Arch: вывод идёт по мере работы, приглашение — когда закончится
    boolean archAsync(List<String> cmd) {
        if (!archReady()) return true;
        area.setEditable(false);
        Thread t = new Thread(() -> {
            List<String> args = new ArrayList<>(List.of("exec"));
            args.addAll(cmd);
            int code = ArchLinux.run(l -> SwingUtilities.invokeLater(() -> println(l.replace("\r", ""))), args.toArray(new String[0]));
            SwingUtilities.invokeLater(() -> {
                if (code != 0) println("(код выхода " + code + ")");
                area.setEditable(true);
                print("\n");
                prompt();
                area.requestFocusInWindow();
            });
        }, "arch-cmd");
        t.setDaemon(true);
        t.start();
        return false;
    }

    // настоящая команда хост-системы (в JavaOS — Linux): sh ls -l /
    // в отдельном потоке: долгая команда (или программа, запущенная с «&») не должна подвешивать весь рабочий стол
    boolean realShell(String cmd) {
        if (cmd.isEmpty()) { println("Использование: SH команда   (выполнить в настоящей системе)"); return true; }
        boolean win = System.getProperty("os.name").startsWith("Windows");
        ProcessBuilder pb = win ? new ProcessBuilder("cmd", "/c", cmd) : new ProcessBuilder("/bin/sh", "-c", cmd);
        pb.redirectErrorStream(true);
        area.setEditable(false);
        Thread t = new Thread(() -> {
            String res;
            try {
                Process p = pb.start();
                p.getOutputStream().close();
                java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
                Thread reader = new Thread(() -> {
                    try { p.getInputStream().transferTo(buf); } catch (Exception ignored) {}
                }, "sh-out");
                reader.setDaemon(true);
                reader.start();
                boolean done = p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
                if (!done) p.destroyForcibly();
                // программа в фоне («&») держит вывод открытым — ждём его недолго
                reader.join(done ? 1500 : 500);
                String s;
                synchronized (buf) { s = buf.toString(win ? java.nio.charset.Charset.forName("cp866") : StandardCharsets.UTF_8); }
                if (s.length() > 200_000) s = s.substring(0, 200_000);
                res = (s.endsWith("\n") || s.isEmpty() ? s : s + "\n") + (done ? "" : "(прервано по таймауту)\n");
            } catch (Exception e) { res = "Ошибка: " + e.getMessage() + "\n"; }
            String out = res;
            SwingUtilities.invokeLater(() -> {
                print(out);
                area.setEditable(true);
                print("\n");
                prompt();
                area.requestFocusInWindow();
            });
        }, "sh-cmd");
        t.setDaemon(true);
        t.start();
        return false;
    }

    List<Win> wins() { return d.windows(); }
    Win activeWin() { return d.activeWindow(); }

    static String cut(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    static String host() {
        String h = System.getenv("COMPUTERNAME");
        return h == null ? "JAVAWIN-PC" : h;
    }

    Path need(String arg) {
        if (arg == null) { println("Неверный синтаксис команды."); return null; }
        Path p = Vfs.resolve(cwd, arg);
        if (!Files.exists(p)) { println("Не удаётся найти " + arg); return null; }
        return p;
    }

    static List<String> split(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder b = new StringBuilder();
        boolean q = false;
        for (char c : line.toCharArray()) {
            if (c == '"') q = !q;
            else if (c == ' ' && !q) { if (b.length() > 0) { out.add(b.toString()); b.setLength(0); } }
            else b.append(c);
        }
        if (b.length() > 0) out.add(b.toString());
        return out;
    }

    void echo(String s) throws IOException {
        int gt = s.indexOf('>');
        if (gt < 0) { println(s.isBlank() ? "Режим вывода команд на экран (ECHO) включён." : s); return; }
        boolean append = gt + 1 < s.length() && s.charAt(gt + 1) == '>';
        String text = s.substring(0, gt).trim();
        String target = s.substring(gt + (append ? 2 : 1)).trim();
        Path p = Vfs.resolve(cwd, target);
        if (append) Files.writeString(p, text + "\r\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        else Files.writeString(p, text + "\r\n", StandardCharsets.UTF_8);
    }

    void start(String arg) {
        if (arg == null) { d.open("cmd", cwd); return; }
        String id = arg.toLowerCase(Locale.ROOT).replace(".exe", "");
        if (id.equals("mspaint")) id = "paint";
        if (id.equals("cmd")) { d.open("cmd", cwd); return; }
        if (Apps.get(id) != null) { d.open(id, id.equals("explorer") ? cwd : null); return; }
        Path p = Vfs.resolve(cwd, arg);
        if (Files.exists(p)) d.openFile(p);
        else println("Не удаётся найти \"" + arg + "\".");
    }

    void dir(Path p) throws IOException {
        if (!Files.exists(p)) { println("Файл не найден"); return; }
        println(" Том в устройстве C имеет метку JAVAWIN\n Серийный номер тома: 4A56-1A2B\n\n Содержимое папки " + Vfs.virt(p) + "\n");
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd.MM.yyyy  HH:mm").withZone(ZoneId.systemDefault());
        int files = 0, dirs = 0;
        long total = 0;
        for (Path k : Vfs.list(p)) {
            BasicFileAttributes at = Files.readAttributes(k, BasicFileAttributes.class);
            String when = f.format(at.lastModifiedTime().toInstant());
            if (at.isDirectory()) {
                dirs++;
                println(when + "    <DIR>          " + k.getFileName());
            } else {
                files++;
                total += at.size();
                println(String.format("%s %17s %s", when, String.format("%,d", at.size()).replace(',', ' '), k.getFileName()));
            }
        }
        long free = Files.getFileStore(Vfs.root()).getUsableSpace();
        println(String.format("%16d файлов %,15d байт", files, total).replace(',', ' '));
        print(String.format("%16d папок  %,15d байт свободно", dirs, free).replace(',', ' '));
        print("\n");
    }

    void tree(Path p, String indent) {
        List<Path> ds = Vfs.list(p).stream().filter(Files::isDirectory).toList();
        for (int i = 0; i < ds.size(); i++) {
            boolean last = i == ds.size() - 1;
            println(indent + (last ? "└───" : "├───") + ds.get(i).getFileName());
            tree(ds.get(i), indent + (last ? "    " : "│   "));
        }
    }

    void systeminfo() {
        Runtime rt = Runtime.getRuntime();
        long total = 0, free = 0;
        try {
            var os = (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            total = os.getTotalMemorySize();
            free = os.getFreeMemorySize();
        } catch (Throwable ignored) {}
        long up = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        println("Имя узла:                  " + host());
        println("Название ОС:               " + Theme.NAME);
        println("Версия ОС:                 " + Theme.VERSION);
        println("Пользователь:              " + Theme.user);
        println("Время работы системы:      " + String.format("%d:%02d:%02d", up / 3600, up / 60 % 60, up % 60));
        println("Ядро:                      Java " + Runtime.version() + " (" + System.getProperty("java.vm.name") + ")");
        println("Компьютер-хозяин:          " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        println("Процессоры:                " + rt.availableProcessors() + " логических");
        if (total > 0) {
            println("Всего физической памяти:   " + Vfs.size(total));
            println("Доступно физической памяти:" + " " + Vfs.size(free));
        }
        println("Память системы (JVM):      " + Vfs.size(rt.totalMemory() - rt.freeMemory()) + " из " + Vfs.size(rt.maxMemory()));
        println("Диск C:                    " + Vfs.virt(Vfs.root()) + " → " + Theme.root);
    }
}
