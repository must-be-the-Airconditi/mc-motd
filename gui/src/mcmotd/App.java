package mcmotd;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * JavaFX 界面（Material Design 3 风格，跟随系统明暗）。
 */
public final class App extends Application {

    private static final int TIMEOUT_MS = 8000;
    private static final int HISTORY_MAX = 6;
    private static final String[] EXAMPLES = {"mc.hypixel.net", "play.cubecraft.net", "2b2t.org"};

    private Preferences prefs;
    private Stage stage;
    private boolean dark;
    private String themeMode = "system";
    private Info lastInfo;
    private boolean busy;
    private String initialAddress;
    private boolean initialBedrock;
    private String previewErrorTitle;
    private String previewErrorDetail;

    private TextField addressField;
    private TextField proxyField;
    private ComboBox<String> editionBox;
    private Button pingButton;
    private HBox historyRow;
    private VBox outputBox;

    private static String cachedFont;

    /* ------------------------------------------------------------ 生命周期 */

    static void launchApp(String address, boolean bedrock) {
        List<String> params = new ArrayList<>();
        if (address != null && !address.isBlank()) {
            params.add(address);
        }
        if (bedrock) {
            params.add("bedrock");
        }
        Application.launch(App.class, params.toArray(new String[0]));
    }

    @Override
    public void start(Stage stage) {
        this.stage = stage;
        prepare(null, false);

        boolean editionFlag = false;
        for (String param : getParameters().getRaw()) {
            if ("bedrock".equals(param)) {
                initialBedrock = true;
                editionFlag = true;
            } else if (initialAddress == null && param != null && !param.isBlank()) {
                initialAddress = param.trim();
            }
        }
        if (!editionFlag) {
            initialBedrock = "bedrock".equals(prefs.get("edition", "java"));
        }

        stage.setTitle(Strings.t("window.title"));
        stage.setMinWidth(880);
        stage.setMinHeight(640);
        rebuild();
        if (initialAddress != null && !initialAddress.isBlank()) {
            addressField.setText(initialAddress);
            Platform.runLater(this::ping);
        }
        stage.show();
    }

    void prepare(String address, boolean bedrock) {
        prefs = Preferences.userNodeForPackage(App.class);
        Strings.init(prefs);
        String forcedTheme = System.getProperty("mcmotd.theme");
        themeMode = forcedTheme != null && !forcedTheme.isBlank() ? forcedTheme.trim() : prefs.get("theme", "system");
        if (!java.util.List.of("system", "light", "dark", "mocha", "latte").contains(themeMode)) {
            themeMode = "system";
        }
        dark = resolveDark();
        initialAddress = address;
        initialBedrock = bedrock;
    }

    void preload(Info info) {
        lastInfo = info;
    }

    void preloadError(String title, String detail) {
        previewErrorTitle = title;
        previewErrorDetail = detail;
    }

    void applyStylesheet(Scene scene) {
        var url = App.class.getResource("/app.css");
        if (url != null) {
            scene.getStylesheets().add(url.toExternalForm());
        }
    }

    /* ------------------------------------------------------------ 界面骨架 */

    Parent buildUi() {
        VBox root = new VBox();
        root.getStyleClass().add("app");
        if (dark) {
            root.getStyleClass().add("dark");
        }
        if ("mocha".equals(themeMode)) {
            root.getStyleClass().add("theme-mocha");
        } else if ("latte".equals(themeMode)) {
            root.getStyleClass().add("theme-latte");
        }
        root.setStyle("-fx-font-family: \"" + fontFamily() + "\";");
        root.getChildren().addAll(appBar(), scrollArea(), footer());
        return root;
    }

    /** 改设置后立刻落盘：Preferences 默认要等后台定时器或 JVM 正常退出才写，闪退/秒关会丢 */
    private void savePref(String key, String value) {
        prefs.put(key, value);
        try {
            prefs.flush();
        } catch (BackingStoreException ignored) {
            // 写不进去也不该影响界面
        }
    }

    private void rebuild() {
        String typedAddress = addressField == null ? null : addressField.getText();
        String typedProxy = proxyField == null ? null : proxyField.getText();
        Parent root = buildUi();
        double width = 1040;
        double height = 940;
        if (stage.getScene() != null) {
            width = stage.getScene().getWidth();
            height = stage.getScene().getHeight();
        }
        Scene scene = new Scene(root, width, height);
        applyStylesheet(scene);
        stage.setScene(scene);
        if (typedAddress != null) {
            addressField.setText(typedAddress);
        }
        if (typedProxy != null) {
            proxyField.setText(typedProxy);
        }
    }

