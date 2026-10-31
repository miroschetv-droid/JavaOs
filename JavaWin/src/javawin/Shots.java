package javawin;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;

// java -jar JavaWin.jar --shot <папка> — отрисовать экраны в png без окна (для проверки вида).
final class Shots {
    static final int W = 1440, H = 860;

    static void make(File dir) throws Exception {
        dir.mkdirs();
        SwingUtilities.invokeAndWait(() -> {
            try {
                Theme.applyUI();
                Desktop d = new Desktop();
                d.setSize(W, H);
                layout(d);
                d.relayout();
                Win ex = d.open("explorer", Vfs.desktop());
                ex.setLocation(80, 50);
                Win np = d.open("notepad", Vfs.desktop().resolve("Прочти меня.txt"));
                np.setLocation(560, 140);
                Win calc = d.open("calc");
                calc.setLocation(1060, 40);
                layout(d);
                save(d, new File(dir, "1-desktop.png"));

                for (Win w : d.windows()) d.closeWin(w);
                d.open("cmd").setLocation(60, 60);
                d.open("mines").setLocation(950, 80);
                d.open("taskmgr").setLocation(380, 300);
                layout(d);
                save(d, new File(dir, "2-apps.png"));

                for (Win w : d.windows()) d.closeWin(w);
                d.open("settings").setLocation(60, 30);
                d.startMenu.setVisible(true);
                d.startMenu.filter();
                layout(d);
                save(d, new File(dir, "3-start-settings.png"));

                for (Win w : d.windows()) d.closeWin(w);
                d.startMenu.setVisible(false);
                d.open("paint", Vfs.pics().resolve("Закулисье.png")).setLocation(200, 40);
                Theme.dark = false;
                Theme.applyUI();
                SwingUtilities.updateComponentTreeUI(d);
                layout(d);
                save(d, new File(dir, "4-light-paint.png"));
                Theme.load();

                Screens.Login l = new Screens.Login(() -> {});
                l.setSize(W, H);
                layout(l);
                save(l, new File(dir, "5-lock.png"));
                d.dispose();
            } catch (Exception e) { throw new RuntimeException(e); }
        });
        System.exit(0);
    }

    static void layout(Component c) {
        if (c instanceof Container k) {
            k.invalidate();
            k.doLayout();
            for (Component x : k.getComponents()) layout(x);
        }
    }

    static void save(JComponent c, File f) throws Exception {
        BufferedImage img = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        c.paint(g);
        g.dispose();
        ImageIO.write(img, "png", f);
    }
}
