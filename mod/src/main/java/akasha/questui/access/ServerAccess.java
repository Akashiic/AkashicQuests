package akasha.questui.access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import akasha.questui.net.Net;
import betterquesting.api2.cache.QuestCache;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Server side of the category rules (DESIGN §11): every 2 s it re-reads each online player's permission nodes; a
 * change (or a dimension change) rebuilds that player's BQ quest cache so closed quests stop being active at once,
 * and players running the mod get the granted nodes for their panel.
 */
public final class ServerAccess {

    private static final int PERIOD_TICKS = 40;

    /** Players whose client runs the mod (they sent Hello). */
    private static final Set<UUID> WITH_MOD = Collections.synchronizedSet(new HashSet<UUID>());
    private static final Map<UUID, Set<String>> LAST = new HashMap<>();
    private static int tick;
    /** Packet handlers run on the network thread: their work is queued for the server thread. */
    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();

    public static void schedule(Runnable r) {
        QUEUE.add(r);
    }

    public static void hello(EntityPlayerMP p) {
        WITH_MOD.add(id(p));
        send(p, granted(p));
    }

    private static UUID id(EntityPlayerMP p) {
        return p.getGameProfile()
            .getId();
    }

    /** Nodes used by the rules that the player has. */
    private static Set<String> granted(EntityPlayerMP p) {
        Set<String> out = new TreeSet<>();
        for (String node : AccessRules.permissionNodes()) if (Permissions.has(p, node)) out.add(node);
        return out;
    }

    private static void send(EntityPlayerMP p, Set<String> nodes) {
        if (WITH_MOD.contains(id(p))) Net.CHANNEL.sendTo(new Net.Perms(nodes), p);
    }

    /** Rebuilds BQ's active/visible quest sets of the player with the current rules. */
    public static void refreshQuests(EntityPlayerMP p) {
        Object qc = p.getExtendedProperties(QuestCache.LOC_QUEST_CACHE.toString());
        if (qc instanceof QuestCache) ((QuestCache) qc).updateCache(p);
    }

    /** After a rule edit: fresh permission answers, quest caches and client copies for everyone. */
    public static void resyncAll() {
        for (EntityPlayerMP p : players()) {
            Permissions.forget(id(p));
            Set<String> g = granted(p);
            synchronized (LAST) {
                LAST.put(id(p), g);
            }
            refreshQuests(p);
            send(p, g);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<EntityPlayerMP> players() {
        MinecraftServer s = MinecraftServer.getServer();
        return s == null ? Collections.<EntityPlayerMP>emptyList()
            : new ArrayList<>((List<EntityPlayerMP>) s.getConfigurationManager().playerEntityList);
    }

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.START) {
            for (Runnable r; (r = QUEUE.poll()) != null;) r.run();
            return;
        }
        if (++tick % PERIOD_TICKS != 0) return;
        if (AccessRules.permissionNodes()
            .isEmpty()) return;
        for (EntityPlayerMP p : players()) {
            Set<String> g = granted(p);
            Set<String> old;
            synchronized (LAST) {
                old = LAST.put(id(p), g);
            }
            if (!g.equals(old)) {
                if (old != null) refreshQuests(p);
                send(p, g);
            }
        }
    }

    @SubscribeEvent
    public void onDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.player instanceof EntityPlayerMP && AccessRules.hasRules()) refreshQuests((EntityPlayerMP) e.player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (!(e.player instanceof EntityPlayerMP)) return;
        UUID id = id((EntityPlayerMP) e.player);
        WITH_MOD.remove(id);
        synchronized (LAST) {
            LAST.remove(id);
        }
        Permissions.forget(id);
    }
}
