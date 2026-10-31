package javawin.apps;

import javawin.*;

import javax.sound.sampled.*;
import javax.swing.*;
import java.awt.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Диктофон: запись с микрофона в «Музыка/Записи» (WAV), список записей, прослушивание и удаление.
public class Recorder extends JPanel implements AppBody {
    static final AudioFormat FMT = new AudioFormat(22050, 16, 1, true, false);

    final DefaultListModel<Path> files = new DefaultListModel<>();
    final JList<Path> list = new JList<>(files);
    final JButton rec = new JButton("● Запись");
    final JLabel status = new JLabel("Нажми «Запись» и говори в микрофон", SwingConstants.CENTER);
    final Meter meter = new Meter();
    final Timer tick = new Timer(100, e -> { meter.repaint(); updateTime(); });

    volatile boolean recording;
    volatile float level;
    long recStart;
    Thread worker;
    Clip clip;

    public Recorder(javawin.Desktop d, Path p) {
        super(new BorderLayout(0, 10));
        setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        setBackground(Theme.window());

        rec.setFont(Theme.font(Font.BOLD, 18f));
        rec.addActionListener(e -> { if (recording) stop(); else start(); });
        status.setFont(Theme.font(Font.PLAIN, 15f));
        JPanel top = new JPanel(new BorderLayout(0, 8));
        top.setOpaque(false);
        top.add(rec, BorderLayout.NORTH);
        top.add(meter);
        top.add(status, BorderLayout.SOUTH);
        add(top, BorderLayout.NORTH);

        list.setFont(Theme.font(Font.PLAIN, 15f));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                Path f0 = (Path) v;
                String t = f0.getFileName().toString().replace(".wav", "");
                try { t += "   (" + (Files.size(f0) / (FMT.getFrameSize() * (int) FMT.getSampleRate())) + " с)"; } catch (Exception ignored) {}
                return super.getListCellRendererComponent(l, t, i, s, f);
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) { if (e.getClickCount() == 2) play(); }
        });
        add(new JScrollPane(list));

        JButton play = new JButton("▶ Слушать");
        play.addActionListener(e -> play());
        JButton stopPlay = new JButton("■ Стоп");
        stopPlay.addActionListener(e -> { if (clip != null) clip.stop(); });
        JButton del = new JButton("Удалить");
        del.addActionListener(e -> delete());
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        bottom.setOpaque(false);
        bottom.add(play);
        bottom.add(stopPlay);
        bottom.add(del);
        add(bottom, BorderLayout.SOUTH);

        reload();
        tick.start();
    }

    static Path dir() {
        Path d = Vfs.music().resolve("Записи");
        try { Files.createDirectories(d); } catch (Exception ignored) {}
        return d;
    }

    void reload() {
        files.clear();
        try (var s = Files.list(dir())) {
            s.filter(f -> f.toString().endsWith(".wav")).sorted(java.util.Comparator.reverseOrder()).forEach(files::addElement);
        } catch (Exception ignored) {}
    }

    void start() {
        TargetDataLine line;
        try {
            line = AudioSystem.getTargetDataLine(FMT);
            line.open(FMT);
        } catch (Exception e) {
            status.setText("Микрофон не найден. Подключи его и попробуй снова.");
            return;
        }
        recording = true;
        recStart = System.currentTimeMillis();
        rec.setText("■ Остановить");
        worker = new Thread(() -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            line.start();
            while (recording) {
                int n = line.read(buf, 0, buf.length);
                out.write(buf, 0, n);
                int peak = 0;
                for (int i = 0; i + 1 < n; i += 2) peak = Math.max(peak, Math.abs((short) ((buf[i] & 0xff) | (buf[i + 1] << 8))));
                level = peak / 32768f;
            }
            line.stop();
            line.close();
            level = 0;
            save(out.toByteArray());
        }, "recorder");
        worker.start();
    }

    void stop() {
        recording = false;
        rec.setText("● Запись");
    }

    void save(byte[] pcm) {
        String name = "Запись " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss")) + ".wav";
        File f = dir().resolve(name).toFile();
        try (AudioInputStream in = new AudioInputStream(new ByteArrayInputStream(pcm), FMT, pcm.length / FMT.getFrameSize())) {
            AudioSystem.write(in, AudioFileFormat.Type.WAVE, f);
            SwingUtilities.invokeLater(() -> { status.setText("Сохранено: Музыка/Записи/" + name); reload(); list.setSelectedIndex(0); });
        } catch (Exception e) {
            SwingUtilities.invokeLater(() -> status.setText("Не удалось сохранить: " + e.getMessage()));
        }
    }

    void play() {
        Path f = list.getSelectedValue();
        if (f == null) { if (files.isEmpty()) return; f = files.get(0); }
        try {
            if (clip != null) clip.close();
            clip = AudioSystem.getClip();
            clip.open(AudioSystem.getAudioInputStream(f.toFile()));
            clip.start();
            status.setText("Играет: " + f.getFileName().toString().replace(".wav", ""));
        } catch (Exception e) {
            status.setText("Не получается включить звук: " + e.getMessage());
        }
    }

    void delete() {
        Path f = list.getSelectedValue();
        if (f == null) return;
        if (JOptionPane.showConfirmDialog(this, "Удалить «" + f.getFileName() + "»?", "Диктофон",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        if (clip != null) clip.close();
        try { Files.delete(f); } catch (Exception ignored) {}
        reload();
    }

    void updateTime() {
        if (!recording) return;
        long s = (System.currentTimeMillis() - recStart) / 1000;
        status.setText(String.format("Идёт запись…  %02d:%02d", s / 60, s % 60));
    }

    @Override public void closing() {
        recording = false;
        tick.stop();
        if (clip != null) clip.close();
    }

    // полоска громкости: видно, слышит ли микрофон
    class Meter extends JComponent {
        Meter() { setPreferredSize(new Dimension(10, 26)); }

        @Override protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            UI.aa(g);
            g.setColor(Theme.panel());
            g.fillRoundRect(0, 4, getWidth(), 18, 9, 9);
            float l = Math.min(1f, level * 1.6f);
            g.setColor(l > 0.85f ? new Color(0xe5484d) : new Color(0x30a46c));
            g.fillRoundRect(0, 4, (int) (getWidth() * l), 18, 9, 9);
            g.dispose();
        }
    }
}
