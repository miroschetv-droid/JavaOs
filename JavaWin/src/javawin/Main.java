package javawin;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;

// Точка входа: загрузка → экран входа → рабочий стол → завершение работы.
public class Main {
    static JFrame frame;
    static Desktop desktop;
    static boolean fullscreen;
    static Rectangle normalBounds;
    // --os: JavaWin работает как оболочка настоящей ОС (JavaOS): весь экран, выключение — по-настоящему
    public static boolean osMode;

    public static void main(String[] args) throws Exception {
        System.setProperty("awt.useSystemAAFontSettings", "lcd");
        System.setProperty("swing.aatext", "true");
        Theme.init();
        if (args.length >= 2 && args[0].equals("--shot")) {
            Shots.make(new File(args[1]));
            return;
        }
        boolean quick = false;
        for (String a : args) {
            if (a.equals("--quick")) quick = true;
            if (a.equals("--os")) osMode = true;
        }
        boolean q = quick;
        SwingUtilities.invokeLater(() -> start(q));
    }

    static void start(boolean quick) {
        Theme.applyUI();
        ToolTipManager.sharedInstance().setInitialDelay(500);
        frame = new JFrame(Theme.NAME);
        frame.setIconImages(Icons.appImages());
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                if (desktop != null) shutdown(false);
                else System.exit(0);
            }
        });
        Rectangle scr = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        if (osMode) {
            frame.setUndecorated(true);
            // В Linux окно рабочего стола не отдаём оконному менеджеру (override-redirect через тип POPUP):
            // JWM подрезает окна до одного монитора, а рабочий стол должен закрывать все мониторы.
            if (!System.getProperty("os.name", "").startsWith("Windows")) frame.setType(Window.Type.POPUP);
            frame.setBounds(allScreens());
            // оконный менеджер может подрезать окно до одного монитора — через полсекунды растягиваем снова
            javax.swing.Timer fix = new javax.swing.Timer(500, e -> {
                Rectangle all = allScreens();
                System.out.println("javawin: frame " + frame.getBounds() + " -> " + all + ", monitors " + screenRects());
                if (!frame.getBounds().equals(all)) frame.setBounds(all);
            });
            fix.setRepeats(false);
            fix.start();
        } else {
            frame.setSize(Math.min(1440, scr.width - 40), Math.min(880, scr.height - 40));
            frame.setLocationRelativeTo(null);
        }
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(Main::keys);
        // общий буфер обмена с Windows (через ClipHost) — только в виртуалке: на настоящем компьютере
        // попытки достучаться до несуществующего хоста раз в 0,7 с подвешивали буфер обмена
        if (osMode && inVM()) ClipSync.start();
        if (osMode) XWindows.start();   // окна Linux-программ — на панель задач
        if (osMode) Drives.start();     // флешки — в «Этот компьютер»
        // JavaOS не нашла свою папку ни на одном диске — сначала спрашиваем, где её создать
        if (osMode && System.getProperty("javaos.nodata") != null && System.getProperty("javaos.data") != null)
            show(new SystemFolder(quick ? Main::openDesktop : Main::boot));
        else if (quick) openDesktop();
        else boot();
        frame.setVisible(true);
        if (osMode) {   // окну без оконного менеджера фокус клавиатуры надо попросить самим
            frame.toFront();
            frame.requestFocus();
        }
    }

    static boolean inVM() {
        try { return java.nio.file.Files.readString(java.nio.file.Path.of("/sys/class/dmi/id/sys_vendor")).contains("QEMU"); }
        catch (Exception e) { return false; }
    }

    static boolean keys(KeyEvent e) {
        if (e.getID() != KeyEvent.KEY_PRESSED) return false;
        int k = e.getKeyCode();
        if (k == KeyEvent.VK_F11 && !osMode) { toggleFullscreen(); return true; }
        if (desktop == null || frame.getContentPane() != desktop) return false;
        if (k == KeyEvent.VK_WINDOWS || (k == KeyEvent.VK_ESCAPE && e.isControlDown())) { desktop.toggleStart(); return true; }
        if (k == KeyEvent.VK_S && e.isControlDown() && e.isShiftDown()) { desktop.screenshot(); return true; }
        if (k == KeyEvent.VK_ESCAPE && e.isControlDown() && e.isShiftDown()) { desktop.open("taskmgr"); return true; }
        if (k == KeyEvent.VK_F4 && e.isAltDown() && desktop.active != null) { desktop.active.close(); return true; }
        if (k == KeyEvent.VK_D && e.isAltDown()) { desktop.showDesktop(); return true; }
        return false;
    }

    static void show(JComponent c) {
        // экраны загрузки/входа/выключения — на главном мониторе, остальные мониторы тёмные
        if (osMode && !(c instanceof Desktop) && GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices().length > 1) {
            JPanel holder = new JPanel(null) {
                @Override public void doLayout() { c.setBounds(screenRects().get(0)); }
            };
            holder.setBackground(Color.BLACK);
            holder.add(c);
            frame.setContentPane(holder);
            frame.revalidate();
            frame.repaint();
            c.requestFocusInWindow();
            return;
        }
        frame.setContentPane(c);
        frame.revalidate();
        frame.repaint();
        c.requestFocusInWindow();
    }

    static void boot() {
        closeDesktop();
        show(new Screens.Boot(Main::lock));
    }

    public static void lock() {
        if (desktop != null) {
            // экран блокировки поверх, рабочий стол со всеми окнами остаётся жить
            Desktop d = desktop;
            show(new Screens.Login(() -> { show(d); d.relayout(); }));
            return;
        }
        show(new Screens.Login(Main::openDesktop));
    }

    static void openDesktop() {
        desktop = new Desktop();
        show(desktop);
        SwingUtilities.invokeLater(desktop::welcome);
    }

    public static void logout() {
        closeDesktop();
        lock();
    }

    public static void shutdown(boolean restart) {
        closeDesktop();
        show(new Screens.Shutdown(restart, () -> {
            if (osMode) System.exit(restart ? 11 : 10);   // код выхода ловит скрипт запуска JavaOS
            else if (restart) boot();
            else {
                frame.dispose();
                System.exit(0);
            }
        }));
    }

    static void closeDesktop() {
        if (desktop != null) {
            desktop.dispose();
            desktop = null;
        }
    }

    // все мониторы вместе (режим двух мониторов): окно системы закрывает их целиком
    static Rectangle allScreens() {
        Rectangle all = null;
        for (GraphicsDevice d : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle b = d.getDefaultConfiguration().getBounds();
            all = all == null ? b : all.union(b);
        }
        return all;
    }

    // мониторы относительно окна системы; первый — главный. Главный выбирает X-сервер (Option "Primary"):
    // gui.sh назначает им физически самый большой экран — разрешение у мониторов бывает одинаковое.
    static java.util.List<Rectangle> screenRects() {
        Rectangle all = allScreens();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice main = ge.getDefaultScreenDevice();
        java.util.List<Rectangle> l = new java.util.ArrayList<>();
        Rectangle first = null;
        for (GraphicsDevice d : ge.getScreenDevices()) {
            Rectangle b = d.getDefaultConfiguration().getBounds();
            b.translate(-all.x, -all.y);
            if (d == main) first = b; else l.add(b);
        }
        l.sort((a, b) -> Integer.compare(a.x, b.x));
        if (first != null) l.add(0, first);
        return l;
    }

    static void toggleFullscreen() {
        fullscreen = !fullscreen;
        if (fullscreen) normalBounds = frame.getBounds();
        frame.dispose();
        frame.setUndecorated(fullscreen);
        if (fullscreen) frame.setBounds(frame.getGraphicsConfiguration().getBounds());
        else frame.setBounds(normalBounds);
        frame.setVisible(true);
    }

    // после смены темы/обоев/акцента в «Параметрах»
    public static void applyTheme(Component any) {
        Theme.save();
        Theme.applyUI();
        Component root = frame != null ? frame : SwingUtilities.getRoot(any);
        if (root != null) {
            SwingUtilities.updateComponentTreeUI(root);
            root.repaint();
        }
    }
}
