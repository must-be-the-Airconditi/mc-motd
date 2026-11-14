package mcmotd;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ColorUIResource;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** 入口：窗口模式 / 截图模式 */
public final class Main {

    public static final String VERSION = "1.3.0";

    private static JFrame frame;

    private Main() {
    }

    public static void main(String[] args) {
        Strings.init(null);
        if (args.length > 0 && args[0].equals("--shot")) {
            try {
                String out = args.length > 1 ? args[1] : "shot.png";
                String address = args.length > 2 ? args[2] : "play.cubecraft.net";
                int width = args.length > 3 ? Integer.parseInt(args[3]) : 1040;
                int height = args.length > 4 ? Integer.parseInt(args[4]) : 940;
                boolean bedrock = false;
                for (String arg : args) {
                    if ("--bedrock".equals(arg)) bedrock = true;
                }
                Ui.renderShot(address, new java.io.File(out), width, height, bedrock);
            } catch (Exception e) {
                System.err.println("截图失败：" + e.getMessage());
                System.exit(1);
            }
            return;
        }
        if (args.length > 1 && args[0].equals("--icon")) {
            try {
                writeIcon(new java.io.File(args[1]), 256);
                System.out.println("已写入图标 " + args[1]);
            } catch (Exception e) {
                System.err.println("生成图标失败：" + e.getMessage());
                System.exit(1);
            }
            return;
        }
        if (args.length > 0 && (args[0].equals("--version") || args[0].equals("-v"))) {
            System.out.println("mc-motd-gui " + VERSION);
            return;
        }
        if (args.length > 0 && args[0].equals("--help")) {
            System.out.println("用法：java -jar mc-motd-gui.jar [服务器地址]");
            System.out.println("     java -jar mc-motd-gui.jar --shot <out.png> [地址]");
            return;
        }

        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        final String initial = args.length > 0 ? args[0] : null;
        SwingUtilities.invokeLater(() -> createFrame(initial));
    }

    private static void createFrame(String initial) {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {
            // 用默认外观
        }
        applyDarkDefaults();
        Strings.init(java.util.prefs.Preferences.userNodeForPackage(Ui.class));

        frame = new JFrame(Strings.t("window.title"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setIconImage(makeIcon());
        frame.setSize(1040, 940);
        frame.setMinimumSize(new Dimension(900, 700));
        frame.setLocationRelativeTo(null);

        Ui ui = new Ui(Main::switchLanguage);
        frame.setContentPane(ui);
        frame.setVisible(true);

        String address = initial != null && !initial.trim().isEmpty()
                ? initial
                : System.getProperty("mcmotd.address", "");
        if (address != null && !address.trim().isEmpty()) {
            ui.ping(address);
        }
    }

    /** 切换语言：重建界面，并把上次查询的地址重新查一遍 */
    private static void switchLanguage() {
        if (frame == null) {
            return;
        }
        String last = java.util.prefs.Preferences.userNodeForPackage(Ui.class).get("address", "");
        Ui ui = new Ui(Main::switchLanguage);
        frame.setContentPane(ui);
        frame.setTitle(Strings.t("window.title"));
        frame.revalidate();
        frame.repaint();
        if (!last.isEmpty()) {
            ui.ping(last);
        }
    }

    private static void applyDarkDefaults() {
        UIManager.put("Panel.background", new ColorUIResource(Theme.BG));
        UIManager.put("Label.foreground", new ColorUIResource(Theme.TEXT));
        UIManager.put("TextField.background", new ColorUIResource(Theme.INPUT));
        UIManager.put("TextField.foreground", new ColorUIResource(Theme.TEXT));
        UIManager.put("TextField.caretForeground", new ColorUIResource(Theme.GREEN));
        UIManager.put("TextField.selectionBackground", new ColorUIResource(Theme.withAlpha(Theme.GREEN, 70)));
        UIManager.put("TextField.selectionForeground", new ColorUIResource(Theme.TEXT));
        UIManager.put("TextArea.background", new ColorUIResource(0x050907));
        UIManager.put("TextArea.foreground", new ColorUIResource(0x9FC4AC));
        UIManager.put("TextArea.caretForeground", new ColorUIResource(Theme.GREEN));
        UIManager.put("CheckBox.background", new ColorUIResource(Theme.BG));
        UIManager.put("CheckBox.foreground", new ColorUIResource(Theme.MUTED));
        UIManager.put("ScrollBar.width", 8);
        UIManager.put("ScrollBar.thumb", new ColorUIResource(Theme.withAlpha(Theme.GREEN, 70)));
        UIManager.put("ScrollBar.track", new ColorUIResource(Theme.BG));
        UIManager.put("ScrollPane.background", new ColorUIResource(Theme.BG));
        UIManager.put("Viewport.background", new ColorUIResource(Theme.BG));
        UIManager.put("ProgressBar.background", new ColorUIResource(Theme.PANEL_SOFT));
        UIManager.put("ProgressBar.foreground", new ColorUIResource(Theme.GREEN));
        UIManager.put("ToolTip.background", new ColorUIResource(Theme.PANEL));
        UIManager.put("ToolTip.foreground", new ColorUIResource(Theme.TEXT));
    }

    private static void writeIcon(java.io.File out, int size) throws java.io.IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = image.createGraphics();
        Ui.LogoView logo = new Ui.LogoView(size);
        logo.setSize(size, size);
        logo.paintComponent(g);
        g.dispose();
        javax.imageio.ImageIO.write(image, "png", out);
    }

    private static BufferedImage makeIcon() {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        Ui.LogoView logo = new Ui.LogoView(64);
        logo.setSize(64, 64);
        logo.paintComponent(g);
        g.dispose();
        return image;
    }
}
