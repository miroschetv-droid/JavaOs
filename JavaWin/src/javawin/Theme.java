package javawin;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.*;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;

// Настройки системы и цвета. Всё, что рисуется своими руками, берёт цвета
// отсюда в момент отрисовки, поэтому смена темы применяется сразу.
public final class Theme {
    public static final String NAME = "JavaWin", VERSION = "1.0";

    public static final Color[] ACCENTS = {
        new Color(0x3b82f6), new Color(0x0ea5e9), new Color(0x10b981), new Color(0xf59e0b),
        new Color(0xef4444), new Color(0xec4899), new Color(0x8b5cf6), new Color(0x64748b),
    };

    public static Color accent = ACCENTS[0];
    public static boolean dark = true;
    public static String user = "Пользователь";
    public static int wallpaper = 0;

    public static Path base;   // папка системы (рядом с jar)
    public static Path root;   // виртуальный диск C:
    static Path cfg;

    private Theme() {}

    public static void init() {
        base = appDir();
        root = base.resolve("C").toAbsolutePath().normalize();
        cfg = base.resolve("javawin.properties");
        load();
        Vfs.prepare();
    }

    // Рядом с jar, а если туда писать нельзя (например, запуск с iso) — в домашней папке.
    static Path appDir() {
        String prop = System.getProperty("javawin.home");
        if (prop != null) return Paths.get(prop).toAbsolutePath();
        try {
            Path p = Paths.get(Theme.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path dir = p.getParent();
            if (dir != null && dir.getFileName() != null && dir.getFileName().toString().equals("app")
                    && dir.getParent() != null && Files.exists(dir.getParent().resolve("runtime")))
                dir = dir.getParent();
            if (dir != null && writable(dir)) return dir;
        } catch (Exception ignored) {}
        return Paths.get(System.getProperty("user.home"), "JavaWin");
    }

    static boolean writable(Path dir) {
        try {
            Path t = Files.createTempFile(dir, ".jw", ".tmp");
            Files.delete(t);
            return true;
        } catch (Exception e) { return false; }
    }

    static void load() {
        if (!Files.exists(cfg)) return;
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(cfg, StandardCharsets.UTF_8)) {
            p.load(r);
            dark = Boolean.parseBoolean(p.getProperty("dark", "true"));
            user = p.getProperty("user", user);
            wallpaper = Math.floorMod(Integer.parseInt(p.getProperty("wallpaper", "0")), Wallpaper.NAMES.length);
            accent = new Color(Integer.parseInt(p.getProperty("accent", "3b82f6"), 16));
        } catch (Exception ignored) {}
    }

    public static void save() {
        Properties p = new Properties();
        p.setProperty("dark", String.valueOf(dark));
        p.setProperty("user", user);
        p.setProperty("wallpaper", String.valueOf(wallpaper));
        p.setProperty("accent", String.format("%06x", accent.getRGB() & 0xffffff));
        try {
            Files.createDirectories(base);
            try (Writer w = Files.newBufferedWriter(cfg, StandardCharsets.UTF_8)) { p.store(w, "JavaWin"); }
        } catch (IOException ignored) {}
    }

    // ---------------------------------------------------------------- цвета

    public static Color window()   { return dark ? new Color(0x202020) : new Color(0xf3f3f3); }
    public static Color panel()    { return dark ? new Color(0x2b2b2b) : new Color(0xfbfbfb); }
    public static Color field()    { return dark ? new Color(0x1c1c1c) : new Color(0xffffff); }
    public static Color control()  { return dark ? new Color(0x373737) : new Color(0xfdfdfd); }
    public static Color control2() { return dark ? new Color(0x3f3f3f) : new Color(0xffffff); }
    public static Color hover()    { return dark ? new Color(0x404040) : new Color(0xe6e6e6); }
    public static Color text()     { return dark ? new Color(0xffffff) : new Color(0x1b1b1b); }
    public static Color dim()      { return dark ? new Color(0xa0a0a0) : new Color(0x616161); }
    public static Color border()   { return dark ? new Color(0x3c3c3c) : new Color(0xd6d6d6); }
    public static Color selection(){ return mix(window(), accent, dark ? 0.35 : 0.22); }
    public static Color shell()    { return dark ? new Color(32, 32, 32, 236) : new Color(240, 240, 240, 236); }
    public static Color menu()     { return dark ? new Color(40, 40, 40, 252) : new Color(246, 246, 246, 252); }

