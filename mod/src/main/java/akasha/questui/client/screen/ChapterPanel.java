package akasha.questui.client.screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.lwjgl.opengl.GL11;

import akasha.questui.Config;
import akasha.questui.access.CategoryRule;
import akasha.questui.client.gfx.Anim;
import akasha.questui.client.gfx.Draw;
import akasha.questui.client.gfx.Palette;
import akasha.questui.client.gfx.Scissor;
import akasha.questui.client.gfx.Textures;
import akasha.questui.client.ui.Lang;
import akasha.questui.client.ui.Widget;
import betterquesting.api2.client.gui.themes.presets.PresetIcon;
import betterquesting.api2.utils.QuestTranslation;

/**
 * Chapter panel (DESIGN §6): pack header, collapsible categories, collapsible groups inside them, chapter rows with
 * state badges.
 * Pinned it takes its own column; unpinned it shrinks to a strip and slides over the map on hover.
 */
public class ChapterPanel extends Widget {

    public static final int HEADER = 18;
    public static final int CATEGORY_ROW = 16;
    public static final int GROUP_ROW = 13;
    public static final int ROW = 12;
    public static final int ICON = 8;
    public static final int INDENT = 8;
    public static final int STRIP = 10;
    public static final int MIN_W = 110;
    private static final int PAD = 4;
    private static final int BADGE = 8;
    private static final long SLIDE_MS = 140;
    private static final long CLOSE_DELAY_MS = 250;

    public interface Host {

        void selectChapter(UUID id);

        void chapterContextMenu(ChapterInfo info, int mx, int my);

        void groupContextMenu(String category, String group, int mx, int my);

        void categoryContextMenu(String category, int mx, int my);

        void headerAddChapter();

        void pinChanged();

        boolean isEditing();

        UUID selectedChapter();

        String packTitle();
    }

    private static final class Row {

        /** Category header when group and info are null. */
        final String category;
        /** Group header when info is null; null for a chapter outside any group. */
        final String group;
        final ChapterInfo info;
        final int y;
        final int h;
        /** Category rows only: progress and badges summed over its chapters. */
        int done, total;
        boolean pending, complete;
        /** Category rows only: access rule and its state for this player (DESIGN §11). */
        CategoryRule rule;
        boolean ruleBanner, ruleHidden;

        Row(String category, String group, ChapterInfo info, int y, int h) {
            this.category = category;
            this.group = group;
            this.info = info;
            this.y = y;
            this.h = h;
        }

        boolean isCategory() {
            return info == null && group == null;
        }
    }

    /** Collapse key of a group: its name, or "category » group" inside a category (groups repeat across them). */
    public static String groupKey(String category, String group) {
        return category.isEmpty() ? group : category + " \u00BB " + group;
    }

    private final Host host;
    private List<ChapterInfo> chapters = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int contentH;
    private double scroll;
    private int fullW = MIN_W;

    // unpinned slide state
    private boolean open;
    private long slideStart;
    private double slideFrom = STRIP, slideTo = STRIP;
    private long leftAt = -1;

    public ChapterPanel(Host host) {
        this.host = host;
    }

    public void setChapters(List<ChapterInfo> list) {
        this.chapters = list;
        rebuildRows();
    }

    public List<ChapterInfo> getChapters() {
        return chapters;
    }

    public ChapterInfo find(UUID id) {
        for (ChapterInfo c : chapters) if (c.id.equals(id)) return c;
        return null;
    }

    /** W_s = clamp(max title + icon + indent + badge + margins, 110, 0.30 W) (DESIGN §6). */
    public int preferredWidth(int screenW) {
        // content-driven: the pack title in the header is ellipsized instead of widening the panel
        int max = 0;
        for (ChapterInfo c : chapters) {
            max = Math.max(
                max,
                Draw.font()
                    .getStringWidth(c.label) + ICON
                    + INDENT
                    + BADGE
                    + 4
                    + 3 * PAD);
        }
        for (ChapterInfo c : chapters) {
            if (!c.group.isEmpty()) max = Math.max(
                max,
                Draw.font()
                    .getStringWidth(QuestTranslation.translate(c.group)) + 10
                    + 2 * PAD);
            if (!c.category.isEmpty()) max = Math.max(
                max,
                Draw.font()
                    .getStringWidth(QuestTranslation.translate(c.category)) + 10
                    + BADGE
                    + 4
                    + 2 * PAD);
        }
        return Math.max(MIN_W, Math.min(max, (int) (0.30 * screenW)));
    }

    public void setFullWidth(int fw) {
        this.fullW = fw;
    }

