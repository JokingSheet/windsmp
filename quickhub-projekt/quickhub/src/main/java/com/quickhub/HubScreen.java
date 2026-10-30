package com.quickhub;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HubScreen extends Screen {
    private static final int W = 340;
    private static final int TOP = 60;
    private static final String[] TABS = {"Schnell", "Favoriten", "Orders", "Zahlungen"};
    private static final String[] SORTS = {"Größter Anstieg", "Größter Fall", "Name", "Höchster Preis"};

    private static int tab = 0;
    private static int sortMode = 0;

    private record IconSpot(ItemStack stack, int x, int y) {
    }

    private int x0;
    private int scroll = 0;
    private int favScroll = 0;
    private int onlineScroll = 0;
    private String input = "";
    private List<Store.Row> rows = new ArrayList<>();
    private final List<IconSpot> icons = new ArrayList<>();
    private final Map<String, ItemStack> iconCache = new HashMap<>();

    public HubScreen() {
        super(Text.literal("Quick Hub"));
    }

    // ================================================================ Aufbau

    @Override
    protected void init() {
        x0 = (width - W) / 2;
        icons.clear();

        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            ButtonWidget b = ButtonWidget.builder(Text.literal(TABS[i]), btn -> {
                tab = idx;
                scroll = 0;
                clearAndInit();
            }).dimensions(x0 + i * 86, 22, 82, 20).build();
            b.active = (i != tab);
            addDrawableChild(b);
        }

        switch (tab) {
            case 0 -> initQuick();
            case 1 -> initFav();
            case 2 -> initOrders();
            default -> initPayments();
        }
    }

    private ItemStack iconStack(String id) {
        return iconCache.computeIfAbsent(id, k -> {
            Identifier ident = Identifier.tryParse(k);
            Item item = ident == null ? Items.BARRIER : Registries.ITEM.get(ident);
            return new ItemStack(item);
        });
    }

    // ---------------------------------------------------------------- Tab 1: Schnell

    private void initQuick() {
        List<Quick.Entry> list = Quick.list();
        for (int i = 0; i < list.size(); i++) {
            Quick.Entry e = list.get(i);
            int y = TOP + i * 30;
            addDrawableChild(ButtonWidget.builder(Text.literal(e.label), b -> runCommand(e.command))
                    .dimensions(x0, y, 170, 20).build());
            icons.add(new IconSpot(iconStack(e.icon), x0 + 4, y + 2));

            TextFieldWidget f = new TextFieldWidget(textRenderer, x0 + 176, y, 164, 20, Text.literal("Befehl"));
            f.setMaxLength(100);
            f.setPlaceholder(Text.literal("Befehl ohne /"));
            f.setText(e.command);
            f.setChangedListener(s -> {
                e.command = s;
                Quick.save();
            });
            addDrawableChild(f);
        }
    }

    private void runCommand(String cmd) {
        String c = cmd == null ? "" : cmd.trim();
        if (c.startsWith("/")) c = c.substring(1);
        if (c.isEmpty()) return;
        if (client != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand(c);
        }
        close();
    }

    // ---------------------------------------------------------------- Tab 2: Favoriten

    private int favRows() {
        return Math.max(3, Math.min(8, (height - TOP - 44) / 24));
    }

    private void initFav() {
        final int rowsN = favRows();
        final int colW = 160;
        int leftX = x0;
        int rightX = x0 + W - colW;

        List<String> favs = Favorites.list();
        favScroll = Math.max(0, Math.min(favScroll, favs.size() - rowsN));
        for (int i = 0; i < rowsN; i++) {
            int idx = favScroll + i;
            if (idx >= favs.size()) break;
            String name = favs.get(idx);
            int y = TOP + i * 24;
            addDrawableChild(ButtonWidget.builder(Text.literal("TPA: " + name), b -> tpa(name))
                    .dimensions(leftX, y, colW - 24, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                Favorites.remove(name);
                clearAndInit();
            }).dimensions(leftX + colW - 20, y, 20, 20).build());
        }

        int fieldY = TOP + rowsN * 24 + 4;
        TextFieldWidget field = new TextFieldWidget(textRenderer, leftX, fieldY, colW - 24, 20, Text.literal("Name"));
        field.setMaxLength(16);
        field.setPlaceholder(Text.literal("Name eingeben..."));
        field.setText(input);
        field.setChangedListener(s -> input = s);
        addDrawableChild(field);
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> {
            Favorites.add(input);
            input = "";
            clearAndInit();
        }).dimensions(leftX + colW - 20, fieldY, 20, 20).build());

        List<String> online = onlineNames();
        onlineScroll = Math.max(0, Math.min(onlineScroll, online.size() - rowsN));
        for (int i = 0; i < rowsN; i++) {
            int idx = onlineScroll + i;
            if (idx >= online.size()) break;
            String name = online.get(idx);
            int y = TOP + i * 24;
            addDrawableChild(ButtonWidget.builder(Text.literal("TPA: " + name), b -> tpa(name))
                    .dimensions(rightX, y, colW - 24, 20).build());
            ButtonWidget add = ButtonWidget.builder(Text.literal("+"), b -> {
                Favorites.add(name);
                clearAndInit();
            }).dimensions(rightX + colW - 20, y, 20, 20).build();
            add.active = !Favorites.contains(name);
            addDrawableChild(add);
        }
    }

    private List<String> onlineNames() {
        List<String> names = new ArrayList<>();
        if (client == null || client.getNetworkHandler() == null) return names;
        String self = client.getSession().getUsername();
        for (PlayerListEntry e : client.getNetworkHandler().getPlayerList()) {
            String n = e.getProfile().name();
            if (!n.equalsIgnoreCase(self)) names.add(n);
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    private void tpa(String name) {
        if (client != null && client.getNetworkHandler() != null) {
            client.getNetworkHandler().sendChatCommand("tpa " + name);
        }
        close();
    }

    // ---------------------------------------------------------------- Tab 3: Orders

    private void initOrders() {
        rows = Store.analyze();
        sortRows();
        int by = height - 26;
        addDrawableChild(ButtonWidget.builder(Text.literal("Scan starten"), b -> {
            close();
            OrderScanner.start();
        }).dimensions(x0, by, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Sortierung: " + SORTS[sortMode]), b -> {
            sortMode = (sortMode + 1) % SORTS.length;
            clearAndInit();
        }).dimensions(x0 + 104, by, 150, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Daten löschen"), b -> {
            Store.clear();
            clearAndInit();
        }).dimensions(x0 + 258, by, 82, 20).build());
    }

    private static double val(double d) {
        return Double.isNaN(d) ? 0 : d;
    }

    private void sortRows() {
        switch (sortMode) {
            case 0 -> rows.sort((a, b) -> Double.compare(val(b.chg), val(a.chg)));
            case 1 -> rows.sort((a, b) -> Double.compare(val(a.chg), val(b.chg)));
            case 2 -> rows.sort((a, b) -> a.id.compareTo(b.id));
            default -> rows.sort((a, b) -> Double.compare(b.avg, a.avg));
        }
    }

    // ---------------------------------------------------------------- Tab 4: Zahlungen

    private void initPayments() {
        addDrawableChild(ButtonWidget.builder(Text.literal("Liste leeren"), b -> {
            Payments.clear();
            clearAndInit();
        }).dimensions(x0, height - 26, 100, 20).build());
    }

    // ================================================================ Eingabe

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int dir = verticalAmount > 0 ? -1 : 1;
        if (tab == 1) {
            if (mouseX < width / 2.0) favScroll += dir;
            else onlineScroll += dir;
            clearAndInit();
        } else if (tab == 2 || tab == 3) {
            scroll += dir;
        }
        return true;
    }

    // ================================================================ Zeichnen

    private void txt(DrawContext ctx, String s, int x, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, Text.literal(s), x, y, color);
    }

    private void ctr(DrawContext ctx, String s, int y, int color) {
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal(s), width / 2, y, color);
    }

    private String fit(String s, int maxW) {
        while (s.length() > 1 && textRenderer.getWidth(s) > maxW) s = s.substring(0, s.length() - 1);
        return s;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctr(ctx, "Quick Hub", 8, 0xFFFFFFFF);
        switch (tab) {
            case 0 -> renderQuick(ctx);
            case 1 -> renderFav(ctx);
            case 2 -> renderOrders(ctx);
            default -> renderPayments(ctx);
        }
    }

    private void renderQuick(DrawContext ctx) {
        txt(ctx, "Klick = Befehl ausführen. Rechts den Befehl ändern (ohne /).", x0, TOP - 12, 0xFFAAAAAA);
        for (IconSpot s : icons) ctx.drawItem(s.stack(), s.x(), s.y());
        int y = TOP + Quick.list().size() * 30 + 4;
        txt(ctx, "Die Befehle werden automatisch gespeichert.", x0, y, 0xFF888888);
        txt(ctx, "Namen, Icons: config/quickhub-commands.json", x0, y + 12, 0xFF888888);
    }

    private void renderFav(DrawContext ctx) {
        txt(ctx, "Favoriten", x0, TOP - 12, 0xFFFFFF55);
        txt(ctx, "Online", x0 + W - 160, TOP - 12, 0xFF55FF55);
    }

    // ---- Orders

    private static final int OROW = 18;

    private static String pretty(String id) {
        return id.replace('_', ' ');
    }

    private static String pct(double v) {
        return Double.isNaN(v) ? "-" : String.format(Locale.US, "%+.1f%%", v);
    }

    private static int color(double v) {
        if (Double.isNaN(v)) return 0xFF888888;
        if (v > 0.5) return 0xFF55FF55;
        if (v < -0.5) return 0xFFFF5555;
        return 0xFFAAAAAA;
    }

    private void renderOrders(DrawContext ctx) {
        String info = Store.snapshotCount() + " Scan(s)";
        if (Store.lastTime() > 0) {
            info += " | letzter: " + new SimpleDateFormat("dd.MM. HH:mm").format(new Date(Store.lastTime()));
        }
        if (OrderScanner.isRunning()) info += " | Scan läuft...";
        ctr(ctx, info, 46, 0xFFAAAAAA);

        if (rows.isEmpty()) {
            ctr(ctx, "Noch keine Daten. \"Scan starten\" oder Taste J.", height / 2 - 6, 0xFFFFFF55);
            ctr(ctx, "Für Trends brauchst du mindestens 2 Scans.", height / 2 + 8, 0xFFAAAAAA);
            return;
        }

        int hy = TOP - 2;
        txt(ctx, "Item", x0, hy, 0xFFFFFF55);
        txt(ctx, "Ø Preis", x0 + 112, hy, 0xFFFFFF55);
        txt(ctx, "vs. zuletzt", x0 + 170, hy, 0xFFFFFF55);
        txt(ctx, "vs. Ø5", x0 + 232, hy, 0xFFFFFF55);
        txt(ctx, "Verlauf", x0 + 284, hy, 0xFFFFFF55);

        int startY = TOP + 10;
        int vis = Math.max(1, (height - 34 - startY) / OROW);
        int max = Math.max(0, rows.size() - vis);
        scroll = Math.max(0, Math.min(scroll, max));

        for (int i = 0; i < vis; i++) {
            int idx = scroll + i;
            if (idx >= rows.size()) break;
            Store.Row r = rows.get(idx);
            int y = startY + i * OROW;
            if (i % 2 == 0) ctx.fill(x0 - 4, y - 1, x0 + W, y + OROW - 1, 0x30FFFFFF);
            ctx.drawItem(iconStack("minecraft:" + r.id), x0, y);
            int ty = y + 5;
            txt(ctx, fit(pretty(r.id), 88), x0 + 20, ty, 0xFFFFFFFF);
            txt(ctx, Util.fmt(r.avg), x0 + 112, ty, 0xFFDDDDDD);
            txt(ctx, pct(r.chg), x0 + 170, ty, color(r.chg));
            txt(ctx, pct(r.chg5), x0 + 232, ty, color(r.chg5));
            drawSpark(ctx, r.hist, x0 + 284, y + 14);
        }
        ctr(ctx, (scroll + 1) + "-" + Math.min(rows.size(), scroll + vis) + " von " + rows.size(),
                height - 38, 0xFF888888);
    }

    private void drawSpark(DrawContext ctx, List<Double> hist, int x, int baseY) {
        if (hist.size() < 2) return;
        double min = Double.MAX_VALUE, mx = 0;
        for (double v : hist) {
            min = Math.min(min, v);
            mx = Math.max(mx, v);
        }
        double range = mx - min;
        for (int i = 0; i < hist.size(); i++) {
            int h = range <= 0 ? 5 : 2 + (int) ((hist.get(i) - min) / range * 9);
            int col = (i > 0 && hist.get(i) >= hist.get(i - 1)) ? 0xFF55FF55 : 0xFFFF5555;
            if (i == 0) col = 0xFF888888;
            ctx.fill(x + i * 4, baseY - h, x + i * 4 + 3, baseY, col);
        }
    }

    // ---- Zahlungen

    private void renderPayments(DrawContext ctx) {
        List<Payments.Payment> list = Payments.list();
        if (list.isEmpty()) {
            ctr(ctx, "Noch keine Zahlungen erkannt.", height / 2 - 20, 0xFFFFFF55);
            ctr(ctx, "Zahlungs-Nachrichten im Chat werden automatisch mitgelesen.", height / 2 - 6, 0xFFAAAAAA);
            ctr(ctx, "Passt das Muster nicht: config/quickhub-chat-dollar.txt", height / 2 + 8, 0xFFAAAAAA);
            return;
        }
        double in = 0, out = 0;
        for (Payments.Payment p : list) {
            if (p.incoming) in += p.amount;
            else out += p.amount;
        }
        txt(ctx, "Eingang: " + Util.fmt(in), x0, 46, 0xFF55FF55);
        txt(ctx, "Ausgang: " + Util.fmt(out), x0 + 120, 46, 0xFFFF5555);
        txt(ctx, "Saldo: " + Util.fmt(in - out), x0 + 240, 46, 0xFFFFFFFF);

        int prow = 12;
        int vis = Math.max(1, (height - 34 - TOP) / prow);
        int max = Math.max(0, list.size() - vis);
        scroll = Math.max(0, Math.min(scroll, max));
        SimpleDateFormat fmt = new SimpleDateFormat("dd.MM. HH:mm");

        for (int i = 0; i < vis; i++) {
            int idx = list.size() - 1 - (scroll + i); // neueste zuerst
            if (idx < 0) break;
            Payments.Payment p = list.get(idx);
            int y = TOP + i * prow;
            if (i % 2 == 0) ctx.fill(x0 - 4, y - 1, x0 + W, y + prow - 1, 0x30FFFFFF);
            int col = p.incoming ? 0xFF55FF55 : 0xFFFF5555;
            txt(ctx, fmt.format(new Date(p.time)), x0, y + 1, 0xFF999999);
            txt(ctx, (p.incoming ? "+" : "-") + Util.fmt(p.amount), x0 + 80, y + 1, col);
            txt(ctx, (p.incoming ? "von " : "an ") + p.player, x0 + 170, y + 1, 0xFFFFFFFF);
        }
        ctr(ctx, (scroll + 1) + "-" + Math.min(list.size(), scroll + vis) + " von " + list.size(),
                height - 38, 0xFF888888);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