    public static Color mix(Color a, Color b, double t) {
        return new Color(
            (int) Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
            (int) Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
            (int) Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    public static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), a); }

    // ---------------------------------------------------------------- шрифты

    static String uiFamily, monoFamily;

    public static Font font(int style, float size) {
        if (uiFamily == null) {
            uiFamily = has("Segoe UI") ? "Segoe UI" : Font.SANS_SERIF;
            monoFamily = has("Cascadia Mono") ? "Cascadia Mono" : has("Consolas") ? "Consolas" : Font.MONOSPACED;
        }
        return new Font(uiFamily, style, 1).deriveFont(size);
    }

    public static Font mono(float size) {
        font(Font.PLAIN, 1);
        return new Font(monoFamily, Font.PLAIN, 1).deriveFont(size);
    }

    static boolean has(String family) {
        for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames())
            if (f.equalsIgnoreCase(family)) return true;
        return false;
    }

    // ---------------------------------------------------------------- стандартные компоненты Swing

    public static void applyUI() {
        try { UIManager.setLookAndFeel(new javax.swing.plaf.metal.MetalLookAndFeel()); } catch (Exception ignored) {}
        UIManager.put("swing.boldMetal", Boolean.FALSE);

        FontUIResource f = new FontUIResource(font(Font.PLAIN, 13f));
        for (Object k : UIManager.getLookAndFeelDefaults().keySet().toArray())
            if (k.toString().endsWith(".font")) UIManager.put(k, f);
        UIManager.put("TextArea.font", new FontUIResource(mono(14f)));

        ColorUIResource win = c(window()), pnl = c(panel()), fld = c(field()), txt = c(text()),
            dm = c(dim()), brd = c(border()), sel = c(selection()), hov = c(hover());

        String[] bgKeys = { "Panel.background", "control", "Viewport.background", "ScrollPane.background",
            "OptionPane.background", "SplitPane.background", "Slider.background", "CheckBox.background",
            "RadioButton.background", "TabbedPane.background", "ToolBar.background", "Label.background" };
        for (String k : bgKeys) UIManager.put(k, win);

        String[] fgKeys = { "Label.foreground", "CheckBox.foreground", "RadioButton.foreground",
            "TitledBorder.titleColor", "OptionPane.messageForeground", "Slider.foreground",
            "TabbedPane.foreground", "Button.foreground" };
        for (String k : fgKeys) UIManager.put(k, txt);

        for (String p : new String[] { "TextField", "TextArea", "TextPane", "EditorPane", "PasswordField",
                "FormattedTextField", "List", "Table", "Tree" }) {
            UIManager.put(p + ".background", fld);
            UIManager.put(p + ".foreground", txt);
            UIManager.put(p + ".caretForeground", txt);
            UIManager.put(p + ".selectionBackground", sel);
            UIManager.put(p + ".selectionForeground", txt);
            UIManager.put(p + ".inactiveForeground", dm);
        }
        UIManager.put("TextField.border", new BorderUIResource(new CompoundBorder(
            new RoundLine(), new EmptyBorder(5, 8, 5, 8))));
        UIManager.put("TextArea.border", new BorderUIResource(new EmptyBorder(8, 10, 8, 10)));
        UIManager.put("ScrollPane.border", new BorderUIResource(new EmptyBorder(0, 0, 0, 0)));
        UIManager.put("Table.gridColor", brd);
        UIManager.put("TableHeader.background", pnl);
        UIManager.put("TableHeader.foreground", dm);

        for (String p : new String[] { "Menu", "MenuItem", "CheckBoxMenuItem", "RadioButtonMenuItem" }) {
            UIManager.put(p + ".background", pnl);
            UIManager.put(p + ".foreground", txt);
            UIManager.put(p + ".selectionBackground", hov);
            UIManager.put(p + ".selectionForeground", txt);
            UIManager.put(p + ".acceleratorForeground", dm);
            UIManager.put(p + ".acceleratorSelectionForeground", dm);
            UIManager.put(p + ".disabledForeground", dm);
            UIManager.put(p + ".border", new BorderUIResource(new EmptyBorder(6, 10, 6, 14)));
        }
        UIManager.put("MenuBar.background", win);
        UIManager.put("MenuBar.foreground", txt);
        UIManager.put("MenuBar.gradient", null);
        UIManager.put("MenuBar.border", new BorderUIResource(new EmptyBorder(2, 4, 2, 4)));
        UIManager.put("Menu.border", new BorderUIResource(new EmptyBorder(5, 10, 5, 10)));
        UIManager.put("PopupMenu.background", pnl);
        UIManager.put("PopupMenu.border", new BorderUIResource(new CompoundBorder(
            new LineBorder(brd), new EmptyBorder(4, 2, 4, 2))));
        UIManager.put("Separator.foreground", brd);
        UIManager.put("Separator.background", pnl);
        UIManager.put("PopupMenuSeparator.foreground", brd);
        UIManager.put("PopupMenuSeparator.background", pnl);
        UIManager.put("ToolTip.background", pnl);
        UIManager.put("ToolTip.foreground", txt);
        UIManager.put("ToolTip.border", new BorderUIResource(new CompoundBorder(
            new LineBorder(brd), new EmptyBorder(4, 8, 4, 8))));
        UIManager.put("Slider.focus", win);
        UIManager.put("CheckBox.focus", win);
    }

    static ColorUIResource c(Color x) { return new ColorUIResource(x); }

    // Скруглённая рамка поля ввода; при фокусе — цвет акцента.
    static class RoundLine extends AbstractBorder implements UIResource {
        public void paintBorder(Component c, Graphics g0, int x, int y, int w, int h) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            boolean focus = c.hasFocus();
            g.setColor(focus ? accent : border());
            g.drawRoundRect(x, y, w - 1, h - 1, 8, 8);
            if (focus) g.fillRect(x + 3, y + h - 2, w - 6, 2);
            g.dispose();
        }
        public Insets getBorderInsets(Component c) { return new Insets(1, 1, 2, 1); }
    }
}
