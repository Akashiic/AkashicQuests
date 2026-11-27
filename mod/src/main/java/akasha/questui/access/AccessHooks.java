package akasha.questui.access;

import java.util.List;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

import betterquesting.api.api.QuestingAPI;
import betterquesting.api.questing.IQuest;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;

/**
 * Entry points patched into BetterQuesting's QuestInstance by {@link akasha.questui.asm.AccessTransformer}
 * (DESIGN §11). Each returns true when the quest must answer "no": {@code isUnlocked}, {@code canSubmit} and
 * {@code canClaimBasically}. That stops BQ's quest cache, task detection, quest completion and reward claims, on
 * both sides (on the client it only changes what is drawn). Never throws into BQ.
 */
public final class AccessHooks {

    /** Set while the client asks BQ what a player could see if the rules allowed it (see {@link #ignoringRules}). */
    private static final ThreadLocal<Boolean> BYPASS = new ThreadLocal<>();

    private AccessHooks() {}

    /**
     * Runs the check with the rules switched off on this thread. The map uses it for visibility: a chapter shown with
     * a banner draws its quests locked, not missing (the lock itself still comes from the rules).
     */
    public static boolean ignoringRules(java.util.function.BooleanSupplier check) {
        BYPASS.set(Boolean.TRUE);
        try {
            return check.getAsBoolean();
        } finally {
            BYPASS.remove();
        }
    }

    public static boolean denyPlayer(IQuest quest, EntityPlayer player) {
        try {
            if (BYPASS.get() != null) return false;
            return AccessRules.mayDeny(quest) && AccessRules.denies(quest, player);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean denyUuid(IQuest quest, UUID uuid) {
        try {
            if (uuid == null || BYPASS.get() != null || !AccessRules.mayDeny(quest)) return false;
            EntityPlayer p = find(uuid);
            return p != null && AccessRules.denies(quest, p);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Online player with that questing UUID on the calling side; offline players are not restricted. */
    private static EntityPlayer find(UUID uuid) {
        if (FMLCommonHandler.instance()
            .getEffectiveSide() == Side.CLIENT) {
            EntityPlayer p = AccessRules.clientPlayer.get();
            return p != null && uuid.equals(QuestingAPI.getQuestingUUID(p)) ? p : null;
        }
        MinecraftServer server = MinecraftServer.getServer();
        if (server == null) return null;
        @SuppressWarnings("unchecked")
        List<EntityPlayerMP> players = server.getConfigurationManager().playerEntityList;
        for (EntityPlayerMP p : players) if (uuid.equals(QuestingAPI.getQuestingUUID(p))) return p;
        return null;
    }
}