    /** Current drawn width (animated when unpinned). */
    public int drawnWidth() {
        if (Config.sidebarPinned) return fullW;
        double t = Anim.easeOutCubic((Anim.millis() - slideStart) / (double) SLIDE_MS);
        return (int) Math.round(slideFrom + (slideTo - slideFrom) * t);
    }

    private static Map<String, List<ChapterInfo>> byGroup(List<ChapterInfo> list) {
        Map<String, List<ChapterInfo>> g = new LinkedHashMap<>();
        for (ChapterInfo c : list) g.computeIfAbsent(c.group, k -> new ArrayList<>())
            .add(c);
        return g;
    }

    /**
     * Uncategorized chapters first (no header), then categories in order of first appearance. Inside each level,
     * ungrouped chapters come first, then groups in order of first appearance. Without categories the panel is the
     * same as before they existed.
     */
    private void rebuildRows() {
        rows.clear();
        int yy = 0;
        Map<String, List<ChapterInfo>> cats = new LinkedHashMap<>();
        for (ChapterInfo c : chapters) cats.computeIfAbsent(c.category, k -> new ArrayList<>())
            .add(c);
        List<ChapterInfo> loose = cats.remove("");
        if (loose != null) yy = addGroups("", loose, yy);
        for (Map.Entry<String, List<ChapterInfo>> e : cats.entrySet()) {
            Row head = new Row(e.getKey(), null, null, yy, CATEGORY_ROW);
            head.complete = true;
            for (ChapterInfo c : e.getValue()) {
                head.done += c.done;
                head.total += c.total;
                head.pending |= c.pendingClaim;
                head.complete &= c.allComplete;
                if (c.rule != null) head.rule = c.rule;
                head.ruleBanner |= c.ruleBanner;
                head.ruleHidden |= c.ruleHidden;
            }
            rows.add(head);
            yy += CATEGORY_ROW;
            if (Config.collapsedCategories.contains(e.getKey())) continue;
            yy = addGroups(e.getKey(), e.getValue(), yy);
        }
        contentH = yy + 4;
        clampScroll();
    }

    private int addGroups(String category, List<ChapterInfo> list, int yy) {
        Map<String, List<ChapterInfo>> g = byGroup(list);
        List<ChapterInfo> loose = g.remove("");
        if (loose != null) {
            for (ChapterInfo c : loose) {
                rows.add(new Row(category, null, c, yy, ROW));
                yy += ROW;
            }
        }
        for (Map.Entry<String, List<ChapterInfo>> e : g.entrySet()) {
            rows.add(new Row(category, e.getKey(), null, yy, GROUP_ROW));
            yy += GROUP_ROW;
            if (Config.collapsedGroups.contains(groupKey(category, e.getKey()))) continue;
            for (ChapterInfo c : e.getValue()) {
                rows.add(new Row(category, e.getKey(), c, yy, ROW));
                yy += ROW;
            }
        }
        return yy;
    }

    private int listTop() {
        return y + HEADER;
    }

