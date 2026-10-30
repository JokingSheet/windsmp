package com.quickhub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Liest eingehende Chat-/Systemnachrichten mit und erkennt Zahlungen ueber Regex-Muster.
 * Die Muster stehen in config/quickhub-payments-config.json und lassen sich anpassen.
 */
public class Payments {
    private static final Path DATA = FabricLoader.getInstance().getConfigDir().resolve("quickhub-payments.json");
    private static final Path CFG = FabricLoader.getInstance().getConfigDir().resolve("quickhub-payments-config.json");
    private static final Path DOLLAR = FabricLoader.getInstance().getConfigDir().resolve("quickhub-chat-dollar.txt");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX = 300;

    public static class Payment {
        public long time;
        public String player = "?";
        public double amount;
        public boolean incoming;
    }

    public static class Pat {
        public String regex;
        public boolean incoming;

        public Pat() {
        }

        public Pat(String regex, boolean incoming) {
            this.regex = regex;
            this.incoming = incoming;
        }
    }

    public static class PCfg {
        public List<Pat> patterns = new ArrayList<>();
    }

    private record Compiled(Pattern p, boolean incoming) {
    }

    private static final List<Payment> LIST = new ArrayList<>();
    private static final List<Compiled> COMPILED = new ArrayList<>();
    private static final List<String> DOLLAR_LINES = new ArrayList<>();

    private static PCfg defaultCfg() {
        String amt = "(?<amount>[0-9][0-9.,]*\\s?[kKmMbB]?)";
        String pl = "(?<player>[A-Za-z0-9_.]{3,16})";
        PCfg c = new PCfg();
        c.patterns.add(new Pat(pl + " (?:paid|sent) you \\$" + amt, true));
        c.patterns.add(new Pat("You (?:paid|sent) " + pl + " \\$" + amt, false));
        c.patterns.add(new Pat("You (?:received|got) \\$" + amt + " from " + pl, true));
        c.patterns.add(new Pat("You (?:sent|paid) \\$" + amt + " to " + pl, false));
        c.patterns.add(new Pat(pl + " hat dir \\$" + amt + " (?:gezahlt|gesendet|überwiesen)", true));
        return c;
    }

    public static void load() {
        PCfg cfg = null;
        try {
            if (Files.exists(CFG)) cfg = GSON.fromJson(Files.readString(CFG), PCfg.class);
        } catch (Exception e) {
            System.err.println("[QuickHub] Zahlungs-Config defekt: " + e);
        }
        if (cfg == null || cfg.patterns == null || cfg.patterns.isEmpty()) {
            cfg = defaultCfg();
            try {
                Files.writeString(CFG, GSON.toJson(cfg));
            } catch (Exception ignored) {
            }
        }
        COMPILED.clear();
        for (Pat p : cfg.patterns) {
            try {
                COMPILED.add(new Compiled(Pattern.compile(p.regex, Pattern.CASE_INSENSITIVE), p.incoming));
            } catch (Exception e) {
                System.err.println("[QuickHub] Ungueltiges Muster: " + p.regex);
            }
        }
        try {
            if (Files.exists(DATA)) {
                List<Payment> l = GSON.fromJson(Files.readString(DATA), new TypeToken<List<Payment>>() {}.getType());
                if (l != null) {
                    LIST.clear();
                    LIST.addAll(l);
                }
            }
        } catch (Exception e) {
            System.err.println("[QuickHub] Konnte Zahlungen nicht laden: " + e);
        }
    }

    private static void save() {
        try {
            Files.writeString(DATA, GSON.toJson(LIST));
        } catch (Exception e) {
            System.err.println("[QuickHub] Konnte Zahlungen nicht speichern: " + e);
        }
    }

    public static List<Payment> list() {
        return LIST;
    }

    public static void clear() {
        LIST.clear();
        save();
    }

    /** Wird fuer jede Spielnachricht aufgerufen. */
    public static void onMessage(Text t, boolean overlay) {
        if (overlay) return;
        String s = t.getString();
        if (s.isEmpty()) return;

        if (s.indexOf('$') >= 0) rememberDollar(s);

        for (Compiled c : COMPILED) {
            Matcher m = c.p().matcher(s);
            if (!m.find()) continue;
            try {
                double amount = Util.parseAmount(m.group("amount"));
                if (amount <= 0) continue;
                Payment p = new Payment();
                p.time = System.currentTimeMillis();
                p.amount = amount;
                p.incoming = c.incoming();
                try {
                    p.player = m.group("player");
                } catch (IllegalArgumentException ignored) {
                }
                LIST.add(p);
                while (LIST.size() > MAX) LIST.remove(0);
                save();
                return;
            } catch (IllegalArgumentException e) {
                // Muster ohne Gruppe "amount" -> ignorieren
            }
        }
    }

    /** Merkt die letzten Zeilen mit $ in einer Datei, damit man Muster anpassen kann. */
    private static void rememberDollar(String s) {
        DOLLAR_LINES.add(s);
        while (DOLLAR_LINES.size() > 40) DOLLAR_LINES.remove(0);
        try {
            Files.writeString(DOLLAR, String.join("\n", DOLLAR_LINES));
        } catch (Exception ignored) {
        }
    }
}
