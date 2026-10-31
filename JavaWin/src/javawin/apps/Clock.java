package javawin.apps;

import javawin.*;

import javax.swing.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

// Часы: секундомер (с кругами), таймер и будильник. Время вышло — окно звенит и мигает.
public class Clock extends JPanel implements AppBody {
    final Timer tick = new Timer(50, e -> update());

    // секундомер
    long swStart, swAcc;
    boolean swRun;
    final JLabel swLabel = big();
    final DefaultListModel<String> laps = new DefaultListModel<>();

    // таймер
    long tmEnd, tmLeft = 5 * 60_000;
    boolean tmRun;
    final JLabel tmLabel = big();
    final JSpinner tmMin = new JSpinner(new SpinnerNumberModel(5, 0, 999, 1));
    final JSpinner tmSec = new JSpinner(new SpinnerNumberModel(0, 0, 59, 5));

    // будильник
    final JLabel now = big();
    final JSpinner alH = new JSpinner(new SpinnerNumberModel(7, 0, 23, 1));
    final JSpinner alM = new JSpinner(new SpinnerNumberModel(0, 0, 59, 1));
    final JCheckBox alOn = new JCheckBox("Будильник включён");
    final JLabel alInfo = new JLabel(" ");
    String rangAt = "";

    long blinkUntil;
    final javawin.Desktop d;

    public Clock(javawin.Desktop d, java.nio.file.Path p) {
        super(new BorderLayout());
        this.d = d;
        setBackground(Theme.window());
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Секундомер", stopwatch());
        tabs.addTab("Таймер", timer());
        tabs.addTab("Будильник", alarm());
        add(tabs);
        tick.start();
        update();
    }

    @Override public void closing() { tick.stop(); }

    static JLabel big() {
        JLabel l = new JLabel("00:00", SwingConstants.CENTER);
        l.setFont(Theme.font(Font.BOLD, 56f));
        return l;
    }