    private HBox appBar() {
        VBox titles = new VBox(1);
        Label title = new Label(Strings.t("app.title"));
        title.getStyleClass().add("brand-title");
        Label subtitle = new Label("Minecraft · Server List Ping");
        subtitle.getStyleClass().add("brand-sub");
        titles.getChildren().addAll(title, subtitle);

        Button theme = textButton(themeIcon());
        theme.setTooltip(new Tooltip(themeTooltip()));
        theme.setOnAction(event -> {
            themeMode = switch (themeMode) {
                case "system" -> "light";
                case "light" -> "dark";
                case "dark" -> "mocha";
                case "mocha" -> "latte";
                default -> "system";
            };
            savePref("theme", themeMode);
            dark = resolveDark();
            rebuild();
        });

        Button lang = textButton(Strings.isEnglish() ? "中文" : "EN");
        lang.setOnAction(event -> {
            Strings.toggle();
            savePref("lang", Strings.isEnglish() ? "en" : "zh");
            rebuild();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bar = new HBox(12, logo(30), titles, spacer, theme, lang);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("appbar");
        return bar;
    }

    private ScrollPane scrollArea() {
        VBox content = new VBox(18);
        content.getStyleClass().add("content");
        content.setPadding(new Insets(20, 20, 28, 20));
        content.getChildren().add(formCard());

        historyRow = new HBox(8);
        historyRow.getStyleClass().add("history-row");
        historyRow.setAlignment(Pos.CENTER_LEFT);
        updateHistoryRow();
        content.getChildren().add(historyRow);

        outputBox = new VBox();
        outputBox.getStyleClass().add("output");
        content.getChildren().add(outputBox);
        if (lastInfo != null) {
            showResult(lastInfo);
        } else if (previewErrorTitle != null) {
            showError(previewErrorTitle, previewErrorDetail);
        } else {
            showEmpty();
        }

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("content-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        return scroll;
    }

    private VBox formCard() {
        addressField = new TextField();
        addressField.setPromptText("play.example.com:25565");
        addressField.getStyleClass().add("search-field");
        HBox.setHgrow(addressField, Priority.ALWAYS);
        addressField.setOnAction(event -> ping());

        editionBox = new ComboBox<>();
        editionBox.getItems().addAll("Java", "Bedrock");
        editionBox.getSelectionModel().select(initialBedrock ? 1 : ("bedrock".equals(prefs.get("edition", "java")) ? 1 : 0));
        editionBox.getStyleClass().add("edition-combo");
        editionBox.setPrefWidth(120);
        editionBox.getSelectionModel().selectedIndexProperty().addListener(
                (obs, old, now) -> savePref("edition", now.intValue() == 1 ? "bedrock" : "java"));

        pingButton = new Button(Strings.t("ping.button"));
        pingButton.getStyleClass().add("filled-button");
        pingButton.setMinWidth(96);
        pingButton.setOnAction(event -> ping());

        HBox row = new HBox(10, addressField, editionBox, pingButton);
        row.setAlignment(Pos.CENTER_LEFT);

        Label proxyLabel = new Label(Strings.t("proxy.label"));
        proxyLabel.getStyleClass().add("field-label");
        proxyField = new TextField(prefs.get("proxy", ""));
        proxyField.setPromptText(Strings.t("proxy.hint"));
        proxyField.getStyleClass().add("proxy-field");
        HBox.setHgrow(proxyField, Priority.ALWAYS);
        proxyField.setOnAction(event -> ping());
        HBox proxyRow = new HBox(10, proxyLabel, proxyField);
        proxyRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(12, row, proxyRow);
        card.getStyleClass().add("form-card");
        return card;
    }

    private Label footer() {
        Label label = new Label(Strings.t("footer"));
        label.getStyleClass().add("footer");
        return label;
    }

    /* ------------------------------------------------------------ 查询流程 */

    private void ping() {
        if (busy) {
            return;
        }
        String address = addressField.getText() == null ? "" : addressField.getText().trim();
        if (address.isEmpty()) {
            showError(Strings.t("input.empty"), null);
            addressField.requestFocus();
            return;
        }
        busy = true;
        setFormDisabled(true);
        showLoading(address);
        savePref("address", address);

        String proxy = proxyField.getText() == null ? "" : proxyField.getText().trim();
        savePref("proxy", proxy);
        if (proxy.isEmpty()) {
            String env = System.getenv("MC_MOTD_PROXY");
            proxy = env == null ? "" : env.trim();
        }
        final String proxyValue = proxy;
        final boolean bedrock = editionBox.getSelectionModel().getSelectedIndex() == 1;
        final String lang = Strings.isEnglish() ? "en" : "zh";

        new Thread(() -> {
            try {
                Slp.Ping ping = bedrock
                        ? Bedrock.ping(address, TIMEOUT_MS, lang)
                        : Slp.ping(address, TIMEOUT_MS, proxyValue.isEmpty() ? null : proxyValue);
                Info info = Info.of(ping);
                remember(address);
                Platform.runLater(() -> {
                    busy = false;
                    setFormDisabled(false);
                    lastInfo = info;
                    previewErrorTitle = null;
                    previewErrorDetail = null;
                    showResult(info);
                });
            } catch (Exception error) {
                String detail = error.getMessage() == null ? error.toString() : error.getMessage();
                Platform.runLater(() -> {
                    busy = false;
                    setFormDisabled(false);
                    // 记下错误态，切主题/语言重画时不能把上一次的旧结果又画出来
                    lastInfo = null;
                    previewErrorTitle = Strings.f("error.title", address);
                    previewErrorDetail = detail;
                    showError(previewErrorTitle, previewErrorDetail);
                });
            }
        }, "mc-motd-ping").start();
    }

    private void setFormDisabled(boolean disabled) {
        addressField.setDisable(disabled);
        proxyField.setDisable(disabled);
        editionBox.setDisable(disabled);
        pingButton.setDisable(disabled);
    }

    /* ------------------------------------------------------------ 输出状态 */

    private void showEmpty() {
        Label title = new Label(Strings.t("empty.title"));
        title.getStyleClass().add("empty-title");
        title.setWrapText(true);
        Label subtitle = new Label(Strings.t("empty.sub"));
        subtitle.getStyleClass().add("empty-sub");
        subtitle.setWrapText(true);

        HBox examples = new HBox(8);
        examples.setAlignment(Pos.CENTER);
        for (String example : EXAMPLES) {
            Button chip = new Button(example);
            chip.getStyleClass().add("chip");
            chip.setOnAction(event -> {
                addressField.setText(example);
                ping();
            });
            examples.getChildren().add(chip);
        }

        VBox card = new VBox(10, title, subtitle, examples);
        card.getStyleClass().add("empty-card");
        card.setAlignment(Pos.CENTER);
        outputBox.getChildren().setAll(card);
    }

    private void showLoading(String address) {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(34, 34);
        Label label = new Label(Strings.f("loading", address));
        label.getStyleClass().add("state-title");
        VBox card = new VBox(14, spinner, label);
        card.getStyleClass().add("state-card");
        card.setAlignment(Pos.CENTER);
        outputBox.getChildren().setAll(card);
    }

    private void showError(String title, String detail) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("error-title");
        titleLabel.setWrapText(true);
        VBox card = new VBox(8, titleLabel);
        card.getStyleClass().add("error-card");
        if (detail != null && !detail.isBlank()) {
            Label detailLabel = new Label(detail);
            detailLabel.getStyleClass().add("error-detail");
            detailLabel.setWrapText(true);
            card.getChildren().add(detailLabel);
            for (String key : new String[]{"error.hint1", "error.hint2", "error.hint3"}) {
                Label hint = new Label(Strings.t(key));
                hint.getStyleClass().add("hint");
                hint.setWrapText(true);
                card.getChildren().add(hint);
            }
        }
        outputBox.getChildren().setAll(card);
    }

    private void showResult(Info info) {
        VBox box = new VBox(16);
        box.getStyleClass().add("result");
        box.getChildren().add(serverCard(info));
        box.getChildren().add(factsCard(info));
        if (!info.sample.isEmpty()) {
            box.getChildren().add(playersCard(info));
        }
        box.getChildren().add(rawCard(info));
        outputBox.getChildren().setAll(box);
    }

    /* ------------------------------------------------------------ 结果组件 */

    private HBox serverCard(Info info) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add("motd-flow");
        fillMotd(flow, info.motd);

        Label subtitle = new Label(subLine(info));
        subtitle.getStyleClass().add("server-sub");
        subtitle.setWrapText(true);

        VBox middle = new VBox(6, flow, subtitle);
        HBox.setHgrow(middle, Priority.ALWAYS);
        middle.setMaxWidth(Double.MAX_VALUE);

        Label latency = new Label(Math.round(info.latencyMs) + " ms");
        latency.getStyleClass().add("latency-label");
        VBox right = new VBox(4, latency, bars(info.latencyMs));
        right.setAlignment(Pos.CENTER_RIGHT);

        HBox card = new HBox(16, favicon(info), middle, right);
        card.getStyleClass().add("server-card");
        card.setAlignment(Pos.CENTER_LEFT);
        return card;
    }

    private Node favicon(Info info) {
        StackPane pane = new StackPane();
        pane.getStyleClass().add("server-icon");
        pane.setMinSize(64, 64);
        pane.setPrefSize(64, 64);
        pane.setMaxSize(64, 64);
        if (info.faviconPng != null && info.faviconPng.length > 0) {
            try {
                Image image = new Image(new ByteArrayInputStream(info.faviconPng), 56, 56, true, false);
                ImageView view = new ImageView(image);
                view.setSmooth(false);
                pane.getChildren().add(view);
                return pane;
            } catch (Exception ignored) {
                // 用像素草方块兜底
            }
        }
        pane.getChildren().add(logo(56));
        return pane;
    }

    private void fillMotd(TextFlow flow, Motd.Parsed parsed) {
        Color fallback = Color.web(dark ? "#e2e3d8" : "#1a1c18");
        if (parsed == null) {
            Text text = new Text(Strings.t("motd.empty"));
            text.setFill(fallback);
            text.setFont(Font.font(fontFamily(), 15));
            flow.getChildren().add(text);
            return;
        }
        boolean any = false;
        if (parsed.lines != null) {
            for (int i = 0; i < parsed.lines.size(); i++) {
                if (i > 0) {
                    flow.getChildren().add(new Text("\n"));
                }
                for (Motd.Seg seg : parsed.lines.get(i)) {
                    flow.getChildren().add(segmentText(seg, fallback));
                    any = true;
                }
            }
        }
        if (!any) {
            String plain = parsed.plain == null || parsed.plain.isBlank() ? Strings.t("motd.empty") : parsed.plain;
            Text text = new Text(plain);
            text.setFill(fallback);
            text.setFont(Font.font(fontFamily(), 15));
            flow.getChildren().add(text);
        }
    }

    private Text segmentText(Motd.Seg seg, Color fallback) {
        Text text = new Text(seg.text);
        text.setFont(Font.font(fontFamily(),
                seg.bold ? FontWeight.BOLD : FontWeight.NORMAL,
                seg.italic ? FontPosture.ITALIC : FontPosture.REGULAR, 15));
        java.awt.Color awt = seg.color;
        Color fill = awt == null ? fallback : Color.rgb(awt.getRed(), awt.getGreen(), awt.getBlue());
        if (!dark) {
            fill = readableOnLight(fill);
        }
        text.setFill(fill);
        if (seg.underlined) {
            text.setUnderline(true);
        }
        if (seg.strikethrough) {
            text.setStrikethrough(true);
        }
        return text;
    }

    /** Minecraft 的高亮色（白、亮绿、亮黄……）在浅色背景上看不清，压低亮度 */
    private static Color readableOnLight(Color color) {
        double luminance = 0.2126 * color.getRed() + 0.7152 * color.getGreen() + 0.0722 * color.getBlue();
        if (luminance <= 0.62) {
            return color;
        }
        return Color.hsb(color.getHue(), color.getSaturation(), Math.min(color.getBrightness(), 0.55));
    }

    /** 版本名缺失时按当前语言回落到「未知版本 / Unknown version」 */
    private static String versionText(Info info) {
        String name = info.versionName == null ? "" : info.versionName.trim();
        if (name.isEmpty()) {
            name = Strings.t("version.unknown");
        }
        return info.protocol != null ? name + " (" + info.protocol + ")" : name;
    }

    private String subLine(Info info) {
        StringBuilder builder = new StringBuilder();
        builder.append(info.online).append('/').append(info.max).append(" · ").append(versionText(info));
        if (info.srv != null) {
            builder.append(" · SRV");
        }
        if (info.legacy) {
            builder.append(" · LEGACY");
        }
        if ("bedrock".equals(info.edition)) {
            builder.append(" · BEDROCK");
        }
        return builder.toString();
    }

    private HBox bars(double latency) {
        Color color = latency < 120 ? Color.web("#4caf50")
                : latency < 300 ? Color.web("#e0b84a") : Color.web("#e57373");
        HBox box = new HBox(3);
        box.setAlignment(Pos.BOTTOM_RIGHT);
        for (int i = 0; i < 5; i++) {
            Region bar = new Region();
            double height = 5 + i * 3;
            bar.setMinSize(4, height);
            bar.setPrefSize(4, height);
            bar.setMaxSize(4, height);
            bar.setStyle("-fx-background-color: " + toHex(color) + "; -fx-background-radius: 2;");
            box.getChildren().add(bar);
        }
        return box;
    }

    private VBox factsCard(Info info) {
        Label title = new Label(Strings.t("facts.title"));
        title.getStyleClass().add("section-title");
        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(8);
        int row = 0;
        row = addFact(grid, row, Strings.t("fact.target"), info.target());
        row = addFact(grid, row, Strings.t("fact.version"), versionText(info));
        row = addFact(grid, row, Strings.t("fact.players"), info.online + " / " + info.max);
        row = addFact(grid, row, Strings.t("fact.latency"), Math.round(info.latencyMs) + " ms");
        row = addFact(grid, row, Strings.t("fact.protocol"),
                info.protocol != null ? String.valueOf(info.protocol) : Strings.t("fact.na"));
        if (info.srv != null) {
            row = addFact(grid, row, Strings.t("facts.srv"), info.srv.host + ":" + info.srv.port);
        }
        row = addFact(grid, row, Strings.t("fact.secure"),
                "java".equals(info.edition) ? Strings.t(info.secureChat ? "secure.on" : "secure.off") : Strings.t("fact.na"));

        VBox card = new VBox(12, title, grid);
        card.getStyleClass().add("facts-card");
        return card;
    }

    private int addFact(GridPane grid, int row, String label, String value) {
        Label key = new Label(label);
        key.getStyleClass().add("fact-label");
        Label val = new Label(value);
        val.getStyleClass().add("fact-value");
        val.setWrapText(true);
        grid.add(key, 0, row);
        grid.add(val, 1, row);
        return row + 1;
    }

    private VBox playersCard(Info info) {
        Label title = new Label(Strings.t("players.title") + " · " + Strings.f("players.count", info.sample.size()));
        title.getStyleClass().add("section-title");
        FlowPane chips = new FlowPane(8, 8);
        for (String name : info.sample) {
            Label chip = new Label(name);
            chip.getStyleClass().add("player-chip");
            chips.getChildren().add(chip);
        }
        VBox card = new VBox(10, title, chips);
        card.getStyleClass().add("players-card");
        return card;
    }

    private VBox rawCard(Info info) {
        Label title = new Label(Strings.t("raw.title"));
        title.getStyleClass().add("section-title");
        Button copy = new Button(Strings.t("raw.copy"));
        copy.getStyleClass().add("text-button");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox head = new HBox(8, title, spacer, copy);
        head.setAlignment(Pos.CENTER_LEFT);

        TextArea area = new TextArea(prettyJson(info.rawJson));
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(12);
        area.getStyleClass().add("raw-area");

        copy.setOnAction(event -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(area.getText());
            Clipboard.getSystemClipboard().setContent(content);
            copy.setText(Strings.t("raw.copied"));
            PauseTransition pause = new PauseTransition(Duration.millis(1200));
            pause.setOnFinished(done -> copy.setText(Strings.t("raw.copy")));
            pause.play();
        });

        VBox card = new VBox(10, head, area);
        card.getStyleClass().add("raw-card");
        return card;
    }

