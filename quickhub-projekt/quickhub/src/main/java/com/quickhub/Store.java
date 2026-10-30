package com.quickhub;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Store {
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("quickhub-orders-data.json");
    private static final Gson GSON = new Gson();
    private static final int MAX_SNAPSHOTS = 500;
    private static List<Snapshot> SNAPS = new ArrayList<>();

    public static class ItemStat {
        public double avg;
        public double max;
        public int n;
    }

    public static class Snapshot {
        public long time;
        public Map<String, ItemStat> items = new HashMap<>();
    }

    public static class Row {
        public String id;
        public double avg;
        public double max;
        public int n;
        public double chg = Double.NaN;   // Aenderung zum letzten Scan in %
        public double chg5 = Double.NaN;  // Aenderung zum Schnitt der letzten bis zu 5 Scans in %
        public List<Double> hist = new ArrayList<>();
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) {
                List<Snapshot> l = GSON.fromJson(Files.readString(FILE), new TypeToken<List<Snapshot>>() {}.getType());
                if (l != null) SNAPS = l;
            }
        } catch (Exception e) {
            System.err.println("[QuickHub] Konnte Daten nicht laden: " + e);
        }
    }

    private static void save() {
        try {
            Files.writeString(FILE, GSON.toJson(SNAPS));
        } catch (Exception e) {
            System.err.println("[QuickHub] Konnte Daten nicht speichern: " + e);
        }
    }

    public static void add(Snapshot s) {
        SNAPS.add(s);
        while (SNAPS.size() > MAX_SNAPSHOTS) SNAPS.remove(0);
        save();
    }

    public static void clear() {
        SNAPS.clear();
        save();
    }

    public static int snapshotCount() {
        return SNAPS.size();
    }

    public static long lastTime() {
        return SNAPS.isEmpty() ? 0 : SNAPS.get(SNAPS.size() - 1).time;
    }

    public static List<Row> analyze() {
        List<Row> out = new ArrayList<>();
        if (SNAPS.isEmpty()) return out;
        Snapshot cur = SNAPS.get(SNAPS.size() - 1);
        for (Map.Entry<String, ItemStat> e : cur.items.entrySet()) {
            Row r = new Row();
            r.id = e.getKey();
            r.avg = e.getValue().avg;
            r.max = e.getValue().max;
            r.n = e.getValue().n;

            List<Double> hist = new ArrayList<>();
            for (Snapshot s : SNAPS) {
                ItemStat st = s.items.get(r.id);
                if (st != null) hist.add(st.avg);
            }
            r.hist = hist.size() > 12 ? new ArrayList<>(hist.subList(hist.size() - 12, hist.size())) : hist;

            if (hist.size() >= 2) {
                double prev = hist.get(hist.size() - 2);
                if (prev > 0) r.chg = (r.avg - prev) / prev * 100.0;
                int from = Math.max(0, hist.size() - 6);
                double sum = 0;
                int c = 0;
                for (int i = from; i < hist.size() - 1; i++) {
                    sum += hist.get(i);
                    c++;
                }
                if (c > 0 && sum > 0) {
                    double mean = sum / c;
                    r.chg5 = (r.avg - mean) / mean * 100.0;
                }
            }
            out.add(r);
        }
        return out;
    }
}
