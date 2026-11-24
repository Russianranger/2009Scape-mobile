import jdk.internal.org.objectweb.asm.*;
import java.nio.file.*;
class Patch {
  public static void main(String[] args) throws Exception {
    Path p=Paths.get(args[0]);
    ClassReader r=new ClassReader(Files.readAllBytes(p));
    ClassWriter w=new ClassWriter(r,0);
    int[] edits={0};
    r.accept(new ClassVisitor(Opcodes.ASM7,w) {
      public MethodVisitor visitMethod(int acc,String name,String desc,String sig,String[] exceptions) {
        MethodVisitor m=super.visitMethod(acc,name,desc,sig,exceptions);
        if (name.equals("glDrawElements")) return new MethodVisitor(Opcodes.ASM7,m) {
          public void visitMethodInsn(int op,String owner,String target,String descriptor,boolean itf) {
            if(target.equals("nglDrawElements") && descriptor.equals("(IIIJ)V")) {
              edits[0]++;super.visitMethodInsn(op,"scapeprobe/DrawProbe","draw",descriptor,false);
            } else super.visitMethodInsn(op,owner,target,descriptor,itf);
          }
        };
        return m;
      }
    },0);
    if(edits[0]==0) throw new Exception("No GL draw call patched");
    Files.write(p,w.toByteArray());System.out.println("Patched "+edits[0]+" native dispatch sites in GL11C");
  }
}
