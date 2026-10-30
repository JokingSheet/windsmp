package com.quickhub;

import com.google.gson.Gson;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Gleiche Datei wie die alte FavTPA-Mod (favtpa.json), deine Favoriten bleiben erhalten. */
public class Favorites {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("favtpa.json");
    private static final Gson GSON = new Gson();
    private static final List<String> NAMES = new ArrayList<>();

    public static void load() {
        NAMES.clear();
        try {
            if (Files.exists(FILE)) {
                String[] arr = GSON.fromJson(Files.readString(FILE), String[].class);
                if (arr != null) NAMES.addAll(Arrays.asList(arr));
            }
        } catch (Exception e) {
            System.err.println("[QuickHub] Konnte favtpa.json nicht laden: " + e);
        }
    }

    private static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(NAMES));
        } catch (Exception e) {
            System.err.println("[QuickHub] Konnte favtpa.json nicht speichern: " + e);
        }
    }

    public static List<String> list() {
        return NAMES;
    }

    public static boolean contains(String name) {
        return NAMES.stream().anyMatch(n -> n.equalsIgnoreCase(name));
    }

    public static void add(String name) {
        name = name.trim();
        if (name.isEmpty() || contains(name)) return;
        NAMES.add(name);
        save();
    }

    public static void remove(String name) {
        NAMES.removeIf(n -> n.equalsIgnoreCase(name));
        save();
    }
}
