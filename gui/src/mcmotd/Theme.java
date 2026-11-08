package mcmotd;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** 配色和字体 */
public final class Theme {

    private Theme() {
    }

    public static final Color BG = new Color(0x070B09);
    public static final Color PANEL = new Color(0x0D1512);
    public static final Color PANEL_SOFT = new Color(0x0A110E);
    public static final Color INPUT = new Color(0x08110D);
    public static final Color LINE = new Color(85, 255, 85, 46);
    public static final Color LINE_STRONG = new Color(85, 255, 85, 120);
    public static final Color TEXT = new Color(0xE8F5EC);
    public static final Color MUTED = new Color(0x7E998A);
    public static final Color MUTED_2 = new Color(0x5B7365);
    public static final Color GREEN = new Color(0x55FF55);
    public static final Color GREEN_DARK = new Color(0x2C8F2C);
    public static final Color GREEN_DARKER = new Color(0x1B5E1B);
    public static final Color CYAN = new Color(0x55FFFF);
    public static final Color YELLOW = new Color(0xFFFF55);
    public static final Color RED = new Color(0xFF5555);

    private static String pick(String... candidates) {
        Set<String> available = new HashSet<>(Arrays.asList(
                GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        for (String name : candidates) {
            if (available.contains(name)) {
                return name;
            }
        }
        return Font.SANS_SERIF;
    }

    private static String sansName;
    private static String monoName;

    private static synchronized void init() {
        if (sansName == null) {
            sansName = pick("Noto Sans CJK SC", "Noto Sans SC", "Source Han Sans SC",
                    "Microsoft YaHei", "PingFang SC", "SansSerif");
            monoName = pick("JetBrains Mono", "Noto Sans Mono", "DejaVu Sans Mono", "Monospaced");
        }
    }

    public static Font sans(int size, int style) {
        init();
        return new Font(sansName, style, size);
    }

    public static Font mono(int size, int style) {
        init();
        return new Font(monoName, style, size);
    }

    public static Color latencyColor(double ms) {
        if (ms < 120) {
            return GREEN;
        }
        if (ms < 300) {
            return YELLOW;
        }
        return RED;
    }

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }
}
