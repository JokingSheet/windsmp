package com.quickhub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class Config {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("quickhub-orders-config.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config INSTANCE = new Config();

    // Befehl ohne Slash
    public String command = "orders";
    // Maximale Seitenzahl pro Scan
    public int maxPages = 10;
    // So viele Ticks muss der Inhalt unveraendert sein, bevor gelesen wird (20 Ticks = 1 Sekunde)
    public int settleTicks = 6;
    // Woran der "Naechste Seite"-Button im Namen erkannt wird (klein geschrieben)
    public List<String> nextKeywords = List.of("next", "weiter", "nächste", "naechste", "forward", "→", "»", ">>");
    // Zeilen mit diesen Woertern gelten als Einzelpreis (klein geschrieben)
    public List<String> eachKeywords = List.of("each", "per ", "pro stück", "pro stueck", "/ea", "price", "preis");

    public static Config get() {
        return INSTANCE;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                Config c = GSON.fromJson(Files.readString(FILE), Config.class);
                if (c != null) INSTANCE = c;
            } else {
                Files.writeString(FILE, GSON.toJson(INSTANCE));
            }
        } catch (Exception e) {
            System.err.println("[QuickHub] Config-Fehler: " + e);
        }
    }
}
