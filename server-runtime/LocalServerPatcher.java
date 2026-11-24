import java.nio.file.*;
import org.objectweb.asm.*;

/** Fail-closed patch against a checksum-pinned upstream class. No gameplay changes. */
public final class LocalServerPatcher {
    public static void main(String[] args) throws Exception {
        byte[] source = Files.readAllBytes(Paths.get(args[0]));
        ClassReader reader = new ClassReader(source);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int[] replacements = {0};
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] errors) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, errors);
                if (!name.equals("configure") || !desc.equals("(II)Lcore/net/NioReactor;")) return mv;
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override public void visitMethodInsn(int op, String owner, String name, String desc, boolean itf) {
                        if (op == Opcodes.INVOKESPECIAL && owner.equals("java/net/InetSocketAddress") &&
                                name.equals("<init>") && desc.equals("(I)V")) {
                            super.visitLdcInsn("127.0.0.1");
                            super.visitInsn(Opcodes.SWAP);
                            desc = "(Ljava/lang/String;I)V";
                            replacements[0]++;
                        }
                        super.visitMethodInsn(op, owner, name, desc, itf);
                    }
                };
            }
        }, 0);
        if (replacements[0] != 1) throw new IllegalStateException("Unexpected upstream bind implementation");
        Files.write(Paths.get(args[1]), writer.toByteArray());
    }
}
