package com.quickhub;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Schnellbefehl-Buttons mit Icon. Der Befehl ist in der GUI editierbar. */
public class Quick {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("quickhub-commands.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static List<Entry> LIST = new ArrayList<>();

    public static class Entry {
        public String label = "";
        public String icon = "minecraft:stick";   // Item-ID fuer das Icon
        public String command = "";               // ohne fuehrenden /

        public Entry() {
        }

        public Entry(String label, String icon, String command) {
            this.label = label;
            this.icon = icon;
            this.command = command;
        }
    }

    private static void defaults() {
        LIST = new ArrayList<>();
        LIST.add(new Entry("Amboss", "minecraft:anvil", "anvil"));
        LIST.add(new Entry("Werkbank", "minecraft:crafting_table", "craft"));
        LIST.add(new Entry("Rüstungs-Trim", "minecraft:sentry_armor_trim_smithing_template", "smithingtable"));
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                List<Entry> l = GSON.fromJson(Files.readString(FILE), new TypeToken<List<Entry>>() {}.getType());
                if (l != null && !l.isEmpty()) {
                    LIST = l;
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[QuickHub] Konnte Schnellbefehle nicht laden: " + e);
        }
        defaults();
        save();
    }

    public static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(LIST));
        } catch (Exception e) {
            System.err.println("[QuickHub] Konnte Schnellbefehle nicht speichern: " + e);
        }
    }

    public static List<Entry> list() {
        return LIST;
    }
}
