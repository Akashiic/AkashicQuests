package akasha.questui.asm;

import java.util.Map;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

/**
 * Loading plugin of the category rules (DESIGN §11): registers {@link AccessTransformer}, which adds the access
 * check to BetterQuesting's QuestInstance. Sorted after FML's deobfuscation so Minecraft class names in method
 * descriptors are the readable ones. Plain ASM (shipped with Forge): no Mixin library needed.
 */
@IFMLLoadingPlugin.Name("AkashaQuestUICore")
@IFMLLoadingPlugin.MCVersion("1.7.10")
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.TransformerExclusions("akasha.questui.asm.")
public class AkashaCore implements IFMLLoadingPlugin {

    @Override
    public String[] getASMTransformerClass() {
        return new String[] { AccessTransformer.class.getName() };
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {}

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
