package akasha.questui.access;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Client copy of what only the server knows: whether it runs this mod and which permission nodes used by category
 * rules the local player has (sent by the server, DESIGN §11). No client classes, so common code can read it.
 */
public final class ClientState {

    private static volatile boolean serverHasMod;
    private static volatile Set<String> perms = Collections.emptySet();

    private ClientState() {}

    public static void set(Collection<String> granted) {
        perms = Collections.unmodifiableSet(new HashSet<>(granted));
        serverHasMod = true;
    }

    public static void reset() {
        perms = Collections.emptySet();
        serverHasMod = false;
    }

    /** Without the mod on the server nobody can grant a node, so permission categories stay hidden. */
    public static boolean has(String node) {
        return perms.contains(node);
    }

    public static boolean serverHasMod() {
        return serverHasMod;
    }
}
