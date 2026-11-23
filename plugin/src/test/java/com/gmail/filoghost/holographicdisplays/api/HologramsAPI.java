package com.gmail.filoghost.holographicdisplays.api;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.plugin.Plugin;

import com.gmail.filoghost.holographicdisplays.api.placeholder.PlaceholderReplacer;

/** Test double with the signatures of HolographicDisplays 2.2.6's placeholder API (checked in its sources). */
public class HologramsAPI {

    public static final Map<String, PlaceholderReplacer> REGISTERED = new LinkedHashMap<>();
    public static final Map<String, Double> RATES = new LinkedHashMap<>();

    public static boolean registerPlaceholder(Plugin plugin, String textPlaceholder, double refreshRate,
        PlaceholderReplacer replacer) {
        if (REGISTERED.containsKey(textPlaceholder)) return false;
        REGISTERED.put(textPlaceholder, replacer);
        RATES.put(textPlaceholder, refreshRate);
        return true;
    }

    public static void unregisterPlaceholders(Plugin plugin) {
        REGISTERED.clear();
        RATES.clear();
    }
}
