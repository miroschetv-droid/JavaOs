// Прослойка вместо qemu-system-x86_64.exe:
//   без параметров  — запускает JavaOS (как «Start VM.bat»: образ, постоянный диск, virtio, WHPX);
//   с параметрами   — передаёт их настоящему QEMU (qemu-system-x86_64-real.exe) и ждёт его.
// Сборка: csc /target:winexe /out:qemu-system-x86_64.exe QemuJavaOS.cs
using System;
using System.Diagnostics;
using System.IO;
using System.Text;

class QemuJavaOS {
    const string JavaOS = @"F:\JavaOS";

    static int Main(string[] args) {
        string dir = AppDomain.CurrentDomain.BaseDirectory;
        string real = Path.Combine(dir, "qemu-system-x86_64-real.exe");

        if (args.Length == 0) {
            // как двойной щелчок по ярлыку JavaOS: свежий образ, буфер обмена, окно GTK
            var p = new ProcessStartInfo("cmd.exe", "/c \"\"" + Path.Combine(JavaOS, "Start VM.bat") + "\"\"");
            p.WorkingDirectory = JavaOS;
            p.UseShellExecute = false;
            p.CreateNoWindow = true;
            Process.Start(p);
            return 0;
        }

        var sb = new StringBuilder();
        foreach (string a in args) sb.Append(Quote(a)).Append(' ');
        var si = new ProcessStartInfo(real, sb.ToString());
        si.UseShellExecute = false;
        var q = Process.Start(si);
        q.WaitForExit();
        return q.ExitCode;
    }

    // правила кавычек Windows (CommandLineToArgvW)
    static string Quote(string a) {
        if (a.Length > 0 && a.IndexOfAny(new[] { ' ', '\t', '"' }) < 0) return a;
        var sb = new StringBuilder("\"");
        int bs = 0;
        foreach (char c in a) {
            if (c == '\\') { bs++; continue; }
            if (c == '"') sb.Append('\\', bs * 2 + 1);
            else sb.Append('\\', bs);
            bs = 0;
            sb.Append(c);
        }
        sb.Append('\\', bs * 2).Append('"');
        return sb.ToString();
    }
}
