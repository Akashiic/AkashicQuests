package akasha.questui;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import akasha.questui.access.ServerAccess;
import akasha.questui.net.Net;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.relauncher.Side;

/**
 * FTB Quests style interface for BetterQuesting. The interface is client only. On the server the mod enforces the
 * category access rules (dimension and permission, DESIGN §11); it is optional on both sides
 * (acceptableRemoteVersions = "*"): without it on the server the rules are only drawn, not enforced.
 */
@Mod(
    modid = AkashaQuestUI.MODID,
    name = "Akasha Quests UI",
    version = Tags.VERSION,
    dependencies = "required-after:betterquesting",
    acceptableRemoteVersions = "*")
public class AkashaQuestUI {

    public static final String MODID = "akashaquestui";
    public static final Logger LOG = LogManager.getLogger("AkashaQuestUI");

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (FMLCommonHandler.instance()
            .getSide() == Side.CLIENT) Config.load(event.getSuggestedConfigurationFile());
        else Config.loadServer(event.getSuggestedConfigurationFile());
        Net.register();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        ServerAccess access = new ServerAccess();
        FMLCommonHandler.instance()
            .bus()
            .register(access);
        if (FMLCommonHandler.instance()
            .getSide() != Side.CLIENT) return;
        akasha.questui.client.ClientSetup.init();
    }
}
