package javawin;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// JavaOS не нашла свою папку ни на одном диске: просим выбрать папку (диск → папки на нём), где её создать.
// Там будут файлы, настройки, скачанное и программы Arch Linux (файл javaos-data.img). После создания — перезагрузка.
public class SystemFolder extends JPanel {
    record Disk(String dev, String label, String fs, long free, long size, File root, String state) {}

    final Runnable skip;
    final JPanel list = new JPanel();
    final UI.Label where = UI.dim("Ищу диски…");
    final JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    Disk disk;       // выбранный диск (null — список дисков)
    String rel = ""; // папка на нём (путь от корня диска)
    boolean busy;

    SystemFolder(Runnable skip) {
        super(new GridBagLayout());
        this.skip = skip;
        setBackground(new Color(0x0f1b33));
        JPanel card = new JPanel(new BorderLayout(0, 12)) {
            @Override protected void paintComponent(Graphics g0) {
                Graphics2D g = (Graphics2D) g0.create();
                UI.aa(g);
                g.setColor(Theme.window());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                g.dispose();
            }
        };
        card.setOpaque(false);
        card.setBorder(new EmptyBorder(26, 30, 22, 30));
        card.setPreferredSize(new Dimension(680, 580));
        JPanel head = new JPanel(new GridLayout(3, 1, 0, 4));
        head.setOpaque(false);
        head.add(UI.title("Выберите папку системы", 26f));
        head.add(UI.label("Здесь JavaOS будет хранить все файлы, настройки, скачанное и приложения."));
        head.add(where);
        card.add(head, BorderLayout.NORTH);
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(list, BorderLayout.NORTH);
        card.add(UI.scroll(top), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        bottom.setOpaque(false);
        actions.setOpaque(false);
        bottom.add(actions, BorderLayout.NORTH);
        bottom.add(UI.button("Не сохранять — всё забудется после выключения", skip), BorderLayout.SOUTH);
        card.add(bottom, BorderLayout.SOUTH);
        add(card);
        loadDisks();
    }

    static List<String> run(String... args) {
        List<String> out = new ArrayList<>();
        List<String> cmd = new ArrayList<>(List.of(System.getProperty("javaos.data")));
        cmd.addAll(List.of(args));
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                for (String l; (l = r.readLine()) != null; ) out.add(l);
            }
            p.waitFor();
        } catch (Exception e) { out.add("ERR: " + e.getMessage()); }
        return out;
    }

    static long mbLimit(Disk d) { return Math.min(d.free() - 200, d.fs().equals("vfat") ? 3900 : 16000); }

    void loadDisks() {
        disk = null;
        rel = "";
        new Thread(() -> {
            List<Disk> disks = new ArrayList<>();
            for (String l : run("list")) {
                String[] f = l.split("\\|", 7);
                if (f.length == 7) try {
                    disks.add(new Disk(f[0], f[1], f[2], Long.parseLong(f[3].trim()), Long.parseLong(f[4].trim()), new File(f[5]), f[6].trim()));
                } catch (NumberFormatException ignored) {}
            }
            SwingUtilities.invokeLater(() -> {
                list.removeAll();
                actions.removeAll();
                where.setText(disks.isEmpty() ? "Подходящих дисков нет. Вставь флешку (FAT32 или exFAT) и перезагрузи компьютер."
                    : "Шаг 1: выбери диск.");
                for (Disk d : disks) {
                    String name = d.label() + "  (" + d.dev().replace("/dev/", "") + (d.fs().equals("ntfs") ? ", диск Windows" : "") + ")";
                    if (d.state().equals("locked")) {
                        list.add(item("drive", name, "Зашифрован BitLocker — JavaOS не может его открыть", null));
                        continue;
                    }
                    String info = String.format("%.0f ГБ, свободно %.1f ГБ", d.size() / 1024.0, d.free() / 1024.0);
                    if (d.state().equals("hiber"))
                        info += " · только чтение: Windows не выключилась до конца (быстрый запуск)";
                    list.add(item("drive", name, info, () -> open(d, "")));
                }
                refresh();
            });
        }, "data-list").start();
    }

    // папки внутри выбранного диска
    void open(Disk d, String path) {
        disk = d;
        rel = path;
        list.removeAll();
        actions.removeAll();
        where.setText("Шаг 2: открой нужную папку и нажми «Выбрать эту папку».   " + d.label() + ":/" + rel);
        list.add(item("up", rel.isEmpty() ? "← К списку дисков" : "← Назад", "", () -> {
            if (rel.isEmpty()) loadDisks();
            else open(d, rel.contains("/") ? rel.substring(0, rel.lastIndexOf('/')) : "");
        }));
        File[] kids = new File(d.root(), rel).listFiles(f -> f.isDirectory() && !f.getName().startsWith(".") && !f.getName().equals("lost+found"));
        if (kids != null) {
            Arrays.sort(kids, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            for (File k : kids) {
                String sub = rel.isEmpty() ? k.getName() : rel + "/" + k.getName();
                list.add(item("folder", k.getName(), "", () -> open(d, sub)));
            }
        }
        long mb = mbLimit(d);
        boolean ro = d.state().equals("hiber");
        UI.Btn pick = UI.accent("Выбрать эту папку", () -> create(mb));
        pick.setEnabled(mb >= 1000 && !ro);
        actions.add(pick);
        if (!ro) actions.add(UI.button("Новая папка", this::newFolder));
        if (ro) actions.add(UI.dim("<html>На этот диск сейчас писать нельзя: Windows не выключилась до конца.<br>Выключи компьютер из Windows через «Завершение работы» с зажатым Shift — и попробуй снова.</html>"));
        else if (mb < 1000) actions.add(UI.dim("Мало места на диске — нужно хотя бы 1,2 ГБ"));
        refresh();
    }

    void newFolder() {
        String name = JOptionPane.showInputDialog(this, "Имя новой папки:", "JavaOS");
        if (name == null || name.isBlank()) return;
        name = name.trim().replaceAll("[/\\\\:*?\"<>|]", "_");
        File f = new File(new File(disk.root(), rel), name);
        if (!f.mkdirs() && !f.isDirectory()) { where.setText("Не получилось создать папку «" + name + "»."); return; }
        open(disk, rel.isEmpty() ? name : rel + "/" + name);
    }

    void create(long mb) {
        if (busy) return;
        busy = true;
        for (Component c : actions.getComponents()) c.setEnabled(false);
        for (Component c : list.getComponents()) c.setEnabled(false);
        where.setText("Создаю папку системы… это может занять пару минут, не выключай компьютер.");
        String dev = disk.dev(), path = rel;
        new Thread(() -> {
            List<String> r = run("create", dev, path, String.valueOf(mb));
            String last = r.isEmpty() ? "ERR: нет ответа" : r.get(r.size() - 1);
            SwingUtilities.invokeLater(() -> {
                busy = false;
                if (last.startsWith("OK")) {
                    where.setText("Готово! Перезагружаю компьютер…");
                    new javax.swing.Timer(1500, e -> System.exit(11)).start();   // gui.sh перезагрузит, при загрузке папка найдётся
                } else {
                    where.setText(last.replaceFirst("^ERR:\\s*", ""));
                    for (Component c : actions.getComponents()) c.setEnabled(true);
                    for (Component c : list.getComponents()) c.setEnabled(true);
                }
            });
        }, "data-create").start();
    }

    void refresh() {
        list.revalidate();
        list.repaint();
        actions.revalidate();
        actions.repaint();
    }

    JComponent item(String icon, String title, String info, Runnable click) {
        UI.Btn b = UI.button(info.isEmpty() ? title : "<html>" + title + "<br><small>" + info + "</small></html>", click == null ? () -> {} : click);
        if (click == null) b.setEnabled(false);
        b.flat = true;
        b.setIcon(Icons.icon(icon, 22));
        b.setIconTextGap(12);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setBorder(new EmptyBorder(8, 12, 8, 12));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height + 4));
        return b;
    }
}
