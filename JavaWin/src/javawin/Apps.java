package javawin;

import javawin.apps.*;

import javax.swing.JComponent;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiFunction;

// Реестр программ. w = 0 — окно подгоняется под предпочтительный размер программы.
public final class Apps {
    public record App(String id, String title, String icon, int w, int h, BiFunction<Desktop, Path, JComponent> make) {}

    public static final List<App> ALL = List.of(
        new App("explorer", "Проводник", "explorer", 920, 580, Explorer::new),
        new App("browser", "Браузер", "browser", 1040, 700, Browser::new),
        new App("notepad", "Блокнот", "notepad", 780, 540, Notepad::new),
        new App("cmd", "Терминал", "cmd", 840, 500, Terminal::new),
        new App("calc", "Калькулятор", "calc", 0, 0, (d, p) -> new Calc()),
        new App("paint", "Paint", "paint", 1000, 660, Paint::new),
        new App("mines", "Сапёр", "mines", 0, 0, (d, p) -> new Mines()),
        new App("snake", "Змейка", "snake", 0, 0, (d, p) -> new Snake()),
        new App("2048", "2048", "2048", 0, 0, (d, p) -> new Game2048()),
        new App("clock", "Часы", "clock", 560, 520, Clock::new),
        new App("recorder", "Диктофон", "mic", 520, 560, Recorder::new),
        new App("radio", "Радио и ТВ", "radio", 560, 600, RadioTV::new),
        new App("player", "Медиаплеер", "music", 860, 520, Player::new),
        new App("taskmgr", "Диспетчер задач", "taskmgr", 760, 560, (d, p) -> new TaskMgr(d)),
        new App("arch", "Arch Linux", "linux", 960, 640, ArchLinux::new),
        new App("wifi", "Wi-Fi", "wifi", 520, 560, WiFi::new),
        new App("settings", "Параметры", "settings", 940, 620, (d, p) -> new Settings(d)),
        new App("about", "О системе", "about", 0, 0, (d, p) -> new About())
    );

    private Apps() {}

    public static App get(String id) {
        for (App a : ALL) if (a.id().equals(id)) return a;
        return null;
    }
}
