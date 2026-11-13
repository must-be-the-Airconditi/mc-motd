package mcmotd;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.prefs.Preferences;

/**
 * 主界面：服务器卡片 + 详情 + 玩家 + 原始 JSON。
 * 纯 Swing，无第三方依赖。
 */
public final class Ui extends JPanel {

    private static final int WINDOW_W = 1040;
    private static final int WINDOW_H = 940;
    private static final int TIMEOUT_MS = 8000;

    private final JTextField addressField = new PlaceholderField("play.example.com:25565");
    private final JTextField proxyField = new PlaceholderField("http://127.0.0.1:31181");
    private final FlatButton pingButton = new FlatButton("PING", true);
    private final JPanel output = new ScrollablePanel();
    private final JPanel historyRow = new JPanel();
    private final Runnable onLangToggle;
    private final JScrollPane scroll;
    private final Preferences prefs = Preferences.userNodeForPackage(Ui.class);
    private final List<String> history = new ArrayList<>();

    public Ui() {
        this(null);
    }

    public Ui(Runnable onLangToggle) {
        super(new BorderLayout());
        this.onLangToggle = onLangToggle;
        setBackground(Theme.BG);
        setBorder(new EmptyBorder(26, 30, 18, 30));

        output.setOpaque(false);
        output.setLayout(new BoxLayout(output, BoxLayout.Y_AXIS));
        output.setBorder(new EmptyBorder(0, 0, 0, 0));

        scroll = new JScrollPane(output);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

        add(buildHeader(), BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        loadPreferences();
        showEmpty();
        wireEvents();
    }

    /* ------------------------------------------------------------ 顶部区域 */

    private JPanel buildHeader() {
        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

        JPanel brand = new JPanel(new BorderLayout(14, 0));
        brand.setOpaque(false);
        brand.add(new LogoView(44), BorderLayout.WEST);

        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(Strings.t("app.title"));
        title.setFont(Theme.sans(23, Font.BOLD));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel subtitle = new JLabel("MINECRAFT: JAVA EDITION  ·  SERVER LIST PING");
        subtitle.setFont(Theme.mono(10, Font.PLAIN));
        subtitle.setForeground(Theme.MUTED_2);
        subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        titles.add(title);
        titles.add(Box.createVerticalStrut(4));
        titles.add(subtitle);
        brand.add(titles, BorderLayout.CENTER);

        FlatButton langButton = new FlatButton(Strings.isEnglish() ? "中文" : "EN", false);
        langButton.setFont(Theme.sans(10, Font.BOLD));
        langButton.addActionListener(e -> {
            Strings.toggle();
            prefs.put("lang", Strings.isEnglish() ? "en" : "zh");
            if (onLangToggle != null) {
                onLangToggle.run();
            }
        });
        brand.add(langButton, BorderLayout.EAST);

        JPanel form = new JPanel(new BorderLayout(10, 0));
        form.setOpaque(false);
        form.setAlignmentX(Component.CENTER_ALIGNMENT);
        form.setMaximumSize(new Dimension(Integer.MAX_VALUE, 54));

        addressField.setFont(Theme.mono(14, Font.PLAIN));
        addressField.setForeground(Theme.TEXT);
        addressField.setBackground(Theme.INPUT);
        addressField.setCaretColor(Theme.GREEN);
        addressField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                new EmptyBorder(10, 14, 10, 14)));
        addressField.setPreferredSize(new Dimension(400, 52));

        pingButton.setPreferredSize(new Dimension(118, 52));
        pingButton.setFont(Theme.mono(12, Font.BOLD));

        form.add(addressField, BorderLayout.CENTER);
        form.add(pingButton, BorderLayout.EAST);

        JPanel advanced = new JPanel(new BorderLayout(10, 0));
        advanced.setOpaque(false);
        advanced.setAlignmentX(Component.CENTER_ALIGNMENT);
        advanced.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel proxyLabel = new JLabel(Strings.t("proxy.label"));
        proxyLabel.setFont(Theme.sans(11, Font.PLAIN));
        proxyLabel.setForeground(Theme.MUTED_2);
        proxyLabel.setPreferredSize(new Dimension(32, 26));

