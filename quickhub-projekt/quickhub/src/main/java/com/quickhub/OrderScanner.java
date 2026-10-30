package com.quickhub;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fuehrt /orders aus, liest jede Seite des Inventar-Menues, klickt auf "Naechste Seite"
 * und speichert am Ende einen Snapshot der Preise.
 */
public class OrderScanner {
    private enum State { IDLE, WAIT_OPEN, READ, WAIT_NEXT }

    private static final Pattern PRICE = Pattern.compile("\\$\\s*([0-9][0-9.,]*)\\s*([kKmMbB]?)");

    private static State state = State.IDLE;
    private static int timer, page, stable, ordersSeen;
    private static String lastSig = "";
    private static String prevSig = "";
    private static final Set<String> seen = new HashSet<>();
    private static Map<String, List<Double>> prices = new HashMap<>();

    public static boolean isRunning() {
        return state != State.IDLE;
    }

    public static void start() {
        if (isRunning()) return;
        MinecraftClient c = MinecraftClient.getInstance();
        if (c.player == null || c.getNetworkHandler() == null) return;
        prices = new HashMap<>();
        seen.clear();
        page = 0;
        stable = 0;
        timer = 0;
        ordersSeen = 0;
        lastSig = "";
        prevSig = "";
        state = State.WAIT_OPEN;
        msg(c, "Scan gestartet (/" + Config.get().command + ")...");
        c.getNetworkHandler().sendChatCommand(Config.get().command);
    }

    public static void tick(MinecraftClient c) {
        if (state == State.IDLE) return;
        if (c.player == null) {
            state = State.IDLE;
            return;
        }
        timer++;
        GenericContainerScreen screen = (c.currentScreen instanceof GenericContainerScreen s) ? s : null;

        switch (state) {
            case WAIT_OPEN -> {
                if (screen != null) {
                    state = State.READ;
                    timer = 0;
                    stable = 0;
                    lastSig = "";
                } else if (timer > 100) {
                    finish(c, "Kein Fenster geöffnet (Timeout). Stimmt der Befehl in der Config?");
                }
            }
            case READ -> {
                if (screen == null) {
                    finish(c, "Fenster wurde geschlossen.");
                    return;
                }
                String sig = signature(screen);
                if (sig.isEmpty()) {
                    if (timer > 100) finish(c, "Fenster blieb leer.");
                    return;
                }
                if (sig.equals(lastSig)) stable++;
                else {
                    stable = 0;
                    lastSig = sig;
                }
                if (stable >= Config.get().settleTicks) {
                    if (!seen.add(sig)) {
                        finish(c, null); // Seite schon gesehen -> fertig
                        return;
                    }
                    readPage(screen);
                    page++;
                    Slot next = findNext(screen);
                    if (next == null || page >= Config.get().maxPages) {
                        finish(c, null);
                        return;
                    }
                    prevSig = sig;
                    click(c, screen, next.id);
                    state = State.WAIT_NEXT;
                    timer = 0;
                }
            }
            case WAIT_NEXT -> {
                if (screen != null) {
                    String sig = signature(screen);
                    if (!sig.isEmpty() && !sig.equals(prevSig)) {
                        state = State.READ;
                        timer = 0;
                        stable = 0;
                        lastSig = sig;
                        return;
                    }
                }
                if (timer > 60) finish(c, null); // keine Aenderung = letzte Seite
            }
            default -> {
            }
        }
    }

    private static void finish(MinecraftClient c, String problem) {
        state = State.IDLE;
        if (c.player != null && c.currentScreen instanceof GenericContainerScreen) {
            c.player.closeHandledScreen();
        }
        if (prices.isEmpty()) {
            msg(c, (problem != null ? problem + " " : "") + "Keine Preise gefunden. Schau in config/quickhub-orders-dump.txt.");
            return;
        }
        Store.Snapshot snap = new Store.Snapshot();
        snap.time = System.currentTimeMillis();
        for (Map.Entry<String, List<Double>> e : prices.entrySet()) {
            Store.ItemStat st = new Store.ItemStat();
            double sum = 0, max = 0;
            for (double v : e.getValue()) {
                sum += v;
                max = Math.max(max, v);
            }
            st.n = e.getValue().size();
            st.avg = sum / st.n;
            st.max = max;
            snap.items.put(e.getKey(), st);
        }
        Store.add(snap);
        msg(c, "Fertig: " + ordersSeen + " Orders, " + snap.items.size() + " Items, " + page + " Seite(n)."
                + (problem != null ? " (" + problem + ")" : "") + " Öffne den Quick Hub mit H.");
    }

