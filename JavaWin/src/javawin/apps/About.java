package javawin.apps;

import javawin.*;
import javawin.Desktop;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class About extends JPanel implements AppBody {
    Win win;

    public About() {
        super(new BorderLayout(0, 14));
        setBorder(new EmptyBorder(22, 26, 18, 26));
        JComponent logo = new JComponent() {
            protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                UI.aa(g);
                Icons.draw(g, "logo", 0, 0, 64);
                g.setColor(Theme.text());
                g.setFont(Theme.font(Font.BOLD, 34f));
                g.drawString(Theme.NAME, 80, 46);
                g.dispose();
            }
        };
        logo.setPreferredSize(new Dimension(400, 66));
        add(logo, BorderLayout.NORTH);
        JPanel txt = new JPanel(new GridLayout(0, 1, 0, 4));
        txt.add(UI.label("Версия " + Theme.VERSION + " (сборка " + Runtime.version().feature() + "." + Runtime.version().update() + ")"));
        txt.add(UI.dim("Своя оконная система, написанная на Java Swing с нуля:"));
        txt.add(UI.dim("окна, рабочий стол, панель задач, меню «Пуск» и программы."));
        txt.add(UI.dim(" "));
        txt.add(UI.dim("Ядро: " + System.getProperty("java.vm.name") + " " + Runtime.version()));
        txt.add(UI.dim("Пользователь: " + Theme.user));
        add(txt, BorderLayout.CENTER);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        b.add(UI.accent("ОК", () -> { if (win != null) win.close(); }));
        add(b, BorderLayout.SOUTH);
    }

    @Override public void attached(Win w) { win = w; }

    @Override public Dimension getPreferredSize() { return new Dimension(470, 310); }
}
