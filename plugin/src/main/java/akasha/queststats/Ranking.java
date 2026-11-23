package akasha.queststats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Immutable snapshot of the quest ranking: who completed how many quests. Pure Java (no Bukkit, no BetterQuesting)
 * so it is unit tested on its own; {@link BqReader} fills it from the server's quest database.
 */
public final class Ranking {

    /** One ranked player. */
    public static final class Entry {

        public final UUID id;
        public final String name;
        public final int completed;

        Entry(UUID id, String name, int completed) {
            this.id = id;
            this.name = name;
            this.completed = completed;
        }
    }

    public static final Ranking EMPTY = new Ranking(Collections.<Entry>emptyList(), 0, 0, 0);

    /** Ranked players, most completions first; ties by name (case-insensitive), then id, so the order is stable. */
    public final List<Entry> entries;
    /** Quests in the database. */
    public final int totalQuests;
    /** Players with at least one completed quest (hidden players included). */
    public final int players;
    /** Sum of completions over all players (hidden players included). */
    public final long totalCompleted;

    private Ranking(List<Entry> entries, int totalQuests, int players, long totalCompleted) {
        this.entries = Collections.unmodifiableList(entries);
        this.totalQuests = totalQuests;
        this.players = players;
        this.totalCompleted = totalCompleted;
    }

    /**
     * @param completed completed-quest count per player (players with 0 are ignored)
     * @param names     player name for an id, or null when unknown (shown as the short id)
     * @param hidden    players left out of {@link #entries} (still counted in the totals)
     */
    public static Ranking of(Map<UUID, Integer> completed, int totalQuests, Function<UUID, String> names,
        Predicate<Entry> hidden) {
        List<Entry> list = new ArrayList<>();
        int players = 0;
        long sum = 0;
        for (Map.Entry<UUID, Integer> e : completed.entrySet()) {
            int n = e.getValue() == null ? 0 : e.getValue();
            if (n <= 0) continue;
            players++;
            sum += n;
            String name = names.apply(e.getKey());
            if (name == null || name.isEmpty()) name = e.getKey()
                .toString()
                .substring(0, 8);
            Entry entry = new Entry(e.getKey(), name, n);
            if (!hidden.test(entry)) list.add(entry);
        }
        list.sort((a, b) -> {
            if (a.completed != b.completed) return Integer.compare(b.completed, a.completed);
            int c = a.name.compareToIgnoreCase(b.name);
            return c != 0 ? c : a.id.compareTo(b.id);
        });
        return new Ranking(list, totalQuests, players, sum);
    }

    /** The player at 1-based position pos, or null past the end. */
    public Entry at(int pos) {
        return pos >= 1 && pos <= entries.size() ? entries.get(pos - 1) : null;
    }

    /** 1-based position of the player, or 0 when not ranked (no completions or hidden). */
    public int positionOf(UUID id) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).id.equals(id)) return i + 1;
        return 0;
    }
}
