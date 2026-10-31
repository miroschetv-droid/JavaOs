package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Медиаплеер: музыка WAV/AIFF/AU — силами Java, MP3/OGG/FLAC — через декодеры JavaOS (mpg123, oggdec, flac),
// которые отдают звук в виде PCM. Звук идёт в выбранное устройство через Java Sound (в Linux — ALSA).
public class Player extends JPanel implements AppBody {
    static final AudioFormat PCM = new AudioFormat(44100, 16, 2, true, false);

    final Desktop d;
    final DefaultListModel<Path> songs = new DefaultListModel<>();
    final JList<Path> list = new JList<>(songs);
    final UI.Label nowTitle = UI.title("Ничего не играет", 18f);
    final UI.Label nowSub = UI.dim("Выбери песню в списке или открой файл из Проводника");
    final UI.Label timeL = UI.dim("0:00"), timeR = UI.dim("0:00");
    final Seek seek = new Seek();
    final Bars bars = new Bars();
    final UI.Btn playBtn = UI.accent("", null);
    final JSlider vol = new JSlider(0, 100, 80);
    final JComboBox<String> out = new JComboBox<>();
    final List<Mixer.Info> outs = new ArrayList<>();

    volatile Thread worker;
    volatile boolean paused;
    volatile long posFrames, totalFrames;   // кадр = 1/44100 секунды
    volatile long seekTo = -1;
    int current = -1;

