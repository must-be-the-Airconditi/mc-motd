package mcmotd;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/** 应用图标：8×8 像素草方块，放大到任意尺寸 */
final class Icon {

    private static final int[] GRASS = {0x55FF55, 0x3FB83F, 0x4FE24F};
    private static final int[] DIRT = {0x6B4B2F, 0x543A24, 0x7A5636};

    private Icon() {
    }

    static void write(File file) throws IOException {
        ImageIO.write(render(256), "png", file);
    }

    static BufferedImage render(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        int cell = Math.max(1, size / 8);
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int rgb = y <= 2 ? GRASS[(x * 7 + y * 3) % 3] : DIRT[(x + y * 5) % 3];
                g.setColor(new java.awt.Color(0xFF000000 | rgb, true));
                g.fillRect(x * cell, y * cell, cell, cell);
            }
        }
        g.setColor(new java.awt.Color(0, 0, 0, 60));
        g.drawRect(0, 0, size - 1, size - 1);
        g.dispose();
        return image;
    }
}