        proxyField.setFont(Theme.mono(11, Font.PLAIN));
        proxyField.setForeground(Theme.MUTED);
        proxyField.setBackground(Theme.PANEL_SOFT);
        proxyField.setCaretColor(Theme.GREEN);
        proxyField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Theme.LINE),
                new EmptyBorder(5, 10, 5, 10)));
        proxyField.setPreferredSize(new Dimension(300, 28));

        advanced.add(proxyLabel, BorderLayout.WEST);
        advanced.add(proxyField, BorderLayout.CENTER);
        JLabel proxyHint = new JLabel(Strings.t("proxy.hint"));
        proxyHint.setFont(Theme.mono(10, Font.PLAIN));
        proxyHint.setForeground(Theme.MUTED_2);
        proxyHint.setPreferredSize(new Dimension(130, 26));
        advanced.add(proxyHint, BorderLayout.EAST);

        historyRow.setOpaque(false);
        historyRow.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        historyRow.setAlignmentX(Component.CENTER_ALIGNMENT);

        header.add(brand);
        header.add(Box.createVerticalStrut(20));
        header.add(form);
        header.add(Box.createVerticalStrut(8));
        header.add(advanced);
        header.add(Box.createVerticalStrut(10));
        header.add(historyRow);
        header.add(Box.createVerticalStrut(14));
        return header;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(new EmptyBorder(12, 0, 0, 0));
        JLabel label = new JLabel(Strings.t("footer"), SwingConstants.CENTER);
        label.setFont(Theme.sans(11, Font.PLAIN));
        label.setForeground(Theme.MUTED_2);
        footer.add(label, BorderLayout.CENTER);
        return footer;
    }

    private void wireEvents() {
        pingButton.addActionListener(e -> ping(addressField.getText()));
        addressField.addActionListener(e -> ping(addressField.getText()));
        proxyField.addActionListener(e -> ping(addressField.getText()));
        addressField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                addressField.selectAll();
            }
        });
    }

    /* ------------------------------------------------------------ 查询流程 */

    public void ping(String address) {
        final String target = address == null ? "" : address.trim();
        if (target.isEmpty()) {
            showError("", Strings.t("input.empty"));
            return;
        }
        setBusy(true);
        showLoading(target);

        new SwingWorker<Info, Void>() {
            @Override
            protected Info doInBackground() throws Exception {
                String proxy = proxyField.getText().trim();
                if (proxy.isEmpty()) {
                    proxy = System.getenv("MC_MOTD_PROXY");
                }
                return Info.of(Slp.ping(target, TIMEOUT_MS, proxy));
            }

            @Override
            protected void done() {
                setBusy(false);
                try {
                    Info info = get();
                    addressField.setText(target);
                    addressField.setForeground(Theme.TEXT);
                    showResult(info);
                    remember(target);
                    rememberAddress(target);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    Throwable cause = e.getCause() == null ? e : e.getCause();
                    showError(target, cause.getMessage() == null ? cause.toString() : cause.getMessage());
                }
            }
        }.execute();
    }

    private void setBusy(boolean busy) {
        pingButton.setEnabled(!busy);
        pingButton.setText(busy ? "PING…" : "PING");
        pingButton.repaint();
    }

    /* ------------------------------------------------------------ 各种面板 */

    private void setOutput(JComponent component) {
        output.removeAll();
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
        output.add(component);
        output.add(Box.createVerticalStrut(12));
        output.revalidate();
        output.repaint();
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }

    public void showEmpty() {
        RoundedPanel panel = new RoundedPanel(Theme.PANEL_SOFT, Theme.LINE, true);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(30, 26, 26, 26));
        panel.setPreferredSize(new Dimension(900, 300));

        JLabel title = new JLabel(Strings.t("empty.title"), SwingConstants.CENTER);
        title.setFont(Theme.sans(15, Font.BOLD));
        title.setForeground(Theme.TEXT);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel(Strings.t("empty.sub"), SwingConstants.CENTER);
        sub.setFont(Theme.sans(11, Font.PLAIN));
        sub.setForeground(Theme.MUTED_2);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel examples = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.CENTER, 8, 0));
        examples.setOpaque(false);
        examples.setAlignmentX(Component.CENTER_ALIGNMENT);
        for (String address : new String[]{"play.cubecraft.net", "mc.hypixel.net", "2b2t.org"}) {
            FlatButton chip = new FlatButton(address, false);
            chip.setFont(Theme.sans(11, Font.PLAIN));
            chip.addActionListener(e -> {
                addressField.setText(address);
                ping(address);
            });
            examples.add(chip);
        }

        panel.add(Box.createVerticalGlue());
        panel.add(title);
        panel.add(Box.createVerticalStrut(6));
        panel.add(sub);
        panel.add(Box.createVerticalStrut(18));
        panel.add(examples);
        panel.add(Box.createVerticalGlue());

        setOutput(panel);
    }

    private void showLoading(String address) {
        RoundedPanel panel = new RoundedPanel(Theme.PANEL, Theme.LINE, false);
        panel.setLayout(new BorderLayout(14, 0));
        panel.setBorder(new EmptyBorder(18, 18, 18, 18));
        panel.setPreferredSize(new Dimension(900, 84));

        JLabel label = new JLabel(Strings.f("loading", address));
        label.setFont(Theme.sans(13, Font.PLAIN));
        label.setForeground(Theme.MUTED);

        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        bar.setBorderPainted(false);
        bar.setBackground(Theme.PANEL_SOFT);
        bar.setForeground(Theme.GREEN);
        bar.setPreferredSize(new Dimension(160, 4));

        JPanel right = new JPanel();
        right.setOpaque(false);
        right.setLayout(new BoxLayout(right, BoxLayout.Y_AXIS));
        JLabel hint = new JLabel("PING");
        hint.setFont(Theme.mono(11, Font.BOLD));
        hint.setForeground(Theme.GREEN);
        right.add(hint);
        right.add(Box.createVerticalStrut(4));
        right.add(bar);

        panel.add(label, BorderLayout.CENTER);
        panel.add(right, BorderLayout.EAST);
        setOutput(panel);
    }

    public void showError(String address, String message) {
        RoundedPanel panel = new RoundedPanel(new Color(0x1A0F0F), new Color(255, 85, 85, 90), false);
        panel.setLayout(new BorderLayout(16, 0));
        panel.setBorder(new EmptyBorder(16, 18, 16, 18));
        panel.setPreferredSize(new Dimension(900, 200));

        JLabel icon = new JLabel("!", SwingConstants.CENTER);
        icon.setFont(Theme.mono(24, Font.BOLD));
        icon.setForeground(Theme.RED);
        icon.setPreferredSize(new Dimension(64, 64));
        icon.setOpaque(true);
        icon.setBackground(new Color(255, 85, 85, 22));
        icon.setBorder(BorderFactory.createLineBorder(new Color(255, 85, 85, 70)));

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        String titleText = address.isEmpty() ? Strings.t("error.empty.title") : Strings.f("error.title", address);
        JLabel title = new JLabel(titleText);
        title.setFont(Theme.sans(14, Font.BOLD));
        title.setForeground(new Color(0xFFD7D7));

        JLabel detail = new JLabel("<html><div style='width:620px'>" + escape(message) + "</div></html>");
        detail.setFont(Theme.sans(11, Font.PLAIN));
        detail.setForeground(new Color(0xC98D8D));

        JLabel hints = new JLabel("<html><div style='width:620px; color:#5B7365'>"
                + Strings.t("error.hint1") + "<br>"
                + Strings.t("error.hint2") + "<br>"
                + Strings.t("error.hint3")
                + "</div></html>");
        hints.setFont(Theme.sans(11, Font.PLAIN));

        body.add(title);
        body.add(Box.createVerticalStrut(6));
        body.add(detail);
        body.add(Box.createVerticalStrut(10));
        body.add(hints);

        panel.add(icon, BorderLayout.WEST);
        panel.add(body, BorderLayout.CENTER);
        setOutput(panel);
    }

    public void showResult(Info info) {
        JPanel container = new JPanel();
        container.setOpaque(false);
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));

        JComponent card = buildServerCard(info);
        stretch(card);
        container.add(card);
        container.add(Box.createVerticalStrut(12));

        JComponent facts = buildFacts(info);
        stretch(facts);
        container.add(facts);

        if (!info.sample.isEmpty()) {
            container.add(Box.createVerticalStrut(12));
            JComponent players = buildPlayers(info);
            stretch(players);
            container.add(players);
        }

        container.add(Box.createVerticalStrut(12));
        JComponent raw = buildRaw(info);
        stretch(raw);
        container.add(raw);

        setOutput(container);
    }

    private JComponent buildServerCard(Info info) {
        RoundedPanel card = new RoundedPanel(Theme.PANEL, Theme.LINE, false);
        card.setLayout(new BorderLayout(16, 0));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        card.add(new FaviconView(info.faviconPng, 62), BorderLayout.WEST);

        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));

        JLabel motd = new JLabel();
        motd.setFont(Theme.sans(15, Font.PLAIN));
        motd.setText(buildMotdHtml(info));
        motd.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(motd);
        body.add(Box.createVerticalStrut(6));

        JLabel sub = new JLabel();
        sub.setFont(Theme.sans(11, Font.PLAIN));
        sub.setText(buildSubHtml(info));
        sub.setAlignmentX(Component.LEFT_ALIGNMENT);
        body.add(sub);

        card.add(body, BorderLayout.CENTER);
        card.add(new LatencyView(info.latencyMs), BorderLayout.EAST);
        return card;
    }

    private String buildMotdHtml(Info info) {
        List<List<Motd.Seg>> lines = info.motd == null ? new ArrayList<>() : info.motd.lines;
        StringBuilder html = new StringBuilder("<html><div style='width:640px'>");
        int rendered = 0;
        for (List<Motd.Seg> line : lines) {
            if (rendered >= 2) {
                break;
            }
            if (rendered > 0) {
                html.append("<br>");
            }
            if (line.isEmpty()) {
                html.append(" ");
            }
            for (Motd.Seg seg : line) {
                html.append("<span style='color:").append(hex(seg.color));
                if (seg.bold) {
                    html.append(";font-weight:bold");
                }
                if (seg.italic) {
                    html.append(";font-style:italic");
                }
                if (seg.underlined && seg.strikethrough) {
                    html.append(";text-decoration:underline line-through");
                } else if (seg.underlined) {
                    html.append(";text-decoration:underline");
                } else if (seg.strikethrough) {
                    html.append(";text-decoration:line-through");
                }
                html.append("'>").append(escape(seg.text)).append("</span>");
            }
            rendered++;
        }
        if (rendered == 0) {
            html.append("<span style='color:#5B7365;font-style:italic'>")
                    .append(escape(Strings.t("motd.empty")))
                    .append("</span>");
        }
        html.append("</div></html>");
        return html.toString();
    }

    private String buildSubHtml(Info info) {
        StringBuilder html = new StringBuilder("<html>");
        html.append("<span style='color:#C9E6D2'>")
                .append(String.format("%,d", info.online)).append(" / ").append(String.format("%,d", info.max))
                .append("</span>");
        html.append("<span style='color:#5B7365'>  ·  </span>");
        html.append("<span style='color:#7E998A'>").append(escape(info.versionName)).append("</span>");
        if (info.srv != null) {
            html.append("<span style='color:#5B7365'>  ·  </span>")
                    .append("<span style='color:#55FF55'>SRV</span>");
        }
        if (info.legacy) {
            html.append("<span style='color:#5B7365'>  ·  </span>")
                    .append("<span style='color:#FFFF55'>LEGACY</span>");
        }
        html.append("</html>");
        return html.toString();
    }

    private JComponent buildFacts(Info info) {
        RoundedPanel panel = new RoundedPanel(Theme.PANEL_SOFT, Theme.LINE, false);
        panel.setLayout(new BorderLayout());

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(9, 16, 9, 16));
        JLabel title = new JLabel(Strings.t("facts.title"));
        title.setFont(Theme.sans(10, Font.BOLD));
        title.setForeground(Theme.MUTED);
        head.add(title, BorderLayout.WEST);

        JLabel badge = new JLabel(Strings.t("facts.srv") + (info.srv != null ? " ✓" : ""));
        badge.setFont(Theme.sans(10, Font.PLAIN));
        badge.setForeground(info.srv != null ? Theme.GREEN : Theme.MUTED_2);
        head.add(badge, BorderLayout.EAST);

        JPanel grid = new JPanel(new GridLayout(2, 3, 1, 1));
        grid.setBackground(Theme.withAlpha(Theme.GREEN, 28));

        String target = info.target() + (info.srv != null ? "  ·  SRV" : "");
        grid.add(fact(Strings.t("fact.target"), target));
        grid.add(fact(Strings.t("fact.version"), info.versionLine()));
        grid.add(fact(Strings.t("fact.players"), String.format("%,d / %,d", info.online, info.max)));
        grid.add(fact(Strings.t("fact.latency"), String.format("%.1f ms", info.latencyMs)));
        grid.add(fact(Strings.t("fact.protocol"),
                info.legacy ? Strings.t("ping.legacy") : Strings.t("ping.modern")));
        grid.add(fact(Strings.t("fact.secure"),
                info.secureChat ? Strings.t("secure.on") : Strings.t("secure.off")));

        panel.add(head, BorderLayout.NORTH);
        panel.add(grid, BorderLayout.CENTER);
        return panel;
    }

    private JComponent fact(String label, String value) {
        JPanel cell = new JPanel();
        cell.setOpaque(true);
        cell.setBackground(Theme.PANEL_SOFT);
        cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
        cell.setBorder(new EmptyBorder(10, 14, 12, 14));

        JLabel key = new JLabel(label);
        key.setFont(Theme.sans(9, Font.PLAIN));
        key.setForeground(Theme.MUTED_2);
        key.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel val = new JLabel(value);
        val.setFont(Theme.sans(12, Font.PLAIN));
        val.setForeground(new Color(0xD3EAD9));
        val.setAlignmentX(Component.LEFT_ALIGNMENT);

        cell.add(key);
        cell.add(Box.createVerticalStrut(3));
        cell.add(val);
        return cell;
    }

    private JComponent buildPlayers(Info info) {
        RoundedPanel panel = new RoundedPanel(Theme.PANEL_SOFT, Theme.LINE, false);
        panel.setLayout(new BorderLayout());

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(9, 16, 9, 16));
        JLabel title = new JLabel(Strings.t("players.title"));
        title.setFont(Theme.sans(10, Font.BOLD));
        title.setForeground(Theme.MUTED);
        JLabel count = new JLabel(Strings.f("players.count", info.sample.size()));
        count.setFont(Theme.sans(10, Font.PLAIN));
        count.setForeground(Theme.GREEN);
        head.add(title, BorderLayout.WEST);
        head.add(count, BorderLayout.EAST);

        JPanel chips = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 6));
        chips.setOpaque(false);
        chips.setBorder(new EmptyBorder(8, 12, 10, 12));
        chips.setPreferredSize(new Dimension(100, 66));
        int shown = Math.min(info.sample.size(), 10);
        for (int i = 0; i < shown; i++) {
            String name = info.sample.get(i);
            if (name.length() > 20) {
                name = name.substring(0, 19) + "…";
            }
            JLabel chip = new JLabel("● " + name);
            chip.setFont(Theme.sans(11, Font.PLAIN));
            chip.setForeground(Theme.MUTED);
            chip.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Theme.LINE),
                    new EmptyBorder(2, 8, 2, 8)));
            chips.add(chip);
        }
        panel.add(head, BorderLayout.NORTH);
        panel.add(chips, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildRaw(Info info) {
        RoundedPanel panel = new RoundedPanel(Theme.PANEL_SOFT, Theme.LINE, false);
        panel.setLayout(new BorderLayout());

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.setBorder(new EmptyBorder(6, 16, 6, 12));

        JCheckBox toggle = new JCheckBox(Strings.t("raw.title"));
        toggle.setFont(Theme.sans(10, Font.BOLD));
        toggle.setForeground(Theme.MUTED);
        toggle.setOpaque(false);
        toggle.setFocusPainted(false);

        FlatButton copy = new FlatButton(Strings.t("raw.copy"), false);
        copy.setFont(Theme.sans(10, Font.PLAIN));
        copy.setPreferredSize(new Dimension(96, 24));
        copy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(pretty(info.rawJson)), null);
            copy.setText(Strings.t("raw.copied"));
        });

        head.add(toggle, BorderLayout.WEST);
        head.add(copy, BorderLayout.EAST);

        JTextArea area = new JTextArea(pretty(info.rawJson));
        area.setFont(Theme.mono(11, Font.PLAIN));
        area.setBackground(new Color(0x050907));
        area.setForeground(new Color(0x9FC4AC));
        area.setCaretColor(Theme.GREEN);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(10, 14, 12, 14));

        JScrollPane areaScroll = new JScrollPane(area);
        areaScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.LINE));
        areaScroll.getViewport().setBackground(new Color(0x050907));
        areaScroll.setPreferredSize(new Dimension(100, 180));
        areaScroll.setVisible(false);

        toggle.addActionListener(e -> {
            areaScroll.setVisible(toggle.isSelected());
            refreshHeights(panel);
        });

        panel.add(head, BorderLayout.NORTH);
        panel.add(areaScroll, BorderLayout.CENTER);
        return panel;
    }

    /* ------------------------------------------------------------ 历史记录 */

    private void remember(String address) {
        history.remove(address);
        history.add(0, address);
        while (history.size() > 6) {
            history.remove(history.size() - 1);
        }
        prefs.put("history", String.join("\n", history));
        renderHistory();
    }

    private void renderHistory() {
        historyRow.removeAll();
        if (!history.isEmpty()) {
            JLabel label = new JLabel(Strings.t("history.label"));
            label.setFont(Theme.sans(10, Font.PLAIN));
            label.setForeground(Theme.MUTED_2);
            historyRow.add(label);

            for (String address : history) {
                FlatButton chip = new FlatButton(address, false);
                chip.setFont(Theme.mono(10, Font.PLAIN));
                chip.addActionListener(e -> {
                    addressField.setText(address);
                    ping(address);
                });
                historyRow.add(chip);
            }
        }
        historyRow.revalidate();
        historyRow.repaint();
    }

    private void loadPreferences() {
        String savedHistory = prefs.get("history", "");
        if (!savedHistory.isEmpty()) {
            for (String line : savedHistory.split("\n")) {
                if (!line.trim().isEmpty()) {
                    history.add(line.trim());
                }
            }
        }
        proxyField.setText(prefs.get("proxy", ""));
        if (proxyField.getText().isEmpty()) {
            String env = System.getenv("MC_MOTD_PROXY");
            if (env != null) {
                proxyField.setText(env);
            }
        }
        String last = prefs.get("address", "");
        if (!last.isEmpty()) {
            addressField.setText(last);
        }
        renderHistory();
    }

    /** 记住地址（查询成功后再存） */
    public void rememberAddress(String address) {
        prefs.put("address", address);
        prefs.put("proxy", proxyField.getText().trim());
    }

    private static void stretch(JComponent component) {
        component.setAlignmentX(Component.LEFT_ALIGNMENT);
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height));
    }

    private void refreshHeights(java.awt.Component component) {
        java.awt.Component current = component;
        while (current != null && current != output) {
            if (current instanceof JComponent) {
                JComponent jc = (JComponent) current;
                jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height));
            }
            current = current.getParent();
        }
        output.revalidate();
        output.repaint();
    }

    /* ------------------------------------------------------------ 截图辅助 */

    /** 无头渲染一张界面截图（用于仓库里的预览图） */
    public static void renderShot(String address, File out, int width, int height) throws Exception {
        System.setProperty("java.awt.headless", "true");
        Ui ui = new Ui();
        ui.setSize(width, height);
        if (address != null && !address.isEmpty()) {
            try {
                ui.showResult(Info.of(Slp.ping(address, TIMEOUT_MS, null)));
            } catch (Exception e) {
                ui.showError(address, e.getMessage());
            }
        }
        layoutTree(ui);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Theme.BG);
        g.fillRect(0, 0, width, height);
        ui.printAll(g);
        g.dispose();
        ImageIO.write(image, "png", out);
        System.out.println("已写入 " + out.getAbsolutePath());
    }

    private static void layoutTree(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    /* ------------------------------------------------------------ 小工具 */

    static String escape(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    static String hex(Color color) {
        if (color == null) {
            color = Theme.TEXT;
        }
        return String.format("#%02X%02X%02X", color.getRed(), color.getGreen(), color.getBlue());
    }

    static String pretty(String json) {
        StringBuilder sb = new StringBuilder(json.length() + 64);
        int indent = 0;
        boolean inString = false;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                sb.append(c);
                if (c == '\\' && i + 1 < json.length()) {
                    sb.append(json.charAt(++i));
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            switch (c) {
                case '"':
                    inString = true;
                    sb.append(c);
                    break;
                case '{':
                case '[':
                    sb.append(c).append('\n');
                    indent++;
                    sb.append("  ".repeat(indent));
                    break;
                case '}':
                case ']':
                    sb.append('\n');
                    indent = Math.max(0, indent - 1);
                    sb.append("  ".repeat(indent)).append(c);
                    break;
                case ',':
                    sb.append(c).append('\n').append("  ".repeat(indent));
                    break;
                case ':':
                    sb.append(": ");
                    break;
                default:
                    sb.append(c);
            }
        }
        return sb.toString();
    }

    /** 让内容宽度跟随视口，避免横向被裁掉 */
    static final class ScrollablePanel extends JPanel implements Scrollable {
        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 120;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /* ------------------------------------------------------------ 自定义组件 */

    /** 圆角面板 */
    static final class RoundedPanel extends JPanel {
        private final Color fill;
        private final Color border;
        private final boolean dashed;

        RoundedPanel(Color fill, Color border, boolean dashed) {
            this.fill = fill;
            this.border = border;
            this.dashed = dashed;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth() - 1;
            int h = getHeight() - 1;
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, w, h, 12, 12);
            g2.setColor(border);
            if (dashed) {
                g2.setStroke(new BasicStroke(1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                        10f, new float[]{6f, 6f}, 0f));
            }
            g2.drawRoundRect(0, 0, w, h, 12, 12);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 扁平按钮 */
    static final class FlatButton extends JButton {
        private final boolean primary;
        private boolean hover;
        private boolean pressed;

        FlatButton(String text, boolean primary) {
            super(text);
            this.primary = primary;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setForeground(primary ? new Color(0xEAFFEA) : Theme.MUTED);
            setFont(primary ? Theme.mono(12, Font.BOLD) : Theme.mono(10, Font.PLAIN));
            setBorder(new EmptyBorder(primary ? 8 : 4, primary ? 18 : 9, primary ? 8 : 4, primary ? 18 : 9));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    hover = true;
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = false;
                    repaint();
                }

                @Override
                public void mousePressed(MouseEvent e) {
                    pressed = true;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    pressed = false;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            if (primary) {
                Color top = pressed ? Theme.GREEN_DARKER : hover ? new Color(0x37A637) : Theme.GREEN_DARK;
                Color bottom = pressed ? new Color(0x144814) : Theme.GREEN_DARKER;
                g2.setPaint(new GradientPaint(0, 0, top, 0, h, bottom));
                g2.fillRoundRect(0, 0, w, h, 8, 8);
                g2.setColor(new Color(85, 255, 85, 140));
                g2.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
            } else {
                g2.setColor(hover ? new Color(85, 255, 85, 22) : new Color(0, 0, 0, 60));
                g2.fillRoundRect(0, 0, w, h, 6, 6);
                g2.setColor(hover ? Theme.withAlpha(Theme.GREEN, 110) : Theme.LINE);
                g2.drawRoundRect(0, 0, w - 1, h - 1, 6, 6);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** 带占位提示的输入框 */
    static final class PlaceholderField extends JTextField {
        private final String placeholder;

        PlaceholderField(String placeholder) {
            this.placeholder = placeholder;
            setOpaque(true);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (getText().isEmpty() && !isFocusOwner()) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(Theme.MUTED_2);
                g2.setFont(getFont());
                g2.drawString(placeholder, getInsets().left, getHeight() / 2 + g2.getFontMetrics().getAscent() / 2 - 2);
                g2.dispose();
            }
        }
    }

    /** 草方块 logo / 无 favicon 时的占位图 */
    static final class LogoView extends JComponent {
        private final int size;

        LogoView(int size) {
            this.size = size;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
            g2.setColor(new Color(0x0B120F));
            g2.fillRect(0, 0, size, size);

            int cell = Math.max(3, size / 8);
            int ox = (size - cell * 8) / 2;
            int oy = (size - cell * 8) / 2;
            Color grass = new Color(0x55FF55);
            Color grassDark = new Color(0x3FB83F);
            Color dirt = new Color(0x6B4B2F);
            Color dirtDark = new Color(0x543A24);
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    Color color;
                    if (y < 3) {
                        color = (x + y) % 3 == 0 ? grassDark : grass;
                    } else if (y == 3) {
                        color = (x % 2 == 0) ? grassDark : dirt;
                    } else {
                        color = ((x * 3 + y * 5) % 4 == 0) ? dirtDark : dirt;
                    }
                    g2.setColor(color);
                    g2.fillRect(ox + x * cell, oy + y * cell, cell, cell);
                }
            }
            g2.setColor(Theme.withAlpha(Theme.GREEN, 60));
            g2.drawRect(0, 0, size - 1, size - 1);
            g2.dispose();
        }
    }

    /** 服务器图标 */
    static final class FaviconView extends JComponent {
        private final Image image;
        private final int size;

        FaviconView(byte[] png, int size) {
            this.size = size;
            Image decoded = null;
            if (png != null && png.length > 0) {
                try {
                    decoded = ImageIO.read(new ByteArrayInputStream(png));
                } catch (Exception ignored) {
                    // 保持 null，用占位图
                }
            }
            this.image = decoded;
            setPreferredSize(new Dimension(size, size));
            setMinimumSize(new Dimension(size, size));
            setMaximumSize(new Dimension(size, size));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            if (image != null) {
                g2.drawImage(image, 0, 0, size, size, null);
            } else {
                g2.setColor(new Color(0x0B120F));
                g2.fillRect(0, 0, size, size);
                int cell = Math.max(3, size / 8);
                for (int y = 0; y < 8; y++) {
                    for (int x = 0; x < 8; x++) {
                        g2.setColor(y < 3 ? new Color(0x4FD94F) : new Color(0x6B4B2F));
                        g2.fillRect(x * cell, y * cell, cell, cell);
                    }
                }
            }
            g2.setColor(new Color(0, 0, 0, 160));
            g2.drawRect(0, 0, size - 1, size - 1);
            g2.dispose();
        }
    }

    /** 延迟信号条 */
    static final class LatencyView extends JComponent {
        private final double latencyMs;

        LatencyView(double latencyMs) {
            this.latencyMs = latencyMs;
            setPreferredSize(new Dimension(96, 54));
            setMinimumSize(new Dimension(96, 54));
            setMaximumSize(new Dimension(96, 54));
        }

        private int level() {
            if (latencyMs < 60) return 5;
            if (latencyMs < 120) return 4;
            if (latencyMs < 250) return 3;
            if (latencyMs < 500) return 2;
            return 1;
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int bars = 5;
            int barW = 4;
            int gap = 3;
            int totalW = bars * barW + (bars - 1) * gap;
            int startX = getWidth() - totalW - 4;
            int baseY = 34;
            int filled = level();
            Color color = Theme.latencyColor(latencyMs);
            for (int i = 0; i < bars; i++) {
                int height = 5 + i * 3;
                int x = startX + i * (barW + gap);
                g2.setColor(i < filled ? color : new Color(255, 255, 255, 30));
                g2.fillRect(x, baseY - height, barW, height);
            }
            g2.setColor(Theme.MUTED);
            g2.setFont(Theme.mono(11, Font.PLAIN));
            String text = String.format("%.1f ms", latencyMs);
            int textW = g2.getFontMetrics().stringWidth(text);
            g2.drawString(text, getWidth() - textW - 4, baseY + 16);
            g2.dispose();
        }
    }
}