    // ---------------------------------------------------------------- Lesen

    private static List<Slot> containerSlots(GenericContainerScreen screen) {
        GenericContainerScreenHandler h = screen.getScreenHandler();
        int n = h.getRows() * 9;
        return h.slots.subList(0, Math.min(n, h.slots.size()));
    }

    private static List<String> loreOf(ItemStack st) {
        List<String> out = new ArrayList<>();
        LoreComponent lore = st.get(DataComponentTypes.LORE);
        if (lore != null) {
            for (Text t : lore.lines()) out.add(t.getString());
        }
        return out;
    }

    private static String signature(GenericContainerScreen screen) {
        StringBuilder sb = new StringBuilder();
        for (Slot s : containerSlots(screen)) {
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            sb.append(s.id).append('|')
                    .append(Registries.ITEM.getId(st.getItem())).append('|')
                    .append(st.getCount()).append('|')
                    .append(st.getName().getString()).append('|')
                    .append(String.join("~", loreOf(st))).append('\n');
        }
        String str = sb.toString();
        return str.isEmpty() ? "" : str.hashCode() + ":" + str.length();
    }

    private static void readPage(GenericContainerScreen screen) {
        StringBuilder dump = new StringBuilder();
        for (Slot s : containerSlots(screen)) {
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            String id = Registries.ITEM.getId(st.getItem()).getPath();
            List<String> lore = loreOf(st);
            if (page == 0) {
                dump.append("Slot ").append(s.id).append(": ").append(id)
                        .append(" x").append(st.getCount())
                        .append(" | Name: ").append(st.getName().getString()).append('\n');
                for (String l : lore) dump.append("    ").append(l).append('\n');
            }
            double p = parsePrice(lore);
            if (p > 0) {
                prices.computeIfAbsent(id, k -> new ArrayList<>()).add(p);
                ordersSeen++;
            }
        }
        if (page == 0) {
            try {
                Files.writeString(FabricLoader.getInstance().getConfigDir().resolve("quickhub-orders-dump.txt"), dump.toString());
            } catch (Exception ignored) {
            }
        }
    }

    static double parsePrice(List<String> lines) {
        double fallback = -1;
        for (String line : lines) {
            Matcher m = PRICE.matcher(line);
            if (!m.find()) continue;
            double v = toNumber(m.group(1), m.group(2));
            if (v <= 0) continue;
            String low = line.toLowerCase();
            for (String k : Config.get().eachKeywords) {
                if (low.contains(k)) return v;
            }
            if (fallback < 0) fallback = v;
        }
        return fallback;
    }

    private static double toNumber(String num, String suffix) {
        try {
            double v = Double.parseDouble(num.replace(",", ""));
            switch (suffix.toLowerCase()) {
                case "k" -> v *= 1_000;
                case "m" -> v *= 1_000_000;
                case "b" -> v *= 1_000_000_000;
                default -> {
                }
            }
            return v;
        } catch (Exception e) {
            return -1;
        }
    }

    // ---------------------------------------------------------------- Naechste Seite

    private static Slot findNext(GenericContainerScreen screen) {
        Slot found = null;
        for (Slot s : containerSlots(screen)) {
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            String name = st.getName().getString().toLowerCase();
            for (String k : Config.get().nextKeywords) {
                if (name.contains(k)) {
                    found = s;
                    break;
                }
            }
        }
        if (found != null) return found;
        // Fallback: letzter Pfeil, dessen Name nicht nach "zurueck" aussieht
        for (Slot s : containerSlots(screen)) {
            ItemStack st = s.getStack();
            if (st.isEmpty()) continue;
            String id = Registries.ITEM.getId(st.getItem()).getPath();
            String name = st.getName().getString().toLowerCase();
            if (id.equals("arrow") && !name.contains("back") && !name.contains("prev")
                    && !name.contains("zurück") && !name.contains("zurueck") && !name.contains("vorher")) {
                found = s;
            }
        }
        return found;
    }

    private static void click(MinecraftClient c, GenericContainerScreen screen, int slotId) {
        if (c.interactionManager == null || c.player == null) return;
        c.interactionManager.clickSlot(screen.getScreenHandler().syncId, slotId, 0, SlotActionType.PICKUP, c.player);
    }

    private static void msg(MinecraftClient c, String s) {
        if (c.player != null) c.player.sendMessage(Text.literal("[QuickHub] " + s), false);
    }
}
