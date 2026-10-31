import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;

// screendump QEMU пишет PPM (P6) — переводим в PNG.
public class Ppm2Png {
    public static void main(String[] a) throws Exception {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(Path.of(a[0]))))) {
            String magic = token(in);
            int w = Integer.parseInt(token(in)), h = Integer.parseInt(token(in));
            token(in);
            if (!magic.equals("P6")) throw new IOException(magic);
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++)
                    img.setRGB(x, y, (in.readUnsignedByte() << 16) | (in.readUnsignedByte() << 8) | in.readUnsignedByte());
            ImageIO.write(img, "png", new File(a[1]));
            System.out.println(w + "x" + h + " -> " + a[1]);
        }
    }

    static String token(DataInputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '#') { while ((c = in.read()) != '\n' && c != -1) ; continue; }
            if (Character.isWhitespace(c)) { if (b.length() > 0) break; continue; }
            b.append((char) c);
        }
        return b.toString();
    }
}
