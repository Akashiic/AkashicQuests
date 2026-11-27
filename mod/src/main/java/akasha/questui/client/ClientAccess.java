package akasha.questui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import akasha.questui.access.AccessRules;
import akasha.questui.access.ClientState;
import akasha.questui.client.screen.QuestScreen;
import akasha.questui.net.Net;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;

/**
 * Client side of the category rules (DESIGN §11): tells the server this client runs the mod once per connection,
 * forgets the server's answers on disconnect and refreshes the book when the player changes dimension.
 */
public final class ClientAccess {

    private boolean helloSent;
    private int lastDim = Integer.MIN_VALUE;

    public static void init() {
        AccessRules.clientPlayer = () -> Minecraft.getMinecraft().thePlayer;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.getNetHandler() == null) return;
        if (!helloSent) {
            helloSent = true;
            Net.CHANNEL.sendToServer(new Net.Hello());
        }
        if (mc.thePlayer.dimension != lastDim) {
            boolean first = lastDim == Integer.MIN_VALUE;
            lastDim = mc.thePlayer.dimension;
            if (!first && AccessRules.hasRules()) refreshScreen();
        }
    }

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        helloSent = false;
        lastDim = Integer.MIN_VALUE;
        ClientState.reset();
        AccessRules.invalidate();
    }

    /** Rebuilds the open book (panel and map) after the rules or their answers changed. */
    public static void refreshScreen() {
        Minecraft mc = Minecraft.getMinecraft();
        mc.func_152344_a(() -> {
            GuiScreen s = mc.currentScreen;
            if (s instanceof QuestScreen) ((QuestScreen) s).refreshGui();
        });
    }
}
