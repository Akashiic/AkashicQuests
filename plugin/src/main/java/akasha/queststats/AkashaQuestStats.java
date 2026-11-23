package akasha.queststats;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Quest ranking from BetterQuesting for the Crucible server, shown through HolographicDisplays placeholders
 * ({aq_top1}, {aq_top1_name}, {aq_top1_count}, ..., {aq_total_quests}, {aq_players}, {aq_total_completed}).
 * <p>
 * Built against spigot-api 1.8.8 but uses only API that Bukkit 1.7.10 already had (JavaPlugin, config, scheduler,
 * commands, ChatColor, Bukkit.getOfflinePlayer(UUID) from 1.7.5), compiled to Java 8 bytecode.
 */
public final class AkashaQuestStats extends JavaPlugin {

    /** HolographicDisplays only reads our cached strings, so it may refresh often. */
    private static final double HD_REFRESH_SECONDS = 5.0;
    private static final long SLOW_REBUILD_MS = 50;

    private volatile Ranking ranking = Ranking.EMPTY;
    private BukkitTask task;
    private HdPlaceholders hd;

    private int topSize;
    private boolean hideOps;
    private final Set<String> hiddenNames = new HashSet<>();
    private String lineFormat;
    private String emptyName;
    private String emptyCount;
    private boolean warnedSlow;

    @Override
    public void onEnable() {
        if (!BqReader.available()) {
            getLogger().severe("BetterQuesting is not loaded in this server; disabling.");
            getServer().getPluginManager()
                .disablePlugin(this);
            return;
        }
        saveDefaultConfig();
        loadSettings();
        hd = HdPlaceholders.find(this);
        if (hd == null) getLogger().warning("HolographicDisplays 2.x not found: no hologram placeholders.");
        else registerPlaceholders();
        schedule();
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        if (hd != null) hd.unregisterAll();
    }

    private void loadSettings() {
        reloadConfig();
        FileConfiguration c = getConfig();
        topSize = Math.max(1, Math.min(100, c.getInt("top-size", 10)));
        hideOps = c.getBoolean("hide-ops", false);
        hiddenNames.clear();
        for (String n : c.getStringList("hidden-players")) hiddenNames.add(n.toLowerCase(Locale.ROOT));
        lineFormat = color(c.getString("format.line", "&e{pos}. &f{name} &7- &a{count}"));
        emptyName = color(c.getString("format.empty-name", "&7---"));
        emptyCount = color(c.getString("format.empty-count", "&70"));
    }

    private void schedule() {
        if (task != null) task.cancel();
        long ticks = 20L * Math.max(5, getConfig().getInt("refresh-seconds", 60));
        // server thread: BetterQuesting's database is not thread-safe
        task = Bukkit.getScheduler()
            .runTaskTimer(this, this::rebuild, 20L, ticks);
    }

    void rebuild() {
        long t0 = System.nanoTime();
        try {
            ranking = Ranking.of(BqReader.completions(), BqReader.totalQuests(), AkashaQuestStats::nameOf, e -> {
                if (hiddenNames.contains(e.name.toLowerCase(Locale.ROOT))) return true;
                return hideOps && BqReader.isOp(e.id);
            });
        } catch (RuntimeException | LinkageError e) {
            getLogger().warning("Could not read BetterQuesting progress: " + e);
            return;
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (ms > SLOW_REBUILD_MS && !warnedSlow) {
            warnedSlow = true;
            getLogger().warning(
                "Ranking rebuild took " + ms
                    + " ms on the server thread; raise refresh-seconds if this repeats (logged once).");
        }
    }

    private static String nameOf(UUID id) {
        String n = BqReader.name(id);
        if (n != null && !n.isEmpty()) return n;
        OfflinePlayer p = Bukkit.getOfflinePlayer(id);
        return p == null ? null : p.getName();
    }

    private void registerPlaceholders() {
        int ok = 0;
        for (int i = 1; i <= topSize; i++) {
            final int pos = i;
            ok += reg("{aq_top" + pos + "}", () -> line(pos));
            ok += reg("{aq_top" + pos + "_name}", () -> {
                Ranking.Entry e = ranking.at(pos);
                return e == null ? emptyName : e.name;
            });
            ok += reg("{aq_top" + pos + "_count}", () -> {
                Ranking.Entry e = ranking.at(pos);
                return e == null ? emptyCount : String.valueOf(e.completed);
            });
        }
        ok += reg("{aq_total_quests}", () -> String.valueOf(ranking.totalQuests));
        ok += reg("{aq_players}", () -> String.valueOf(ranking.players));
        ok += reg("{aq_total_completed}", () -> String.valueOf(ranking.totalCompleted));
        getLogger().info("Registered " + ok + " HolographicDisplays placeholders ({aq_top1} ... {aq_top" + topSize + "}).");
    }

    private int reg(String text, java.util.function.Supplier<String> value) {
        return hd.register(text, HD_REFRESH_SECONDS, value) ? 1 : 0;
    }

    String line(int pos) {
        Ranking.Entry e = ranking.at(pos);
        return lineFormat.replace("{pos}", String.valueOf(pos))
            .replace("{name}", e == null ? emptyName : e.name)
            .replace("{count}", e == null ? emptyCount : String.valueOf(e.completed))
            .replace("{total}", String.valueOf(ranking.totalQuests));
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "top" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload":
                if (!sender.hasPermission("akashaqueststats.admin")) {
                    sender.sendMessage(ChatColor.RED + "Sem permissão.");
                    return true;
                }
                loadSettings();
                if (hd != null) {
                    hd.unregisterAll();
                    registerPlaceholders();
                }
                schedule();
                rebuild();
                sender.sendMessage(ChatColor.GREEN + "AkashaQuestStats recarregado: " + ranking.players
                    + " jogadores com quests concluídas.");
                return true;
            case "top":
                sender.sendMessage(
                    ChatColor.GOLD + "Top quests (" + ranking.totalQuests + " no livro, " + ranking.players
                        + " jogadores):");
                for (int i = 1; i <= topSize; i++) {
                    if (ranking.at(i) == null) break;
                    sender.sendMessage(line(i));
                }
                return true;
            default:
                // a player name: their count and position
                for (Ranking.Entry e : ranking.entries) {
                    if (e.name.equalsIgnoreCase(args[0])) {
                        sender.sendMessage(
                            ChatColor.YELLOW + e.name + ChatColor.WHITE + ": " + e.completed + "/" + ranking.totalQuests
                                + " quests, posição " + ranking.positionOf(e.id));
                        return true;
                    }
                }
                sender.sendMessage(ChatColor.GRAY + "Uso: /" + label + " [top|reload|<jogador>]");
                return true;
        }
    }
}
