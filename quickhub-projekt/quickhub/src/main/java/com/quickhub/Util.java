package com.quickhub;

import java.util.Locale;

public class Util {
    /** Wandelt "1,500.5", "$2K", "3.2M" in eine Zahl um. -1 bei Fehler. */
    public static double parseAmount(String raw) {
        if (raw == null) return -1;
        String s = raw.trim().replace(",", "").replace(" ", "");
        if (s.isEmpty()) return -1;
        double mult = 1;
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        if (last == 'k') mult = 1_000;
        else if (last == 'm') mult = 1_000_000;
        else if (last == 'b') mult = 1_000_000_000;
        if (mult != 1) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s) * mult;
        } catch (Exception e) {
            return -1;
        }
    }

    public static String fmt(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return String.format(Locale.US, "%.2fB", v / 1e9);
        if (a >= 1e6) return String.format(Locale.US, "%.2fM", v / 1e6);
        if (a >= 1e3) return String.format(Locale.US, "%.2fK", v / 1e3);
        return String.format(Locale.US, "%.2f", v);
    }
}
