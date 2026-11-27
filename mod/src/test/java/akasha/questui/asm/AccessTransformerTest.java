package akasha.questui.asm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.CheckClassAdapter;

/** Patches the real BetterQuesting QuestInstance from the classpath and checks the result. */
public class AccessTransformerTest {

    private static byte[] questInstance() throws Exception {
        try (InputStream in = AccessTransformerTest.class.getClassLoader()
            .getResourceAsStream("betterquesting/questing/QuestInstance.class")) {
            assertNotNull("QuestInstance on the test classpath", in);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) > 0;) out.write(b, 0, n);
            return out.toByteArray();
        }
    }

    @Test
    public void patchesTheThreeChecks() throws Exception {
        byte[] src = questInstance();
        byte[] out = new AccessTransformer()
            .transform("betterquesting.questing.QuestInstance", "betterquesting.questing.QuestInstance", src);
        assertNotSame("class was patched", src, out);

        ClassNode cn = new ClassNode();
        new ClassReader(out).accept(cn, 0);
        int hooked = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("isUnlocked") && !m.name.equals("canSubmit") && !m.name.equals("canClaimBasically"))
                continue;
            AbstractInsnNode n = m.instructions.getFirst();
            while (n instanceof LabelNode || n instanceof LineNumberNode) n = n.getNext();
            // aload0, aload1, invokestatic AccessHooks.*
            MethodInsnNode call = (MethodInsnNode) n.getNext()
                .getNext();
            assertEquals(Opcodes.INVOKESTATIC, call.getOpcode());
            assertEquals("akasha/questui/access/AccessHooks", call.owner);
            hooked++;
        }
        assertEquals(3, hooked);

        // bytecode verification (types, stack, frames)
        StringWriter sw = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(out), getClass().getClassLoader(), false, new PrintWriter(sw));
        assertEquals("", sw.toString());
    }

    @Test
    public void leavesOtherClassesAlone() {
        byte[] b = { 1, 2, 3 };
        assertEquals(b, new AccessTransformer().transform("x", "some.Other", b));
    }
}
