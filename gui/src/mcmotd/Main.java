package mcmotd;

import java.io.File;

/** JavaFX GUI 入口 */
public final class Main {

    public static final String VERSION = "1.5.0";

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String shot = null;
        String icon = null;
        String address = null;
        int width = -1;
        int height = -1;
        boolean bedrock = false;
        boolean version = false;
        boolean help = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--shot": {
                    if (i + 1 >= args.length) {
                        usage("--shot 需要一个输出文件");
                    }
                    shot = args[++i];
                    while (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        String value = args[++i];
                        if (value.matches("\\d+")) {
                            if (width < 0) {
                                width = Integer.parseInt(value);
                            } else {
                                height = Integer.parseInt(value);
                            }
                        } else if (address == null) {
                            address = value;
                        } else {
                            usage("多余的参数：" + value);
                        }
                    }
                    break;
                }
                case "--icon":
                    if (i + 1 >= args.length) {
                        usage("--icon 需要一个输出文件");
                    }
                    icon = args[++i];
                    break;
                case "--bedrock":
                    bedrock = true;
                    break;
                case "-v":
                case "--version":
                    version = true;
                    break;
                case "-h":
                case "--help":
                    help = true;
                    break;
                default:
                    if (address == null) {
                        address = arg;
                    } else {
                        usage("未知参数：" + arg);
                    }
            }
        }

        if (help) {
            printUsage();
            return;
        }
        if (version) {
            System.out.println("mc-motd " + VERSION);
            return;
        }
        if (icon != null) {
            Icon.write(new File(icon));
            System.out.println("已生成 " + icon);
            return;
        }
        if (shot != null) {
            Shot.run(shot, address == null ? "play.cubecraft.net" : address,
                    width > 0 ? width : 1040, height > 0 ? height : 940, bedrock);
            return;
        }
        // JavaFX 从 JDK 11 起不再随 JDK 分发：双击 jar（= java -jar）没带 --module-path，
        // 只会抛 NoClassDefFoundError 然后什么都不显示，这里给个能看懂的说法。
        try {
            Class.forName("javafx.application.Application", false, Main.class.getClassLoader());
        } catch (ClassNotFoundException missing) {
            System.err.println("找不到 JavaFX：这个 jar 只包含界面代码，需要单独的 JavaFX 运行时。");
            System.err.println("JavaFX is not on the module path; this jar needs the JavaFX runtime.");
            System.err.println("运行方式：java --module-path <javafx目录> --add-modules javafx.controls -cp mc-motd-gui.jar mcmotd.Main");
            System.err.println("或者下载自带 JavaFX 的 portable.zip / 安装包（见 Releases）。");
            System.exit(3);
        }
        App.launchApp(address, bedrock);
    }

    private static void usage(String message) {
        System.err.println(message);
        printUsage();
        System.exit(2);
    }

    private static void printUsage() {
        System.out.println("mc-motd GUI " + VERSION + "（JavaFX）");
        System.out.println("用法：");
        System.out.println("  [java ... mcmotd.Main] [地址]            启动界面，可选预填地址");
        System.out.println("  --shot <out.png> [地址] [宽] [高] [--bedrock]  渲染截图");
        System.out.println("  --icon <out.png>                        生成 256×256 图标");
        System.out.println("  -v, --version                           显示版本");
        System.out.println("  -h, --help                              显示帮助");
        System.out.println("环境变量：MC_MOTD_PROXY、MC_MOTD_LANG");
    }
}