    static JPanel row(JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 8));
        p.setOpaque(false);
        for (JComponent c : cs) p.add(c);
        return p;
    }

    static JButton btn(String t, Runnable r) {
        JButton b = new JButton(t);
        b.addActionListener(e -> r.run());
        return b;
    }

    JPanel page(JComponent top, JComponent center) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        p.setOpaque(false);
        p.add(top, BorderLayout.NORTH);
        if (center != null) p.add(center);
        return p;
    }

    JComponent stopwatch() {
        JButton start = new JButton("Старт");
        start.addActionListener(e -> {
            if (swRun) { swAcc += System.currentTimeMillis() - swStart; swRun = false; start.setText("Старт"); }
            else { swStart = System.currentTimeMillis(); swRun = true; start.setText("Пауза"); }
        });
        JButton lap = btn("Круг", () -> { if (swRun) laps.add(0, "Круг " + (laps.size() + 1) + ":   " + fmt(swTime(), true)); });
        JButton reset = btn("Сброс", () -> { swRun = false; swAcc = 0; laps.clear(); start.setText("Старт"); });
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(swLabel);
        top.add(row(start, lap, reset), BorderLayout.SOUTH);
        JList<String> list = new JList<>(laps);
        list.setFont(Theme.font(Font.PLAIN, 16f));
        return page(top, new JScrollPane(list));
    }

    long swTime() { return swAcc + (swRun ? System.currentTimeMillis() - swStart : 0); }

    JComponent timer() {
        JButton start = new JButton("Старт");
        start.addActionListener(e -> {
            if (tmRun) { tmLeft = tmEnd - System.currentTimeMillis(); tmRun = false; start.setText("Старт"); }
            else {
                if (tmLeft <= 0) tmLeft = setTime();
                if (tmLeft <= 0) return;
                tmEnd = System.currentTimeMillis() + tmLeft; tmRun = true; start.setText("Пауза");
            }
        });
        JButton reset = btn("Сброс", () -> { tmRun = false; tmLeft = setTime(); start.setText("Старт"); });
        tmMin.addChangeListener(e -> { if (!tmRun) tmLeft = setTime(); });
        tmSec.addChangeListener(e -> { if (!tmRun) tmLeft = setTime(); });
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(tmLabel);
        JPanel south = new JPanel(new GridLayout(2, 1));
        south.setOpaque(false);
        south.add(row(new JLabel("Минуты:"), tmMin, new JLabel("Секунды:"), tmSec));
        south.add(row(start, reset));
        top.add(south, BorderLayout.SOUTH);
        return page(top, null);
    }

    long setTime() { return ((Integer) tmMin.getValue() * 60L + (Integer) tmSec.getValue()) * 1000; }

    JComponent alarm() {
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(now);
        JPanel south = new JPanel(new GridLayout(3, 1));
        south.setOpaque(false);
        south.add(row(new JLabel("Разбудить в"), alH, new JLabel(":"), alM));
        alOn.setOpaque(false);
        south.add(row(alOn));
        south.add(row(alInfo));
        top.add(south, BorderLayout.SOUTH);
        return page(top, null);
    }

    void update() {
        swLabel.setText(fmt(swTime(), true));
        if (tmRun) {
            long left = tmEnd - System.currentTimeMillis();
            if (left <= 0) { tmRun = false; tmLeft = 0; ring("Таймер: время вышло!"); }
            else tmLeft = left;
        }
        tmLabel.setText(fmt(Math.max(tmLeft, 0) + (tmRun ? 999 : 0), false));

        LocalTime t = LocalTime.now();
        now.setText(t.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        String want = String.format("%02d:%02d", alH.getValue(), alM.getValue());
        alInfo.setText(alOn.isSelected() ? "Зазвенит в " + want + " (окно Часов должно быть открыто)" : " ");
        if (alOn.isSelected() && t.format(DateTimeFormatter.ofPattern("HH:mm")).equals(want) && !want.equals(rangAt)) {
            rangAt = want;
            ring("Будильник: " + want + "!");
        }

        boolean blink = System.currentTimeMillis() < blinkUntil && (System.currentTimeMillis() / 300) % 2 == 0;
        Color c = blink ? Theme.accent : Theme.text();
        swLabel.setForeground(Theme.text());
        tmLabel.setForeground(c);
        now.setForeground(c);
    }

    void ring(String text) {
        blinkUntil = System.currentTimeMillis() + 8000;
        new Thread(() -> {
            for (int i = 0; i < 6; i++) {
                Toolkit.getDefaultToolkit().beep();
                beep();
                try { Thread.sleep(500); } catch (InterruptedException e) { return; }
            }
        }, "clock-ring").start();
        SwingUtilities.invokeLater(() -> d.toast("Часы", text));
    }

    // короткий писк через звуковую карту (beep() на многих компьютерах молчит)
    static void beep() {
        try {
            float rate = 44100;
            byte[] buf = new byte[(int) (rate * 0.25)];
            for (int i = 0; i < buf.length; i++) buf[i] = (byte) (Math.sin(i / rate * 2 * Math.PI * 880) * 90);
            javax.sound.sampled.AudioFormat f = new javax.sound.sampled.AudioFormat(rate, 8, 1, true, false);
            try (javax.sound.sampled.SourceDataLine line = javax.sound.sampled.AudioSystem.getSourceDataLine(f)) {
                line.open(f);
                line.start();
                line.write(buf, 0, buf.length);
                line.drain();
            }
        } catch (Exception ignored) {}
    }

    static String fmt(long ms, boolean centis) {
        long s = ms / 1000, h = s / 3600, m = s / 60 % 60;
        String r = h > 0 ? String.format("%d:%02d:%02d", h, m, s % 60) : String.format("%02d:%02d", m, s % 60);
        return centis ? r + String.format(",%02d", ms / 10 % 100) : r;
    }
}
