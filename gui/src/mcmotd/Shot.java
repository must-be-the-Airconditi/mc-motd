package mcmotd;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;

/** 无头渲染：查询一次然后把界面截图存成 PNG（用于 README 截图） */
final class Shot {

    private Shot() {
    }

    static void run(String out, String address, int width, int height, boolean bedrock) throws Exception {
        // 查询发生在 buildUi 之前，语言要先按 -Dmcmotd.lang/MC_MOTD_LANG/prefs 定下来，
        // 否则英文界面里报错详情还是中文
        Strings.init(java.util.prefs.Preferences.userNodeForPackage(App.class));
        Info result = null;
        String failure = null;
        try {
            Slp.Ping ping = bedrock
                    ? Bedrock.ping(address, 8000, Strings.isEnglish() ? "en" : "zh")
                    : Slp.ping(address, 8000, null);
            result = Info.of(ping);
        } catch (Exception e) {
            failure = e.getMessage() == null ? e.toString() : e.getMessage();
            System.err.println(Strings.t("shot.failed") + failure);
        }
        final Info info = result;
        final String error = failure;

        CountDownLatch done = new CountDownLatch(1);
        Platform.startup(() -> {
        });
        Platform.runLater(() -> {
            try {
                App app = new App();
                app.prepare(address, bedrock);
                app.preload(info);
                if (error != null) {
                    app.preloadError(Strings.f("error.title", address), error);
                }
                Parent root = app.buildUi();
                Scene scene = new Scene(root, width, height);
                app.applyStylesheet(scene);
                root.applyCss();
                root.layout();
                WritableImage image = root.snapshot(null, null);
                writePng(image, new File(out));
                System.out.println("已生成 " + out);
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                done.countDown();
            }
        });
        done.await(60, TimeUnit.SECONDS);
        Runtime.getRuntime().halt(0);
    }

    private static void writePng(WritableImage image, File file) throws Exception {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage buffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffer.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        javax.imageio.ImageIO.write(buffer, "png", file);
    }
}
