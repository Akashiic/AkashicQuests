package akasha.queststats;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;

import org.bukkit.plugin.Plugin;

/**
 * Registers global placeholders in HolographicDisplays 2.x through its API ({@code HologramsAPI.registerPlaceholder},
 * checked against the 2.2.6 sources). Called by reflection: the HolographicDisplays 2.2.6 jar is not published in
 * any Maven repository, and this way the plugin also loads without HolographicDisplays.
 */
final class HdPlaceholders {

    private static final String API = "com.gmail.filoghost.holographicdisplays.api.HologramsAPI";
    private static final String REPLACER = "com.gmail.filoghost.holographicdisplays.api.placeholder.PlaceholderReplacer";

    private final Plugin plugin;
    private final Method register;
    private final Method unregisterAll;
    private final Class<?> replacer;

    private HdPlaceholders(Plugin plugin, Method register, Method unregisterAll, Class<?> replacer) {
        this.plugin = plugin;
        this.register = register;
        this.unregisterAll = unregisterAll;
        this.replacer = replacer;
    }

    /** The HolographicDisplays API of this server, or null when the plugin or its API is missing. */
    static HdPlaceholders find(Plugin plugin) {
        if (!plugin.getServer()
            .getPluginManager()
            .isPluginEnabled("HolographicDisplays")) return null;
        ClassLoader cl = plugin.getServer()
            .getPluginManager()
            .getPlugin("HolographicDisplays")
            .getClass()
            .getClassLoader();
        return load(plugin, cl);
    }

    /** The API as seen from the class loader cl (HolographicDisplays' own), or null when it is not the 2.x one. */
    static HdPlaceholders load(Plugin plugin, ClassLoader cl) {
        try {
            Class<?> api = Class.forName(API, true, cl);
            Class<?> rep = Class.forName(REPLACER, true, cl);
            Method reg = api.getMethod("registerPlaceholder", Plugin.class, String.class, double.class, rep);
            Method unreg = api.getMethod("unregisterPlaceholders", Plugin.class);
            return new HdPlaceholders(plugin, reg, unreg, rep);
        } catch (ReflectiveOperationException | LinkageError e) {
            if (plugin != null) plugin.getLogger()
                .warning("HolographicDisplays found but its placeholder API is not the 2.x one: " + e);
            return null;
        }
    }

    /** Registers {@code text} (e.g. "{aq_top1}"); false when HolographicDisplays refused it (already taken). */
    boolean register(String text, double refreshSeconds, Supplier<String> value) {
        Object proxy = Proxy.newProxyInstance(replacer.getClassLoader(), new Class<?>[] { replacer }, (p, m, args) -> {
            switch (m.getName()) {
                case "update":
                    return value.get();
                case "hashCode":
                    return System.identityHashCode(p);
                case "equals":
                    return p == args[0];
                case "toString":
                    return "AkashaQuestStats" + text;
                default:
                    return null;
            }
        });
        try {
            return (Boolean) register.invoke(null, plugin, text, refreshSeconds, proxy);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger()
                .warning("Could not register " + text + " in HolographicDisplays: " + e);
            return false;
        }
    }

    void unregisterAll() {
        try {
            unregisterAll.invoke(null, plugin);
        } catch (ReflectiveOperationException e) {
            plugin.getLogger()
                .warning("Could not unregister the HolographicDisplays placeholders: " + e);
        }
    }
}
