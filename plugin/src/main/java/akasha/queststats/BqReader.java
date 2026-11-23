package akasha.queststats;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import betterquesting.api.questing.IQuest;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.storage.NameCache;

/**
 * Reads completions from BetterQuesting's server-side quest database. Must run on the server thread (BQ is not
 * thread-safe). Only BetterQuesting classes with JDK types in their signatures are called, so the plugin never
 * links against Minecraft classes (which Crucible remaps for plugins). Public for the mod's devtest, which runs it
 * against the real BetterQuesting in an integrated server.
 */
public final class BqReader {

    private BqReader() {}

    /** True when BetterQuesting is loaded in this server. */
    public static boolean available() {
        try {
            Class.forName("betterquesting.questing.QuestDatabase");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    /** Completed-quest count per player. */
    public static Map<UUID, Integer> completions() {
        Map<UUID, Integer> out = new HashMap<>();
        Set<UUID> users = new HashSet<>();
        for (IQuest q : QuestDatabase.INSTANCE.values()) {
            if (!(q instanceof QuestInstance)) continue;
            users.clear();
            ((QuestInstance) q).getUsersWithCompletionData(users);
            // completion data stays after a repeatable quest resets: count only what is complete now
            for (UUID u : users) if (q.isComplete(u)) out.merge(u, 1, Integer::sum);
        }
        return out;
    }

    public static int totalQuests() {
        return QuestDatabase.INSTANCE.size();
    }

    /** BetterQuesting's name for the player (kept for offline players), or null. */
    public static String name(UUID id) {
        return NameCache.INSTANCE.getName(id);
    }

    public static boolean isOp(UUID id) {
        return NameCache.INSTANCE.isOP(id);
    }
}
