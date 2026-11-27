package akasha.questui.access;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import akasha.questui.AkashaQuestUI;
import akasha.questui.Config;

/**
 * Permission nodes on the server (DESIGN §11). On a hybrid server (Thermos, Crucible, KCauldron...) the node is
 * asked to Bukkit, so any permissions plugin (PermissionsEx, LuckPerms, GroupManager...) and its groups decide.
 * Without Bukkit the {@code permissionFallback} option decides: operators only (default), everyone or nobody.
 * Answers are cached per player for {@link #TTL_MS}; {@link #forget} drops them.
 */
public final class Permissions {

    private static final long TTL_MS = 5000;

    private static boolean probed;
    private static Method getPlayer;
    private static Method hasPermission;

    private static final class Entry {

        final boolean value;
        final long at;

        Entry(boolean value, long at) {
            this.value = value;
            this.at = at;
        }
    }

    private static final Map<UUID, Map<String, Entry>> CACHE = new HashMap<>();

    private Permissions() {}

    private static synchronized void probe() {
        if (probed) return;
        probed = true;
        try {
            Class<?> bukkit = Class.forName("org.bukkit.Bukkit");
            getPlayer = bukkit.getMethod("getPlayer", UUID.class);
            hasPermission = Class.forName("org.bukkit.permissions.Permissible")
                .getMethod("hasPermission", String.class);
            AkashaQuestUI.LOG.info("Category permissions: using Bukkit permissions");
        } catch (Throwable t) {
            getPlayer = null;
            hasPermission = null;
            AkashaQuestUI.LOG
                .info("Category permissions: no Bukkit on this server, fallback '{}'", Config.permissionFallback);
        }
    }

    public static boolean bukkitAvailable() {
        probe();
        return getPlayer != null;
    }

    public static synchronized boolean has(EntityPlayerMP player, String node) {
        UUID id = player.getGameProfile()
            .getId();
        if (id == null) return false;
        long now = System.currentTimeMillis();
        Map<String, Entry> m = CACHE.computeIfAbsent(id, k -> new HashMap<>());
        Entry e = m.get(node);
        if (e == null || now - e.at > TTL_MS) {
            e = new Entry(query(player, id, node), now);
            m.put(node, e);
        }
        return e.value;
    }

    public static synchronized void forget(UUID id) {
        CACHE.remove(id);
    }

    private static boolean query(EntityPlayerMP player, UUID id, String node) {
        probe();
        if (getPlayer != null) {
            try {
                Object bp = getPlayer.invoke(null, id);
                if (bp != null) return (Boolean) hasPermission.invoke(bp, node);
            } catch (Throwable t) {
                AkashaQuestUI.LOG.warn("Bukkit permission check failed for {}", node, t);
            }
            return false;
        }
        String fb = Config.permissionFallback;
        if ("all".equals(fb)) return true;
        if ("none".equals(fb)) return false;
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.getConfigurationManager()
            .func_152596_g(player.getGameProfile());
    }
}