    private int listH() {
        return h - HEADER;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH - listH())));
    }

    /** Scrolls so the selected chapter row is visible. */
    public void reveal(UUID id) {
        for (Row r : rows) {
            if (r.info != null && r.info.id.equals(id)) {
                if (r.y < scroll) scroll = r.y;
                else if (r.y + r.h > scroll + listH()) scroll = r.y + r.h - listH();
                clampScroll();
                return;
            }
        }
    }

    private void slide(boolean toOpen) {
        if (open == toOpen) return;
        open = toOpen;
        slideFrom = drawnWidth();
        slideTo = toOpen ? fullW : STRIP;
        slideStart = Anim.millis();
    }

    /** True when the point is over the visible part of the panel. */
    public boolean covers(int mx, int my) {
        return mx >= x && mx < x + drawnWidth() && my >= y && my < y + h;
    }

    @Override
    public void drawPanel(int mx, int my, float partialTick) {
        if (!Config.sidebarPinned) {
            boolean over = covers(mx, my);
            if (over) {
                leftAt = -1;
                if (!open) slide(true);
            } else if (open) {
                if (leftAt < 0) leftAt = Anim.millis();
                else if (Anim.millis() - leftAt > CLOSE_DELAY_MS) slide(false);
            }
        }
        int dw = drawnWidth();
        Draw.rect(x, y, x + dw, y + h, Palette.panelBg());
        Draw.rect(x + dw - 1, y, x + dw, y + h, Palette.PANEL_EDGE);
        if (dw <= STRIP + 1) {
            Draw.triangle(x + (dw - 1 - 6) / 2.0, y + h / 2.0 - 3, 6, Draw.RIGHT, Palette.TEXT_2);
            return;
        }

        Scissor.push(x, y, dw - 1, h);
        drawHeader(mx, my, dw, partialTick);
        Scissor.push(x, listTop(), dw - 1, listH());
        GL11.glPushMatrix();
        clampScroll(); // the list height changes with the window
        int top = listTop() - (int) Math.round(scroll);
        UUID sel = host.selectedChapter();
        for (Row r : rows) {
            int ry = top + r.y;
            if (ry + r.h < listTop() || ry > y + h) continue;
            boolean hot = mx >= x && mx < x + dw - 1 && my >= Math.max(ry, listTop()) && my < ry + r.h;
            if (r.isCategory()) drawCategory(r, ry, dw, hot, r.y > 0);
            else if (r.info == null) drawGroup(r, ry, dw, hot);
            else drawChapter(r, ry, dw, hot, r.info.id.equals(sel), partialTick);
        }
        GL11.glPopMatrix();
        Scissor.pop();
        // scroll bar: 2px, proportional handle
        if (contentH > listH()) {
            double frac = listH() / (double) contentH;
            int hh = Math.max(12, (int) (listH() * frac));
            int hy = listTop() + (int) ((listH() - hh) * (scroll / (contentH - listH())));
            Draw.rect(x + dw - 4, hy, x + dw - 2, hy + hh, 0x80FFFFFF);
        }
        Scissor.pop();
    }

    private void drawHeader(int mx, int my, int dw, float pt) {
        Draw.rect(x, y, x + dw - 1, y + HEADER, 0x14FFFFFF);
        Draw.rect(x, y + HEADER - 1, x + dw - 1, y + HEADER, Palette.PANEL_EDGE);
        int right = x + dw - 1 - PAD;
        // pin
        int px = right - 8;
        boolean pinHot = mx >= px - 2 && mx < px + 10 && my >= y && my < y + HEADER;
        Draw.bind(Textures.BADGE_PIN);
        Draw.tex(px, y + 5, px + 8, y + 13, Config.sidebarPinned ? 0xFFFFFFFF : (pinHot ? 0xC0FFFFFF : 0x60FFFFFF));
        right = px - 4;
        if (host.isEditing()) {
            int ax = right - 8;
            boolean hot = mx >= ax - 2 && mx < ax + 10 && my >= y && my < y + HEADER;
            PresetIcon.ICON_NEW.getTexture()
                .drawTexture(ax, y + 5, 8, 8, 0F, pt);
            if (hot) Draw.rect(ax - 1, y + 4, ax + 9, y + 14, 0x30FFFFFF);
            right = ax - 4;
        }
        String title = Draw.ellipsize(host.packTitle(), right - x - PAD);
        Draw.text(title, x + PAD, y + 5, Palette.TEXT, true);
    }

    /**
     * Category header: tinted band, divider above (except at the top), accent title, summed state badge. A category
     * with an access rule gets a 2 px bar in the rule colour; closed for this player (banner, or hidden but listed
     * in edit mode) it is dimmed with a lock.
     */
    private void drawCategory(Row r, int ry, int dw, boolean hot, boolean divider) {
        Draw.rect(x, ry, x + dw - 1, ry + r.h, hot ? Palette.ROW_HOVER : 0x0FFFFFFF);
        if (divider) Draw.rect(x, ry, x + dw - 1, ry + 1, Palette.PANEL_EDGE);
        if (r.rule != null) Draw.rect(x, ry + (divider ? 1 : 0), x + 2, ry + r.h, r.rule.color);
        boolean closed = r.ruleBanner || r.ruleHidden;
        int color = r.ruleHidden ? Palette.TEXT_OFF : (r.ruleBanner ? r.rule.color : Palette.ACCENT);
        boolean collapsed = Config.collapsedCategories.contains(r.category);
        Draw.triangle(x + PAD, ry + 5, 6, collapsed ? Draw.RIGHT : Draw.DOWN, color);
        int bx = x + dw - 1 - PAD - BADGE;
        int by = ry + (r.h - BADGE) / 2;
        Draw.text(
            Draw.ellipsize(QuestTranslation.translate(r.category), bx - 2 - (x + PAD + 9)),
            x + PAD + 9,
            ry + 4,
            color,
            true);
        if (closed) {
            Draw.bind(Textures.BADGE_LOCK);
            Draw.tex(bx, by, bx + BADGE, by + BADGE, 0xFFFFFFFF);
        } else if (r.pending) {
            Draw.bind(Textures.BADGE_ALERT);
            Draw.tex(bx, by, bx + BADGE, by + BADGE, 0xFFFFFFFF);
        } else if (r.complete && r.total > 0) {
            Draw.bind(Textures.BADGE_CHECK);
            Draw.tex(bx, by, bx + BADGE, by + BADGE, 0xFFFFFFFF);
        }
    }

    private void drawGroup(Row r, int ry, int dw, boolean hot) {
        if (hot) Draw.rect(x, ry, x + dw - 1, ry + r.h, Palette.ROW_HOVER);
        boolean collapsed = Config.collapsedGroups.contains(groupKey(r.category, r.group));
        Draw.triangle(x + PAD, ry + 3.5, 6, collapsed ? Draw.RIGHT : Draw.DOWN, Palette.TEXT_2);
        Draw.text(
            Draw.ellipsize(QuestTranslation.translate(r.group), dw - 2 * PAD - 12),
            x + PAD + 9,
            ry + 3,
            Palette.TEXT_GROUP,
            false);
    }

    private void drawChapter(Row r, int ry, int dw, boolean hot, boolean sel, float pt) {
        ChapterInfo c = r.info;
        if (sel) {
            Draw.rect(x, ry, x + dw - 1, ry + r.h, Palette.ROW_SELECTED);
            Draw.rect(x, ry, x + 2, ry + r.h, Palette.ACCENT);
        } else if (hot && !c.locked) {
            Draw.rect(x, ry, x + dw - 1, ry + r.h, Palette.ROW_HOVER);
        }
        int ix = x + PAD + (r.group == null ? 0 : INDENT);
        int iy = ry + (ROW - ICON) / 2;
        c.icon.drawTexture(ix, iy, ICON, ICON, 0F, pt);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (c.locked) Draw.rect(ix, iy, ix + ICON, iy + ICON, 0x8C000000);

        boolean closed = c.locked || c.ruleBanner || c.ruleHidden;
        int color = closed && !sel ? Palette.TEXT_OFF
            : (sel ? 0xFFFFFFFF : (c.allComplete ? Palette.TEXT_DONE : Palette.TEXT_CHAPTER));
        int textX = ix + ICON + 4;
        int maxW = x + dw - 1 - PAD - BADGE - 2 - textX;
        Draw.text(Draw.ellipsize(c.label, maxW), textX, ry + 2, color, false);

        // right badge: rewards waiting > complete > locked
        int bx = x + dw - 1 - PAD - BADGE;
        int by = ry + (ROW - BADGE) / 2;
        if (c.pendingClaim) {
            double s = 1 + 0.08 * (2 * Anim.pulse(1.2) - 1);
            double r2 = BADGE / 2.0 * s;
            Draw.bind(Textures.BADGE_ALERT);
            Draw.tex(bx + 4 - r2, by + 4 - r2, bx + 4 + r2, by + 4 + r2, 0xFFFFFFFF);
        } else if (c.allComplete) {
            Draw.bind(Textures.BADGE_CHECK);
            Draw.tex(bx, by, bx + BADGE, by + BADGE, 0xFFFFFFFF);
        } else if (closed) {
            Draw.bind(Textures.BADGE_LOCK);
            Draw.tex(bx, by, bx + BADGE, by + BADGE, 0xFFFFFFFF);
        }
    }

    private Row rowAt(int mx, int my) {
        if (!covers(mx, my) || my < listTop() || drawnWidth() <= STRIP + 1) return null;
        int ly = my - listTop() + (int) Math.round(scroll);
        for (Row r : rows) if (ly >= r.y && ly < r.y + r.h) return r;
        return null;
    }

    @Override
    public boolean onMouseClick(int mx, int my, int button) {
        if (!covers(mx, my)) return false;
        int dw = drawnWidth();
        if (my < y + HEADER) {
            int px = x + dw - 1 - PAD - 8;
            if (button == 0 && mx >= px - 2 && mx < px + 10) {
                Config.sidebarPinned = !Config.sidebarPinned;
                Config.saveState();
                open = !Config.sidebarPinned;
                slideFrom = slideTo = open ? fullW : STRIP;
                host.pinChanged();
            } else if (button == 0 && host.isEditing() && mx >= px - 14 && mx < px - 2) {
                host.headerAddChapter();
            }
            return true;
        }
        Row r = rowAt(mx, my);
        if (r == null) return true;
        if (r.isCategory()) {
            if (button == 0) {
                if (!Config.collapsedCategories.remove(r.category)) Config.collapsedCategories.add(r.category);
                Config.saveState();
                rebuildRows();
            } else if (button == 1 && host.isEditing()) {
                host.categoryContextMenu(r.category, mx, my);
            }
            return true;
        }
        if (r.info == null) {
            if (button == 0) {
                String key = groupKey(r.category, r.group);
                if (!Config.collapsedGroups.remove(key)) Config.collapsedGroups.add(key);
                Config.saveState();
                rebuildRows();
            } else if (button == 1 && host.isEditing()) {
                host.groupContextMenu(r.category, r.group, mx, my);
            }
            return true;
        }
        if (button == 0 && (!r.info.locked || host.isEditing())) host.selectChapter(r.info.id);
        else if (button == 1 && host.isEditing()) host.chapterContextMenu(r.info, mx, my);
        return true;
    }

    @Override
    public boolean onMouseScroll(int mx, int my, int scroll) {
        if (!covers(mx, my)) return false;
        this.scroll += scroll * ROW * 3;
        clampScroll();
        return true;
    }

    @Override
    public List<String> getTooltip(int mx, int my) {
        if (!covers(mx, my)) return null;
        if (my < y + HEADER) {
            int px = x + drawnWidth() - 1 - PAD - 8;
            if (mx >= px - 2 && mx < px + 10)
                return single(Lang.get(Config.sidebarPinned ? "unpin_panel" : "pin_panel"));
            if (host.isEditing() && mx >= px - 14 && mx < px - 2) return single(Lang.get("new_chapter"));
            int done = 0, total = 0;
            for (ChapterInfo c : chapters) {
                done += c.done;
                total += c.total;
            }
            return single(
                Lang.get("progress_total", done, total, String.format("%.1f", total > 0 ? done * 100.0 / total : 0)));
        }
        Row r = rowAt(mx, my);
        if (r != null && r.isCategory()) {
            List<String> tt = new ArrayList<>();
            tt.add(QuestTranslation.translate(r.category));
            tt.add(
                "§7" + Lang.get(
                    "progress",
                    r.done,
                    r.total,
                    String.format("%.1f", r.total > 0 ? r.done * 100.0 / r.total : 0)));
            if (r.pending) tt.add("§6" + Lang.get("rewards_waiting"));
            if (r.rule != null) {
                if (r.ruleBanner) tt.add("§e" + bannerText(r.rule));
                if (r.ruleHidden) tt.add("§c" + Lang.get("category_hidden_for_you"));
                if (host.isEditing()) tt.addAll(ruleSummary(r.rule));
            }
            return tt;
        }
        if (r == null || r.info == null) return null;
        ChapterInfo c = r.info;
        List<String> tt = new ArrayList<>();
        tt.add(c.name);
        String desc = QuestTranslation.translateQuestLineDescription(c.id, c.line);
        if (desc != null && !desc.isEmpty() && !desc.equals("betterquesting.questline." + c.id + ".desc")) {
            for (String s : Draw.font()
                .listFormattedStringToWidth(desc, 200)) tt.add("§7" + s);
        }
        tt.add("§7" + Lang.get("progress", c.done, c.total, String.format("%.1f", c.percent())));
        if (c.pendingClaim) tt.add("§6" + Lang.get("rewards_waiting"));
        if (c.locked) tt.add("§7" + Lang.get("chapter_locked"));
        if (c.ruleBanner) tt.add("§e" + bannerText(c.rule));
        if (c.ruleHidden) tt.add("§c" + Lang.get("category_hidden_for_you"));
        return tt;
    }

    /** Banner of a rule: its own text, or "Only in dimension(s) ..." (DESIGN §11). */
    public static String bannerText(CategoryRule rule) {
        if (rule == null) return "";
        if (!rule.banner.isEmpty()) return QuestTranslation.translate(rule.banner);
        if (rule.dims.isEmpty() && !rule.exclude.isEmpty()) return Lang.get("category_not_dims", rule.excludeText());
        return Lang.get("category_only_dims", rule.dimsText());
    }

    /** Edit mode: the rule in a few tooltip lines. */
    public static List<String> ruleSummary(CategoryRule rule) {
        List<String> l = new ArrayList<>();
        l.add("§8" + Lang.get("rule_dims", rule.dims.isEmpty() ? Lang.get("rule_any") : rule.dimsText()));
        if (!rule.exclude.isEmpty()) l.add("§8" + Lang.get("rule_exclude", rule.excludeText()));
        l.add("§8" + Lang.get("rule_perm", rule.perm.isEmpty() ? Lang.get("rule_none") : rule.perm));
        l.add("§8" + Lang.get("rule_outside", Lang.get("rule_mode." + rule.outside)));
        return l;
    }

    private static List<String> single(String s) {
        List<String> l = new ArrayList<>();
        l.add(s);
        return l;
    }
}
