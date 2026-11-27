package akasha.questui.access;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Access rule of a chapter category (DESIGN §11). Stored in BQ's quest settings under {@code akashaui:categories},
 * so it travels with the quest database, the default quests and BQ's settings sync.
 * <p>
 * A category with a rule is open to a player only when all hold: the player is in one of {@link #dims} (empty =
 * any dimension), not in one of {@link #exclude} and has the permission node {@link #perm} (empty = everyone). Without
 * permission the category is
 * hidden; in the wrong dimension it is hidden or shown with a banner ({@link #outside}). Its quests cannot be
 * submitted, completed or claimed while it is closed (enforced on the server by {@link AccessHooks}).
 */
public final class CategoryRule {

    public static final String HIDE = "hide";
    public static final String BANNER = "banner";
    public static final int DEFAULT_COLOR = 0xFFE0A030;

    public final String name;
    public final Set<Integer> dims;
    /** Dimensions where the category does not count (blacklist), checked after {@link #dims}. */
    public final Set<Integer> exclude;
    public final String perm;
    public final String outside;
    public final String banner;
    public final int color;

    public CategoryRule(String name, Set<Integer> dims, Set<Integer> exclude, String perm, String outside,
        String banner, int color) {
        this.name = name == null ? "" : name.trim();
        this.dims = Collections.unmodifiableSet(new LinkedHashSet<>(dims));
        this.exclude = Collections.unmodifiableSet(new LinkedHashSet<>(exclude));
        this.perm = perm == null ? "" : perm.trim();
        this.outside = BANNER.equals(outside) ? BANNER : HIDE;
        this.banner = banner == null ? "" : banner.trim();
        this.color = color == 0 ? DEFAULT_COLOR : color | 0xFF000000;
    }

    public static CategoryRule empty(String name) {
        return new CategoryRule(
            name,
            Collections.<Integer>emptySet(),
            Collections.<Integer>emptySet(),
            "",
            HIDE,
            "",
            DEFAULT_COLOR);
    }

    /** True when the rule restricts nothing (it can be dropped). */
    public boolean isOpen() {
        return dims.isEmpty() && exclude.isEmpty() && perm.isEmpty();
    }

    /** Whitelist (empty = any), then blacklist. */
    public boolean dimensionOk(int dim) {
        return (dims.isEmpty() || dims.contains(dim)) && !exclude.contains(dim);
    }

    public CategoryRule withName(String n) {
        return new CategoryRule(n, dims, exclude, perm, outside, banner, color);
    }

    public CategoryRule withDims(Set<Integer> d) {
        return new CategoryRule(name, d, exclude, perm, outside, banner, color);
    }

    public CategoryRule withExclude(Set<Integer> x) {
        return new CategoryRule(name, dims, x, perm, outside, banner, color);
    }

    public CategoryRule withPerm(String p) {
        return new CategoryRule(name, dims, exclude, p, outside, banner, color);
    }

    public CategoryRule withOutside(String o) {
        return new CategoryRule(name, dims, exclude, perm, o, banner, color);
    }

    public CategoryRule withBanner(String b) {
        return new CategoryRule(name, dims, exclude, perm, outside, b, color);
    }

    /** "7, -1" → {7, -1}; anything that is not an integer is skipped. */
    public static Set<Integer> parseDims(String s) {
        Set<Integer> out = new LinkedHashSet<>();
        if (s == null) return out;
        for (String part : s.split("[,;\\s]+")) {
            if (part.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(part));
            } catch (NumberFormatException ignored) {}
        }
        return out;
    }

    public String dimsText() {
        return join(dims);
    }

    public String excludeText() {
        return join(exclude);
    }

    private static String join(Set<Integer> set) {
        StringBuilder sb = new StringBuilder();
        for (int d : set) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(d);
        }
        return sb.toString();
    }

    public static CategoryRule read(NBTTagCompound tag) {
        return new CategoryRule(
            tag.getString("name"),
            parseDims(tag.getString("dims")),
            parseDims(tag.getString("exclude")),
            tag.getString("perm"),
            tag.getString("outside"),
            tag.getString("banner"),
            tag.hasKey("color") ? tag.getInteger("color") : DEFAULT_COLOR);
    }

    public NBTTagCompound write() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", name);
        if (!dims.isEmpty()) tag.setString("dims", dimsText());
        if (!exclude.isEmpty()) tag.setString("exclude", excludeText());
        if (!perm.isEmpty()) tag.setString("perm", perm);
        tag.setString("outside", outside);
        if (!banner.isEmpty()) tag.setString("banner", banner);
        if (color != DEFAULT_COLOR) tag.setInteger("color", color);
        return tag;
    }
}