    public Player(Desktop d, Path arg) {
        super(new BorderLayout());
        this.d = d;
        setBackground(Theme.window());

        // ---- слева: список песен
        list.setCellRenderer((l, p, i, sel, foc) -> {
            UI.Label c = UI.label(p.getFileName().toString());
            c.setFont(Theme.font(i == current ? Font.BOLD : Font.PLAIN, 13f));
            c.setBorder(new EmptyBorder(7, 10, 7, 10));
            c.setOpaque(sel);
            c.setBackground(new Color(Theme.accent.getRed(), Theme.accent.getGreen(), Theme.accent.getBlue(), 70));
            return c;
        });
        list.setOpaque(false);
        list.setBackground(Theme.panel());
        list.setForeground(Theme.text());
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2 && list.getSelectedIndex() >= 0) play(list.getSelectedIndex()); }
        });
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setBackground(Theme.panel());
        left.setBorder(new EmptyBorder(14, 10, 10, 10));
        left.setPreferredSize(new Dimension(300, 10));
        left.add(UI.title("Музыка", 15f), BorderLayout.NORTH);
        JScrollPane ls = UI.scroll(list);
        ls.getViewport().setBackground(Theme.panel());
        ls.setOpaque(false);
        left.add(ls, BorderLayout.CENTER);
        left.add(UI.row(6, UI.button("Добавить…", this::addFiles), UI.button("Папка «Музыка»", () -> d.open("explorer", Vfs.music()))), BorderLayout.SOUTH);
        add(left, BorderLayout.WEST);

        // ---- справа: что играет, эквалайзер, управление
        JPanel right = new JPanel(new BorderLayout(0, 12));
        right.setOpaque(false);
        right.setBorder(new EmptyBorder(18, 22, 16, 22));
        JPanel head = new JPanel(new GridLayout(2, 1, 0, 4));
        head.setOpaque(false);
        head.add(nowTitle);
        head.add(nowSub);
        right.add(head, BorderLayout.NORTH);
        right.add(bars, BorderLayout.CENTER);

        JPanel ctrl = new JPanel();
        ctrl.setOpaque(false);
        ctrl.setLayout(new BoxLayout(ctrl, BoxLayout.Y_AXIS));
        JPanel seekRow = new JPanel(new BorderLayout(8, 0));
        seekRow.setOpaque(false);
        seekRow.add(timeL, BorderLayout.WEST);
        seekRow.add(seek, BorderLayout.CENTER);
        seekRow.add(timeR, BorderLayout.EAST);
        ctrl.add(seekRow);
        ctrl.add(Box.createVerticalStrut(10));
        playBtn.setIcon(new Glyph(0, true));
        playBtn.setPreferredSize(new Dimension(64, 44));
        playBtn.addActionListener(e -> toggle());
        UI.Btn prev = UI.button("", () -> step(-1)), next = UI.button("", () -> step(1));
        prev.setIcon(new Glyph(2, false));
        next.setIcon(new Glyph(3, false));
        prev.setPreferredSize(new Dimension(52, 44));
        next.setPreferredSize(new Dimension(52, 44));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttons.setOpaque(false);
        buttons.add(prev);
        buttons.add(playBtn);
        buttons.add(next);
        ctrl.add(buttons);
        ctrl.add(Box.createVerticalStrut(10));
        vol.setOpaque(false);
        vol.setPreferredSize(new Dimension(160, 24));
        JPanel bottom = new JPanel(new BorderLayout(8, 0));
        bottom.setOpaque(false);
        bottom.add(UI.row(6, UI.dim("Громкость"), vol), BorderLayout.WEST);
        bottom.add(UI.row(6, UI.dim("Вывод"), out), BorderLayout.EAST);
        ctrl.add(bottom);
        right.add(ctrl, BorderLayout.SOUTH);
        add(right, BorderLayout.CENTER);

        fillOutputs();
        loadFolder();
        if (arg != null && Files.isRegularFile(arg)) {
            int i = indexOf(arg);
            if (i < 0) { songs.addElement(arg); i = songs.size() - 1; }
            play(i);
        }
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("SPACE"), "toggle");
        getActionMap().put("toggle", new AbstractAction() { public void actionPerformed(ActionEvent e) { toggle(); } });
    }

    @Override public Dimension getPreferredSize() { return new Dimension(860, 520); }
    @Override public void closing() { stop(); bars.timer.stop(); }
    @Override public Component focusTarget() { return list; }

    // ---------------------------------------------------------------- список

    void loadFolder() {
        try (var s = Files.list(Vfs.music())) {
            s.filter(Player::isAudio).sorted().forEach(songs::addElement);
        } catch (IOException ignored) {}
    }

    int indexOf(Path p) {
        for (int i = 0; i < songs.size(); i++) if (songs.get(i).equals(p)) return i;
        return -1;
    }

    public static boolean isAudio(Path p) {
        return switch (Vfs.ext(p)) { case "wav", "aif", "aiff", "au", "mp3", "ogg", "flac" -> true; default -> false; };
    }

    void addFiles() {
        JFileChooser fc = new JFileChooser(Vfs.root().toFile());
        fc.setMultiSelectionEnabled(true);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        for (File f : fc.getSelectedFiles()) if (isAudio(f.toPath()) && indexOf(f.toPath()) < 0) songs.addElement(f.toPath());
    }

    // устройства вывода: колонки, наушники, HDMI — у компьютера их бывает несколько
    void fillOutputs() {
        out.addItem("Как в системе");
        outs.add(null);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                Mixer m = AudioSystem.getMixer(mi);
                if (!m.isLineSupported(new DataLine.Info(SourceDataLine.class, PCM))) continue;
                String n = mi.getName();
                if (n.startsWith("Port")) continue;
                out.addItem(n.length() > 34 ? n.substring(0, 33) + "…" : n);
                outs.add(mi);
            } catch (Exception ignored) {}
        }
        out.addActionListener(e -> { if (current >= 0 && worker != null) { long p = posFrames; play(current); seekTo = p; } });
    }

    // ---------------------------------------------------------------- управление

    void toggle() {
        if (worker == null) { play(current >= 0 ? current : (list.getSelectedIndex() >= 0 ? list.getSelectedIndex() : 0)); return; }
        paused = !paused;
        setPlayIcon(paused);
    }

    void step(int dir) {
        if (songs.isEmpty()) return;
        play(Math.floorMod((current < 0 ? 0 : current) + dir, songs.size()));
    }

    void stop() {
        Thread w = worker;
        worker = null;
        if (w != null) { w.interrupt(); try { w.join(500); } catch (InterruptedException ignored) {} }
    }

    void play(int i) {
        if (i < 0 || i >= songs.size()) return;
        stop();
        current = i;
        list.setSelectedIndex(i);
        list.repaint();
        Path p = songs.get(i);
        nowTitle.setText(stripExt(p.getFileName().toString()));
        nowSub.setText(Vfs.ext(p).toUpperCase(Locale.ROOT) + " · " + Vfs.size(size(p)));
        paused = false;
        setPlayIcon(false);
        posFrames = 0;
        totalFrames = estimateFrames(p);
        seekTo = -1;
        Mixer.Info dev = outs.get(Math.max(0, out.getSelectedIndex()));
        Thread t = new Thread(() -> run(p, dev), "player");
        t.setDaemon(true);
        worker = t;
        t.start();
    }

    void setPlayIcon(boolean showPlay) { playBtn.setIcon(new Glyph(showPlay ? 0 : 1, true)); playBtn.repaint(); }

    // значки кнопок рисуем сами: в шрифтах JavaOS нет символов «играть», «пауза», «назад», «вперёд»
    static class Glyph implements Icon {
        final int kind;   // 0 играть, 1 пауза, 2 назад, 3 вперёд
        final boolean white;
        Glyph(int kind, boolean white) { this.kind = kind; this.white = white; }
        public int getIconWidth() { return 18; }
        public int getIconHeight() { return 18; }
        public void paintIcon(Component c, Graphics g0, int x, int y) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            g.translate(x, y);
            g.setColor(white ? Color.WHITE : Theme.text());
            switch (kind) {
                case 0 -> g.fillPolygon(new int[] { 4, 16, 4 }, new int[] { 2, 9, 16 }, 3);
                case 1 -> { g.fillRoundRect(3, 2, 4, 14, 2, 2); g.fillRoundRect(11, 2, 4, 14, 2, 2); }
                case 2 -> { g.fillRect(2, 3, 3, 12); g.fillPolygon(new int[] { 16, 6, 16 }, new int[] { 3, 9, 15 }, 3); }
                default -> { g.fillRect(13, 3, 3, 12); g.fillPolygon(new int[] { 2, 12, 2 }, new int[] { 3, 9, 15 }, 3); }
            }
            g.dispose();
        }
    }

    static long size(Path p) { try { return Files.size(p); } catch (IOException e) { return 0; } }
    static String stripExt(String n) { int k = n.lastIndexOf('.'); return k > 0 ? n.substring(0, k) : n; }

    // ---------------------------------------------------------------- воспроизведение

    void run(Path p, Mixer.Info dev) {
        Process proc = null;
        try {
            long skip = 0;
            while (true) {
                AudioInputStream in;
                String ext = Vfs.ext(p);
                if (ext.equals("mp3") || ext.equals("ogg") || ext.equals("flac")) {
                    List<String> cmd = switch (ext) {
                        case "mp3" -> List.of("mpg123", "-q", "-s", "-r", "44100", "-e", "s16", "--stereo", "-k", String.valueOf(skip / 1152), p.toString());
                        case "ogg" -> List.of("oggdec", "-Q", "-R", "-o", "-", p.toString());
                        default -> List.of("flac", "-d", "-c", "-s", "--force-raw-format", "--endian=little", "--sign=signed", p.toString());
                    };
                    try { proc = new ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start(); }
                    catch (IOException e) { fail("Этот формат играет только в JavaOS (нужен декодер " + cmd.get(0) + ")"); return; }
                    proc.getOutputStream().close();
                    AudioFormat f = ext.equals("mp3") ? PCM : rawFormat(p, ext);
                    in = new AudioInputStream(new BufferedInputStream(proc.getInputStream(), 1 << 16), f, AudioSystem.NOT_SPECIFIED);
                    if (ext.equals("mp3")) { posFrames = skip / 1152 * 1152; skip = 0; }
                } else {
                    AudioInputStream src = AudioSystem.getAudioInputStream(p.toFile());
                    AudioFormat sf = src.getFormat();
                    AudioFormat target = new AudioFormat(sf.getSampleRate(), 16, sf.getChannels(), true, false);
                    in = AudioSystem.getAudioInputStream(target, src);
                    if (in.getFrameLength() > 0) totalFrames = (long) (in.getFrameLength() * 44100 / sf.getSampleRate());
                }
                AudioFormat fmt = in.getFormat();
                SourceDataLine line = (SourceDataLine) (dev == null ? AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, fmt))
                                                                    : AudioSystem.getMixer(dev).getLine(new DataLine.Info(SourceDataLine.class, fmt)));
                line.open(fmt, (int) (fmt.getFrameSize() * fmt.getFrameRate() / 5));
                line.start();
                byte[] buf = new byte[4096 - 4096 % fmt.getFrameSize()];
                double rateK = 44100.0 / fmt.getFrameRate();
                long localFrames = 0;
                // пропуск до нужного места (для wav/ogg/flac перематываем, читая поток)
                long want = skip;
                while (want > 0) {
                    long n = in.skip(Math.min(want / (long) rateK * fmt.getFrameSize(), 1 << 20));
                    if (n <= 0) break;
                    want -= (long) (n / fmt.getFrameSize() * rateK);
                    localFrames += n / fmt.getFrameSize();
                }
                if (skip > 0 && !ext.equals("mp3")) posFrames = skip;
                int n;
                boolean restart = false;
                while (worker == Thread.currentThread() && (n = in.read(buf)) > 0) {
                    while (paused && worker == Thread.currentThread()) { line.stop(); Thread.sleep(50); }
                    if (!line.isRunning()) line.start();
                    if (seekTo >= 0) { skip = seekTo; seekTo = -1; restart = true; break; }
                    bars.feed(buf, n, fmt.getChannels());   // столбики — по самой музыке, до громкости
                    scale(buf, n, vol.getValue() / 100.0);
                    line.write(buf, 0, n);
                    posFrames += (long) (n / fmt.getFrameSize() * rateK);
                    if (posFrames > totalFrames) totalFrames = posFrames;
                    SwingUtilities.invokeLater(this::updateTime);
                }
                if (restart) {
                    line.stop(); line.flush(); line.close(); in.close();
                    if (proc != null) proc.destroy();
                    continue;
                }
                if (worker == Thread.currentThread()) line.drain();
                line.stop(); line.close(); in.close();
                break;
            }
            if (worker == Thread.currentThread()) SwingUtilities.invokeLater(() -> { worker = null; step(1); });
        } catch (InterruptedException ignored) {
        } catch (UnsupportedAudioFileException e) {
            fail("Этот файл не получается прочитать как звук");
        } catch (LineUnavailableException | IllegalArgumentException e) {
            fail("Звук некуда вывести: нет звуковой карты или она занята. Попробуй другой «Вывод».");
        } catch (Exception e) {
            fail("Ошибка: " + e);
        } finally {
            if (proc != null) proc.destroy();
        }
    }

    // ogg и flac: частота и каналы — из заголовка файла
    static AudioFormat rawFormat(Path p, String ext) {
        float rate = 44100;
        int ch = 2;
        try (InputStream s = Files.newInputStream(p)) {
            byte[] h = s.readNBytes(256);
            if (ext.equals("ogg")) {
                for (int i = 0; i + 16 < h.length; i++) if (h[i] == 1 && h[i + 1] == 'v' && h[i + 2] == 'o' && h[i + 3] == 'r') {
                    ch = h[i + 11] & 0xff;
                    rate = (h[i + 12] & 0xff) | (h[i + 13] & 0xff) << 8 | (h[i + 14] & 0xff) << 16;
                    break;
                }
            } else if (h.length > 26 && h[0] == 'f' && h[1] == 'L') {
                int b = 8 + 10;   // STREAMINFO: после размеров блоков
                rate = ((h[b] & 0xff) << 12) | ((h[b + 1] & 0xff) << 4) | ((h[b + 2] & 0xf0) >> 4);
                ch = ((h[b + 2] & 0x0e) >> 1) + 1;
            }
        } catch (IOException ignored) {}
        if (rate <= 0) rate = 44100;
        return new AudioFormat(rate, 16, Math.max(1, ch), true, false);
    }

    // длина песни (примерно) — чтобы полоска перемотки знала, где конец
    static long estimateFrames(Path p) {
        long size = size(p);
        String ext = Vfs.ext(p);
        try {
            if (ext.equals("mp3")) {
                byte[] h;
                try (InputStream s = Files.newInputStream(p)) { h = s.readNBytes(64 * 1024); }
                int i = 0;
                if (h.length > 10 && h[0] == 'I' && h[1] == 'D' && h[2] == '3')
                    i = 10 + ((h[6] & 0x7f) << 21 | (h[7] & 0x7f) << 14 | (h[8] & 0x7f) << 7 | (h[9] & 0x7f));
                for (; i + 4 < h.length; i++) if ((h[i] & 0xff) == 0xff && (h[i + 1] & 0xe0) == 0xe0) break;
                if (i + 4 >= h.length) return 0;
                int[] kbps = { 0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 0 };
                int br = kbps[(h[i + 2] & 0xf0) >> 4];
                String s = new String(h, i, Math.min(200, h.length - i), java.nio.charset.StandardCharsets.ISO_8859_1);
                int x = Math.max(s.indexOf("Xing"), s.indexOf("Info"));
                if (x >= 0 && (h[i + x + 7] & 1) != 0) {
                    int k = i + x + 8;
                    long frames = (long) (h[k] & 0xff) << 24 | (h[k + 1] & 0xff) << 16 | (h[k + 2] & 0xff) << 8 | (h[k + 3] & 0xff);
                    return frames * 1152;
                }
                return br > 0 ? size * 8 / (br * 1000L) * 44100 : 0;
            }
            if (ext.equals("ogg")) return size * 8 / 160_000 * 44100;   // ~160 кбит/с
            if (ext.equals("flac")) return size * 8 / 900_000 * 44100;  // ~900 кбит/с
        } catch (Exception ignored) {}
        return 0;
    }

    static void scale(byte[] b, int n, double k) {
        if (k >= 0.999) return;
        double g = k * k;   // громкость на слух — квадратичная
        for (int i = 0; i + 1 < n; i += 2) {
            int s = (short) ((b[i] & 0xff) | (b[i + 1] << 8));
            s = (int) (s * g);
            b[i] = (byte) s;
            b[i + 1] = (byte) (s >> 8);
        }
    }

    void fail(String msg) {
        SwingUtilities.invokeLater(() -> {
            worker = null;
            setPlayIcon(true);
            nowSub.setText(msg);
        });
    }

    void updateTime() {
        timeL.setText(fmt(posFrames));
        timeR.setText(totalFrames > 0 ? fmt(totalFrames) : "—");
        seek.repaint();
    }

    static String fmt(long frames) {
        long s = frames / 44100;
        return s / 60 + ":" + String.format("%02d", s % 60);
    }

    // ---------------------------------------------------------------- перемотка

    class Seek extends JComponent {
        Seek() {
            setPreferredSize(new Dimension(100, 18));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter m = new MouseAdapter() {
                public void mousePressed(MouseEvent e) { jump(e.getX()); }
                public void mouseDragged(MouseEvent e) { jump(e.getX()); }
            };
            addMouseListener(m);
            addMouseMotionListener(m);
        }

        void jump(int x) {
            if (worker == null || totalFrames <= 0) return;
            seekTo = Math.max(0, Math.min(totalFrames - 1, (long) (totalFrames * (x / (double) getWidth()))));
            posFrames = seekTo;
            updateTime();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            int y = getHeight() / 2 - 2, w = getWidth();
            g.setColor(Theme.border());
            g.fill(new RoundRectangle2D.Float(0, y, w, 4, 4, 4));
            double k = totalFrames > 0 ? Math.min(1, posFrames / (double) totalFrames) : 0;
            g.setColor(Theme.accent);
            g.fill(new RoundRectangle2D.Float(0, y, (float) (w * k), 4, 4, 4));
            g.fillOval((int) (w * k) - 6, y - 4, 12, 12);
            g.dispose();
        }
    }

    // ---------------------------------------------------------------- «эквалайзер»

    class Bars extends JComponent {
        static final int N = 28;
        final float[] level = new float[N];
        final float[] target = new float[N];
        final Timer timer = new Timer(33, e -> tick());

        Bars() { timer.start(); }

        // по кусочку звука: громкость в полосах (простое преобразование Фурье по 256 точкам)
        void feed(byte[] b, int n, int ch) {
            int frames = n / (2 * ch);
            int m = Math.min(256, frames);
            if (m < 64) return;
            float[] x = new float[m];
            for (int i = 0; i < m; i++) {
                int o = i * 2 * ch;
                x[i] = (short) ((b[o] & 0xff) | (b[o + 1] << 8)) / 32768f;
            }
            float[] t = new float[N];
            for (int k = 0; k < N; k++) {
                int bin = 1 + (int) Math.pow(1.17, k + 4);   // полосы шире к высоким частотам
                if (bin >= m / 2) bin = m / 2 - 1;
                double re = 0, im = 0;
                for (int i = 0; i < m; i++) { double a = 2 * Math.PI * bin * i / m; re += x[i] * Math.cos(a); im -= x[i] * Math.sin(a); }
                t[k] = (float) Math.min(1, Math.sqrt(re * re + im * im) / m * 6);
            }
            synchronized (target) { System.arraycopy(t, 0, target, 0, N); }
        }

        void tick() {
            boolean playing = worker != null && !paused;
            synchronized (target) {
                for (int i = 0; i < N; i++) {
                    float t = playing ? target[i] : 0;
                    level[i] += (t - level[i]) * (t > level[i] ? 0.6f : 0.15f);
                }
            }
            repaint();
        }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            int w = getWidth(), h = getHeight();
            g.setColor(Theme.panel());
            g.fill(new RoundRectangle2D.Float(0, 0, w, h, 16, 16));
            float bw = (w - 24) / (float) N;
            for (int i = 0; i < N; i++) {
                float v = Math.max(0.03f, level[i]);
                int bh = (int) ((h - 30) * v);
                Color c = Theme.accent;
                g.setColor(new Color(c.getRed(), c.getGreen(), c.getBlue(), 120 + (int) (135 * v)));
                g.fill(new RoundRectangle2D.Float(12 + i * bw + 2, h - 14 - bh, bw - 4, bh, 4, 4));
            }
            if (worker == null) {
                g.setFont(Theme.font(Font.PLAIN, 13f));
                g.setColor(Theme.dim());
                String s = "Двойной щелчок по песне — играть · Пробел — пауза";
                g.drawString(s, (w - g.getFontMetrics().stringWidth(s)) / 2, h / 2);
            }
            g.dispose();
        }
    }
}
