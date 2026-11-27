package akasha.questui.asm;

import net.minecraft.launchwrapper.IClassTransformer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Adds, at the head of three methods of {@code betterquesting.questing.QuestInstance} (DESIGN §11):
 *
 * <pre>
 * isUnlocked(UUID)                 if (AccessHooks.denyUuid(this, uuid))     return false;
 * canSubmit(EntityPlayer)          if (AccessHooks.denyPlayer(this, player)) return false;
 * canClaimBasically(EntityPlayer)  if (AccessHooks.denyPlayer(this, player)) return false;
 * </pre>
 *
 * Only a jump to the original first instruction is added, with a same-locals frame, so no frame recomputation
 * (and no class loading) is needed. If a method is missing the class is left untouched and a warning is logged.
 */
public class AccessTransformer implements IClassTransformer, Opcodes {

    private static final Logger LOG = LogManager.getLogger("AkashaQuestUI");
    private static final String TARGET = "betterquesting.questing.QuestInstance";
    private static final String HOOKS = "akasha/questui/access/AccessHooks";
    private static final String QUEST = "Lbetterquesting/api/questing/IQuest;";
    private static final String PLAYER = "Lnet/minecraft/entity/player/EntityPlayer;";

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !TARGET.equals(transformedName)) return bytes;
        try {
            ClassNode cn = new ClassNode();
            new ClassReader(bytes).accept(cn, 0);
            int done = 0;
            for (MethodNode m : cn.methods) {
                if ("isUnlocked".equals(m.name) && "(Ljava/util/UUID;)Z".equals(m.desc)) {
                    guard(m, "denyUuid", "(" + QUEST + "Ljava/util/UUID;)Z");
                    done++;
                } else if (("canSubmit".equals(m.name) || "canClaimBasically".equals(m.name))
                    && ("(" + PLAYER + ")Z").equals(m.desc)) {
                        guard(m, "denyPlayer", "(" + QUEST + PLAYER + ")Z");
                        done++;
                    }
            }
            if (done != 3) {
                LOG.warn("QuestInstance: patched {} of 3 methods; category rules are not enforced", done);
                return bytes;
            }
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            cn.accept(cw);
            LOG.info("QuestInstance patched: category access rules enforced");
            return cw.toByteArray();
        } catch (Throwable t) {
            LOG.error("Could not patch QuestInstance; category rules are not enforced", t);
            return bytes;
        }
    }

    private static AbstractInsnNode firstReal(MethodNode m) {
        for (AbstractInsnNode n = m.instructions.getFirst(); n != null; n = n.getNext()) {
            if (!(n instanceof LabelNode) && !(n instanceof LineNumberNode)) return n;
        }
        return null;
    }

    private static void guard(MethodNode m, String hook, String desc) {
        LabelNode go = new LabelNode();
        InsnList l = new InsnList();
        l.add(new VarInsnNode(ALOAD, 0));
        l.add(new VarInsnNode(ALOAD, 1));
        l.add(new MethodInsnNode(INVOKESTATIC, HOOKS, hook, desc, false));
        l.add(new JumpInsnNode(IFEQ, go));
        l.add(new InsnNode(ICONST_0));
        l.add(new InsnNode(IRETURN));
        l.add(go);
        // a frame already at the method start covers the jump target (same offset); two frames there are invalid
        if (!(firstReal(m) instanceof FrameNode)) l.add(new FrameNode(F_SAME, 0, null, 0, null));
        m.instructions.insert(l);
    }
}
