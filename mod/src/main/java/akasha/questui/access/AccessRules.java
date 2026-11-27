package akasha.questui.access;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import akasha.questui.AkashaQuestUI;
import akasha.questui.client.data.UiProps;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.storage.QuestSettings;

/**
 * Category access rules (DESIGN §11), shared by the server (enforcement) and the client (panel, map, BQ's own
 * quest states). Holds the rules read from BQ's quest settings and an index quest → categories of the chapters
 * that contain it, rebuilt lazily when invalidated and at most every 2 s otherwise.
 * <p>
 * A quest is closed for a player only when <b>every</b> chapter containing it is in a closed category: a quest
 * linked into a Skyblock chapter from the main campaign stays open through the campaign chapter.
 */
public final class AccessRules {

    private static final long MAX_AGE_MS = 2000;

    private static final class Index {

        static final Index EMPTY = new Index(
            Collections.<String, CategoryRule>emptyMap(),
            new IdentityHashMap<IQuest, String[]>());

        final Map<String, CategoryRule> rules;
        /** Quests in at least one ruled category → categories of all chapters containing them. */
        final IdentityHashMap<IQuest, String[]> questCats;

        Index(Map<String, CategoryRule> rules, IdentityHashMap<IQuest, String[]> questCats) {
            this.rules = rules;
            this.questCats = questCats;
        }
    }

    private static volatile Index index = Index.EMPTY;
    private static volatile boolean dirty = true;
    private static volatile long builtAt;

    /** Client side: the local player (set by the client setup, null on a dedicated server). */
    public static volatile Supplier<EntityPlayer> clientPlayer = () -> null;

    private AccessRules() {}

    public static void invalidate() {
        dirty = true;
    }

    private static Index index() {
        Index ix = index;
        if (dirty || System.currentTimeMillis() - builtAt > MAX_AGE_MS) ix = rebuild();
        return ix;
    }

    private static synchronized Index rebuild() {
        if (!dirty && System.currentTimeMillis() - builtAt <= MAX_AGE_MS) return index;
        try {
            Map<String, CategoryRule> rules = readRules();
            IdentityHashMap<IQuest, String[]> qc = new IdentityHashMap<>();
            if (!rules.isEmpty()) {
                Map<IQuest, List<String>> all = new IdentityHashMap<>();
                for (Map.Entry<UUID, IQuestLine> e : QuestLineDatabase.INSTANCE.getOrderedEntries()) {
                    String cat = e.getValue()
                        .getProperty(UiProps.CATEGORY)
                        .trim();
                    for (UUID qid : new ArrayList<>(
                        e.getValue()
                            .keySet())) {
                        IQuest q = QuestDatabase.INSTANCE.get(qid);
                        if (q != null) all.computeIfAbsent(q, k -> new ArrayList<>())
                            .add(cat);
                    }
                }
                for (Map.Entry<IQuest, List<String>> e : all.entrySet()) {
                    for (String c : e.getValue()) {
                        if (rules.containsKey(c)) {
                            qc.put(
                                e.getKey(),
                                e.getValue()
                                    .toArray(new String[0]));
                            break;
                        }
                    }
                }
            }
            index = new Index(rules, qc);
            dirty = false;
            builtAt = System.currentTimeMillis();
        } catch (RuntimeException ex) {
            // a database sync on the other thread (singleplayer): keep the old index and retry on the next call
            AkashaQuestUI.LOG.debug("category index rebuild deferred", ex);
        }
        return index;
    }

    /** Rules from BQ's quest settings, by category name; rules that restrict nothing are dropped. */
    public static Map<String, CategoryRule> readRules() {
        Map<String, CategoryRule> out = new LinkedHashMap<>();
        NBTTagList list = QuestSettings.INSTANCE.getProperty(UiProps.CATEGORY_RULES);
        for (int i = 0; i < list.tagCount(); i++) {
            CategoryRule r = CategoryRule.read(list.getCompoundTagAt(i));
            if (!r.name.isEmpty() && !r.isOpen()) out.put(r.name, r);
        }
        return out;
    }

    /** Rule stored for the category (including open ones, for the editor), or null. */
    public static CategoryRule storedRule(String category) {
        NBTTagList list = QuestSettings.INSTANCE.getProperty(UiProps.CATEGORY_RULES);
        for (int i = 0; i < list.tagCount(); i++) {
            CategoryRule r = CategoryRule.read(list.getCompoundTagAt(i));
            if (r.name.equals(category)) return r;
        }
        return null;
    }

    /** Settings list with the rule for {@code oldName} replaced by {@code rule} (null removes it). */
    public static NBTTagList withRule(String oldName, CategoryRule rule) {
        NBTTagList src = QuestSettings.INSTANCE.getProperty(UiProps.CATEGORY_RULES);
        NBTTagList out = new NBTTagList();
        boolean placed = false;
        for (int i = 0; i < src.tagCount(); i++) {
            NBTTagCompound t = src.getCompoundTagAt(i);
            String n = t.getString("name");
            if (n.equals(oldName) || rule != null && n.equals(rule.name)) {
                if (!placed && rule != null && !rule.isOpen()) out.appendTag(rule.write());
                placed = true;
            } else {
                out.appendTag(t);
            }
        }
        if (!placed && rule != null && !rule.isOpen()) out.appendTag(rule.write());
        return out;
    }

    /** Active rule of a category, or null when it is open to everyone. */
    public static CategoryRule rule(String category) {
        return category == null || category.isEmpty() ? null : index().rules.get(category);
    }

    /** Permission nodes used by any rule. */
    public static Set<String> permissionNodes() {
        Set<String> out = new HashSet<>();
        for (CategoryRule r : index().rules.values()) if (!r.perm.isEmpty()) out.add(r.perm);
        return out;
    }

    public static boolean hasRules() {
        return !index().rules.isEmpty();
    }

    // Evaluation ---------------------------------------------------------------------------------

    public static boolean permissionOk(CategoryRule r, EntityPlayer p) {
        if (r.perm.isEmpty()) return true;
        if (p instanceof EntityPlayerMP) return Permissions.has((EntityPlayerMP) p, r.perm);
        return ClientState.has(r.perm);
    }

    /** The category is open to the player right now (dimension and permission). */
    public static boolean allowed(CategoryRule r, EntityPlayer p) {
        return r == null || r.dimensionOk(p.dimension) && permissionOk(r, p);
    }

    private static boolean editMode() {
        Boolean b = QuestSettings.INSTANCE.getProperty(NativeProps.EDIT_MODE);
        return b != null && b;
    }

    /** True when the quest must be treated as locked for the player (all its chapters are in closed categories). */
    public static boolean denies(IQuest quest, EntityPlayer player) {
        if (quest == null || player == null) return false;
        Index ix = index();
        if (ix.rules.isEmpty()) return false;
        String[] cats = ix.questCats.get(quest);
        if (cats == null || editMode()) return false;
        for (String c : cats) {
            CategoryRule r = ix.rules.get(c);
            if (r == null || allowed(r, player)) return false;
        }
        return true;
    }

    /** Fast check used before resolving a player from a questing UUID. */
    public static boolean mayDeny(IQuest quest) {
        Index ix = index();
        return !ix.rules.isEmpty() && ix.questCats.containsKey(quest);
    }
}
