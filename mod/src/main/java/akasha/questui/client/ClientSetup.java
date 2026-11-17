package akasha.questui.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;

import akasha.questui.client.data.ImageSources;
import akasha.questui.client.gfx.Textures;
import akasha.questui.client.screen.OpenRequests;
import cpw.mods.fml.common.FMLCommonHandler;

/** Client wiring: screen interception, open requests (command, server trigger) and texture (re)loading. */
public final class ClientSetup {

    private ClientSetup() {}

    public static void init() {
        MinecraftForge.EVENT_BUS.register(new ScreenInterceptor());
        MinecraftForge.EVENT_BUS.register(new HudFilter());
        OpenRequests open = new OpenRequests();
        MinecraftForge.EVENT_BUS.register(open); // chat trigger
        FMLCommonHandler.instance()
            .bus()
            .register(open); // client tick
        ClientCommandHandler.instance.registerCommand(new OpenRequests.Command());
        Textures.load();
        ((IReloadableResourceManager) Minecraft.getMinecraft()
            .getResourceManager()).registerReloadListener(new IResourceManagerReloadListener() {

                @Override
                public void onResourceManagerReload(IResourceManager manager) {
                    // generated textures are reloaded by the texture manager; chapter images are cached here
                    ImageSources.clear();
                }
            });
    }
}