    /* ------------------------------------------------------------ 历史记录 */

    private List<String> history() {
        List<String> items = new ArrayList<>();
        for (String line : prefs.get("history", "").split("\n")) {
            String value = line.trim();
            if (!value.isEmpty()) {
                items.add(value);
            }
        }
        return items;
    }

    private void remember(String address) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        set.add(address);
        for (String item : history()) {
            set.add(item);
        }
        List<String> items = new ArrayList<>(set);
        if (items.size() > HISTORY_MAX) {
            items = items.subList(0, HISTORY_MAX);
        }
        savePref("history", String.join("\n", items));
        Platform.runLater(this::updateHistoryRow);
    }

    private void updateHistoryRow() {
        if (historyRow == null) {
            return;
        }
        historyRow.getChildren().clear();
        List<String> items = history();
        historyRow.setVisible(!items.isEmpty());
        historyRow.setManaged(!items.isEmpty());
        if (items.isEmpty()) {
            return;
        }
        Label label = new Label(Strings.t("history.label"));
        label.getStyleClass().add("history-label");
        historyRow.getChildren().add(label);
        for (String item : items) {
            Button chip = new Button(item);
            chip.getStyleClass().add("chip");
            chip.setOnAction(event -> {
                addressField.setText(item);
                ping();
            });
            historyRow.getChildren().add(chip);
        }
    }

    /* ------------------------------------------------------------ 主题 / 图标 */

    private boolean resolveDark() {
        String forced = System.getProperty("mcmotd.dark");
        if (forced != null && !forced.isBlank()) {
            return !"false".equalsIgnoreCase(forced.trim());
        }
        return switch (themeMode) {
            case "light", "latte" -> false;
            case "dark", "mocha" -> true;
            default -> systemDark();
        };
    }

    private static boolean systemDark() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            if (os.contains("win")) {
                String output = run("reg", "query",
                        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
                        "/v", "AppsUseLightTheme");
                if (output.isBlank()) {
                    return false; // 查询失败时按浅色处理，别误判成深色
                }
                return !output.contains("0x1");
            }
            if (os.contains("mac")) {
                String output = run("defaults", "read", "-g", "AppleInterfaceStyle");
                return output.trim().equalsIgnoreCase("Dark");
            }
            String scheme = run("gsettings", "get", "org.gnome.desktop.interface", "color-scheme");
            if (scheme.toLowerCase(Locale.ROOT).contains("dark")) {
                return true;
            }
            String gtkTheme = System.getenv("GTK_THEME");
            if (gtkTheme != null && gtkTheme.toLowerCase(Locale.ROOT).contains("dark")) {
                return true;
            }
            String theme = run("gsettings", "get", "org.gnome.desktop.interface", "gtk-theme");
            return theme.toLowerCase(Locale.ROOT).contains("dark");
        } catch (Exception e) {
            return false;
        }
    }

    private static String run(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!process.waitFor(1200, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                return "";
            }
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && output.length() < 4000) {
                    output.append(line).append('\n');
                }
            }
            return output.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private String themeIcon() {
        return switch (themeMode) {
            case "light" -> "☀";
            case "dark" -> "☾";
            case "mocha" -> "◒";
            case "latte" -> "◓";
            default -> "◐";
        };
    }

    private String themeTooltip() {
        return switch (themeMode) {
            case "light" -> Strings.t("theme.light");
            case "dark" -> Strings.t("theme.dark");
            case "mocha" -> Strings.t("theme.mocha");
            case "latte" -> Strings.t("theme.latte");
            default -> Strings.t("theme.system");
        };
    }

    private static Button textButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("text-button");
        return button;
    }

    private static Canvas logo(double size) {
        Canvas canvas = new Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();
        double cell = size / 8.0;
        String[] grass = {"#55ff55", "#3fb83f", "#4fe24f"};
        String[] dirt = {"#6b4b2f", "#543a24", "#7a5636"};
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                g.setFill(Color.web(y <= 2 ? grass[(x * 7 + y * 3) % 3] : dirt[(x + y * 5) % 3]));
                g.fillRect(x * cell, y * cell, cell, cell);
            }
        }
        g.setStroke(Color.web("#000000", 0.25));
        g.strokeRect(0.5, 0.5, size - 1, size - 1);
        return canvas;
    }

    static String fontFamily() {
        if (cachedFont != null) {
            return cachedFont;
        }
        String[] candidates = {"Noto Sans CJK SC", "Source Han Sans SC", "Microsoft YaHei UI",
                "PingFang SC", "Segoe UI", "Noto Sans", "SansSerif"};
        List<String> families = Font.getFamilies();
        for (String candidate : candidates) {
            if (families.contains(candidate)) {
                cachedFont = candidate;
                return cachedFont;
            }
        }
        cachedFont = "SansSerif";
        return cachedFont;
    }

    /* ------------------------------------------------------------ 小工具 */

    private static String toHex(Color color) {
        return String.format("#%02x%02x%02x",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    private static String prettyJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length() + 64);
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < raw.length()) {
                    out.append(raw.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    out.append(c);
                }
                case '{', '[' -> {
                    out.append(c).append('\n').append("  ".repeat(++depth));
                }
                case '}', ']' -> {
                    out.append('\n').append("  ".repeat(Math.max(0, --depth))).append(c);
                }
                case ',' -> out.append(",\n").append("  ".repeat(depth));
                case ':' -> out.append(": ");
                default -> {
                    if (!Character.isWhitespace(c)) {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
