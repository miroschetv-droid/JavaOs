package javawin.apps;

import javawin.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

// Радио и ТВ: прямой эфир настоящих станций и каналов через интернет. Играет плеер mpv из Arch Linux
// (вместе с yt-dlp он берёт эфир с официальных сайтов каналов) — при первом включении ставится сам.
// Радио — без картинки, ТВ — в отдельном окне плеера.
public class RadioTV extends JPanel implements AppBody {
    record Station(String name, String url) {}

    static final List<Station> RADIO = List.of(
        new Station("Европа Плюс", "https://ep128.hostingradio.ru:8030/ep128"),
        new Station("Русское радио", "https://rusradio.hostingradio.ru/rusradio128.mp3"),
        new Station("Хит FM", "https://hitfm.hostingradio.ru/hitfm128.mp3"),
        new Station("Детское радио", "https://pub0302.101.ru:8443/stream/air/mp3/256/199"),
        new Station("Радио Рекорд", "https://radiorecord.hostingradio.ru/rr_main96.aacp"),
        new Station("DFM", "https://dfm.hostingradio.ru/dfm96.aacp"),
        new Station("Наше радио", "https://nashe1.hostingradio.ru/nashe-128.mp3"),
        new Station("Радио Maximum", "https://maximum.hostingradio.ru/maximum128.mp3"));

    // официальные страницы эфира каналов
    static final List<Station> TV = List.of(
        new Station("СТС Kids", "https://ctckids.ru/online"),
        new Station("Карусель", "https://www.karusel-tv.ru/live"),
        new Station("СТС", "https://ctc.ru/online/"),
        new Station("Первый канал", "https://www.1tv.ru/live"),
        new Station("Россия 1", "https://smotrim.ru/channel/1"),
        new Station("Мульт", "https://multtv.ru/"));

    final javawin.Desktop d;
    final UI.Label status = UI.dim("Выбери станцию или канал. Нужен интернет.");
    final JTextField custom = new JTextField();

    public RadioTV(javawin.Desktop d, java.nio.file.Path p) {
        super(new BorderLayout());
        this.d = d;
        setBackground(Theme.window());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Радио", list(RADIO, false));
        tabs.addTab("ТВ", list(TV, true));
        add(tabs, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(8, 6));
        bottom.setOpaque(false);
        bottom.setBorder(new EmptyBorder(8, 12, 12, 12));
        JPanel own = new JPanel(new BorderLayout(6, 0));
        own.setOpaque(false);
        own.add(UI.dim("Своя ссылка:"), BorderLayout.WEST);
        own.add(custom, BorderLayout.CENTER);
        own.add(UI.button("Включить", () -> {
            String u = custom.getText().trim();
            if (!u.isEmpty()) play(new Station(u, u), true);
        }), BorderLayout.EAST);
        bottom.add(own, BorderLayout.NORTH);
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.add(status, BorderLayout.CENTER);
        row.add(UI.button("■ Стоп", this::stop), BorderLayout.EAST);
        bottom.add(row, BorderLayout.SOUTH);
        add(bottom, BorderLayout.SOUTH);

        if (ArchLinux.script() == null) status.setText("Радио и ТВ работают в JavaOS (нужен Arch Linux и интернет).");
    }

    JComponent list(List<Station> items, boolean video) {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        col.setBorder(new EmptyBorder(10, 10, 10, 10));
        for (Station s : items) {
            UI.Btn b = UI.button(s.name(), () -> play(s, video));
            b.flat = true;
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.setFont(Theme.font(Font.PLAIN, 16f));
            b.setBorder(new EmptyBorder(10, 14, 10, 14));
            b.setAlignmentX(LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
            col.add(b);
        }
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(col, BorderLayout.NORTH);
        return UI.scroll(top);
    }

    void play(Station s, boolean video) {
        if (ArchLinux.script() == null) return;
        status.setText((video ? "Открываю «" : "Включаю «") + s.name() + "»… в первый раз плеер ещё скачивается — до пары минут");
        new Thread(() -> {
            // один плеер за раз; mpv и yt-dlp — один раз из Arch Linux
            ArchLinux.run(l -> {}, "exec", "/bin/sh", "-c",
                "pkill -x mpv; command -v mpv >/dev/null && command -v yt-dlp >/dev/null || pacman -S --noconfirm --needed mpv yt-dlp");
            String title = s.name().replace("'", "");
            String cmd = "mpv --force-window=" + (video ? "yes" : "no") + (video ? "" : " --no-video")
                + " --title='" + title + "' --ytdl-format='best[height<=720]/best' '" + s.url().replace("'", "") + "'";
            ArchLinux.run(l -> {}, "run", cmd);
            SwingUtilities.invokeLater(() -> status.setText((video ? "Смотрим: " : "Играет: ") + s.name()
                + ". Если ничего нет — канал не даёт смотреть себя через программу."));
        }, "radio-tv").start();
    }

    void stop() {
        if (ArchLinux.script() == null) return;
        new Thread(() -> ArchLinux.run(l -> {}, "exec", "pkill", "-x", "mpv"), "radio-stop").start();
        status.setText("Остановлено");
    }

    @Override public void closing() { stop(); }
}
