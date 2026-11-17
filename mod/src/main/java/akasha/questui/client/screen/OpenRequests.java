package akasha.questui.client.screen;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import akasha.questui.AkashaQuestUI;
import akasha.questui.Config;
import akasha.questui.client.map.ChapterModel;
import akasha.questui.client.ui.Lang;
import betterquesting.api.questing.IQuest;
import betterquesting.api.utils.UuidConverter;
import betterquesting.api2.utils.QuestTranslation;
import betterquesting.client.gui2.GuiQuest;
import betterquesting.client.gui2.GuiQuestLines;
import betterquesting.questing.QuestDatabase;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * Opens the quest book on request, at a chapter or a quest:
 * <ul>
 * <li>{@code /akashaquests [target]} - client command typed by the player;</li>
 * <li>a chat message whose top-level component is the translation {@value #TRIGGER_KEY}, sent by the server, e.g.
 * from a CustomNPC dialog option: {@code /tellraw @dp {"translate":"akashaquestui.open","with":["Botania"]}}.
 * The message is not shown. Player chat always arrives wrapped (chat.type.text, emote, announcement), so players
 * cannot send the trigger; only the server (commands, plugins) can.</li>
 * </ul>
 * Target: empty (last chapter), a chapter or quest id (BQ's encoded form or a plain UUID), {@code chapter:name},
 * {@code quest:name}, or a name: chapter first (exact, then partial), then quest (exact). Names ignore case,
 * accents and formatting. The book opens as soon as no other screen is open (the chat closing after the command,
 * the NPC dialog closing after its option), and a request older than {@link #TIMEOUT_MS} is dropped. Locked and
 * hidden chapters and quests stay closed, exactly like clicking in the book.
 */
public final class OpenRequests {

    public static final String TRIGGER_KEY = "akashaquestui.open";
    private static final long TIMEOUT_MS = 10_000;

    private static String pending;
    private static long pendingAt;

    /** Queues a request; the latest one wins. */
    public static void request(String target) {
        pending = target == null ? "" : target.trim();
        pendingAt = Minecraft.getSystemTime();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChat(ClientChatReceivedEvent event) {
        if (!(event.message instanceof ChatComponentTranslation)) return;
        ChatComponentTranslation msg = (ChatComponentTranslation) event.message;
        if (!TRIGGER_KEY.equals(msg.getKey())) return;
        event.setCanceled(true);
        Object[] args = msg.getFormatArgs();
        Object a = args.length > 0 ? args[0] : "";
        request(a instanceof IChatComponent ? ((IChatComponent) a).getUnformattedText() : String.valueOf(a));
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending == null) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (Minecraft.getSystemTime() - pendingAt > TIMEOUT_MS || mc.thePlayer == null) {
            pending = null;
            return;
        }
        if (mc.currentScreen != null) return;
        String target = pending;
        pending = null;
        try {
            open(mc, target);
        } catch (RuntimeException e) {
            AkashaQuestUI.LOG.error("Could not open the quest book for '{}'", target, e);
        }
    }

    private static void open(Minecraft mc, String target) {
        if (!Config.enabled) {
            // the mod's screens are off: BQ's own book, without chapter or quest selection
            mc.displayGuiScreen(new GuiQuestLines(null));
            return;
        }
        String kind = "";
        String text = target;
        int colon = target.indexOf(':');
        if (colon > 0) {
            String prefix = target.substring(0, colon)
                .trim()
                .toLowerCase(Locale.ROOT);
            if (prefix.equals("chapter") || prefix.equals("capitulo") || prefix.equals("quest")) {
                kind = prefix.equals("quest") ? "quest" : "chapter";
                text = target.substring(colon + 1)
                    .trim();
            }
        }
        if (text.isEmpty()) {
            mc.displayGuiScreen(new GuiQuestLines(null));
            return;
        }

        boolean canEdit = ChapterModel.canEdit();
        List<ChapterInfo> chapters = ChapterInfo.collect(canEdit);
        UUID id = parseId(text);
        if (id != null) {
            for (ChapterInfo c : chapters) if (c.id.equals(id)) {
                openChapter(mc, c, canEdit);
                return;
            }
            if (QuestDatabase.INSTANCE.get(id) != null) {
                // the interceptor focuses it, applying the book's visibility rules (hidden quests do not open)
                mc.displayGuiScreen(new GuiQuest(null, id));
                return;
            }
        }

        String key = normalize(text);
        if (!kind.equals("quest")) {
            ChapterInfo partial = null;
            for (ChapterInfo c : chapters) {
                String label = normalize(c.label), name = normalize(c.name);
                if (label.equals(key) || name.equals(key)) {
                    openChapter(mc, c, canEdit);
                    return;
                }
                if (partial == null && (label.contains(key) || name.contains(key))) partial = c;
            }
            if (partial != null) {
                openChapter(mc, partial, canEdit);
                return;
            }
        }
        if (!kind.equals("chapter")) {
            for (Map.Entry<UUID, IQuest> e : QuestDatabase.INSTANCE.entrySet()) {
                if (normalize(QuestTranslation.translateQuestName(e.getKey(), e.getValue())).equals(key)) {
                    mc.displayGuiScreen(new GuiQuest(null, e.getKey()));
                    return;
                }
            }
        }
        tell(mc, Lang.get("open.not_found", target));
        mc.displayGuiScreen(new GuiQuestLines(null));
    }

    private static void openChapter(Minecraft mc, ChapterInfo c, boolean canEdit) {
        if (c.locked && !canEdit) tell(mc, Lang.get("open.locked", c.label));
        else QuestScreen.preselectChapter(c.id);
        mc.displayGuiScreen(new GuiQuestLines(null));
    }

    private static UUID parseId(String s) {
        if (s.contains(" ")) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException ignored) {}
        try {
            return UuidConverter.decodeUuid(s);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Lower case, no accents, no formatting codes, single spaces. */
    static String normalize(String s) {
        String t = EnumChatFormatting.getTextWithoutFormattingCodes(s == null ? "" : s);
        t = Normalizer.normalize(t, Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
        return t.toLowerCase(Locale.ROOT)
            .trim()
            .replaceAll("\\s+", " ");
    }

    private static void tell(Minecraft mc, String text) {
        ChatComponentText msg = new ChatComponentText(text);
        msg.getChatStyle()
            .setColor(EnumChatFormatting.YELLOW);
        mc.thePlayer.addChatMessage(msg);
    }

    /** {@code /akashaquests [target]}: see the class comment. */
    public static final class Command extends CommandBase {

        @Override
        public String getCommandName() {
            return "akashaquests";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
            return "/akashaquests [chapter|quest|chapter:<name>|quest:<name>|<id>]";
        }

        @Override
        public int getRequiredPermissionLevel() {
            return 0;
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
            return true;
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            request(String.join(" ", args));
        }
    }
}
