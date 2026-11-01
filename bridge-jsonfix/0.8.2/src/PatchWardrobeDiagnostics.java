import java.nio.file.*;
import jdk.internal.org.objectweb.asm.*;
public class PatchWardrobeDiagnostics {
  public static void main(String[] args) throws Exception {
    ClassReader r=new ClassReader(Files.readAllBytes(Path.of(args[0])));
    ClassWriter w=new ClassWriter(r,ClassWriter.COMPUTE_MAXS);
    int[] handler={0},returns={0},lambda={0};
    r.accept(new ClassVisitor(Opcodes.ASM8,w) {
      @Override public MethodVisitor visitMethod(int access,String name,String desc,String sig,String[] exceptions) {
        MethodVisitor out=super.visitMethod(access,name,desc,sig,exceptions);
        if(name.equals("lambda$setCosmetic$3")&&desc.equals("(Ljava/net/http/HttpResponse;)Ljava/lang/Boolean;")) {
          lambda[0]++;
          out.visitCode();out.visitVarInsn(Opcodes.ALOAD,0);
          out.visitMethodInsn(Opcodes.INVOKESTATIC,"online/theaxiom/wardrobe/WardrobeBridgeDiagnostic",
            "accept","(Ljava/net/http/HttpResponse;)Ljava/lang/Boolean;",false);
          out.visitInsn(Opcodes.ARETURN);out.visitMaxs(1,1);out.visitEnd();
          return new MethodVisitor(Opcodes.ASM8){};
        }
        if(!name.equals("setCosmetic"))return out;
        return new MethodVisitor(Opcodes.ASM8,out) {
          @Override public void visitMethodInsn(int op,String owner,String method,String type,boolean iface) {
            if(op==Opcodes.INVOKESTATIC&&owner.equals("java/net/http/HttpResponse$BodyHandlers")
              &&method.equals("discarding")&&type.equals("()Ljava/net/http/HttpResponse$BodyHandler;")) {
              handler[0]++;super.visitMethodInsn(op,owner,"ofString",type,iface);
            }else super.visitMethodInsn(op,owner,method,type,iface);
          }
          @Override public void visitInsn(int op) {
            if(op==Opcodes.ARETURN) {
              returns[0]++;
              super.visitMethodInsn(Opcodes.INVOKESTATIC,
                "online/theaxiom/wardrobe/WardrobeBridgeDiagnostic","observe",
                "(Ljava/util/concurrent/CompletableFuture;)Ljava/util/concurrent/CompletableFuture;",false);
            }
            super.visitInsn(op);
          }
        };
      }
    },0);
    if(handler[0]!=1||returns[0]!=1||lambda[0]!=1) throw new IllegalStateException("Unexpected bridge method pattern");
    Files.write(Path.of(args[1]),w.toByteArray());
    System.out.println("PATCH_OK");
  }
}