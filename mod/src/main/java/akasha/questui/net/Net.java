package akasha.questui.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import akasha.questui.AkashaQuestUI;
import akasha.questui.access.AccessRules;
import akasha.questui.access.CategoryRule;
import akasha.questui.access.ClientState;
import akasha.questui.access.ServerAccess;
import akasha.questui.client.data.UiProps;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetSettingSync;
import betterquesting.storage.QuestSettings;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;

/**
 * The mod's own channel (DESIGN §11): the client announces itself, the server sends the permission nodes the player
 * has, and operators edit category rules (BQ's settings packet would only resync the editor).
 */
public final class Net {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(AkashaQuestUI.MODID);

    private Net() {}

    public static void register() {
        CHANNEL.registerMessage(HelloHandler.class, Hello.class, 0, Side.SERVER);
        CHANNEL.registerMessage(PermsHandler.class, Perms.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(EditRuleHandler.class, EditRule.class, 2, Side.SERVER);
    }

    // client → server: "this client runs the mod" ----------------------------------------------------

    public static final class Hello implements IMessage {

        @Override
        public void fromBytes(ByteBuf buf) {}

        @Override
        public void toBytes(ByteBuf buf) {}
    }

    public static final class HelloHandler implements IMessageHandler<Hello, IMessage> {

        @Override
        public IMessage onMessage(Hello msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            ServerAccess.schedule(() -> ServerAccess.hello(p));
            return null;
        }
    }

    // server → client: granted permission nodes -------------------------------------------------------

    public static final class Perms implements IMessage {

        List<String> nodes = new ArrayList<>();

        public Perms() {}

        public Perms(Collection<String> nodes) {
            this.nodes = new ArrayList<>(nodes);
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            int n = buf.readShort();
            nodes = new ArrayList<>();
            for (int i = 0; i < n; i++) nodes.add(ByteBufUtils.readUTF8String(buf));
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeShort(nodes.size());
            for (String s : nodes) ByteBufUtils.writeUTF8String(buf, s);
        }
    }

    public static final class PermsHandler implements IMessageHandler<Perms, IMessage> {

        @Override
        public IMessage onMessage(Perms msg, MessageContext ctx) {
            ClientState.set(msg.nodes);
            AccessRules.invalidate();
            akasha.questui.client.ClientAccess.refreshScreen();
            return null;
        }
    }

    // client → server: an operator edits (or renames, or removes) a category rule -----------------------

    public static final class EditRule implements IMessage {

        String oldName = "";
        /** Empty = remove the rule. */
        NBTTagCompound rule = new NBTTagCompound();

        public EditRule() {}

        public EditRule(String oldName, CategoryRule rule) {
            this.oldName = oldName;
            this.rule = rule == null ? new NBTTagCompound() : rule.write();
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            oldName = ByteBufUtils.readUTF8String(buf);
            rule = ByteBufUtils.readTag(buf);
            if (rule == null) rule = new NBTTagCompound();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeUTF8String(buf, oldName);
            ByteBufUtils.writeTag(buf, rule);
        }
    }

    public static final class EditRuleHandler implements IMessageHandler<EditRule, IMessage> {

        @Override
        public IMessage onMessage(EditRule msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            MinecraftServer server = MinecraftServer.getServer();
            if (server == null || !server.getConfigurationManager()
                .func_152596_g(p.getGameProfile())) {
                AkashaQuestUI.LOG
                    .warn("{} tried to edit a category rule without being an operator", p.getCommandSenderName());
                return null;
            }
            CategoryRule rule = msg.rule.hasKey("name") ? CategoryRule.read(msg.rule) : null;
            String oldName = msg.oldName;
            ServerAccess.schedule(() -> {
                QuestSettings.INSTANCE.setProperty(UiProps.CATEGORY_RULES, AccessRules.withRule(oldName, rule));
                SaveLoadHandler.INSTANCE.markDirty();
                AccessRules.invalidate();
                NetSettingSync.sendSync(null);
                ServerAccess.resyncAll();
            });
            return null;
        }
    }
}
