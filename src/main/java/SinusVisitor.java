import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Random;

public class SinusVisitor extends ClassVisitor {

    private static final String TRANSFORM_METHOD_NAME = "calculation";
    private static final Random RANDOM = new Random();

    public SinusVisitor(int api, ClassVisitor classVisitor) {
        super(api, classVisitor);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name.equals(TRANSFORM_METHOD_NAME)) {
            MethodVisitor methodVisitor = cv.visitMethod(access, name, descriptor, signature, exceptions);

            return new MethodVisitor(Opcodes.ASM7, methodVisitor) {

                @Override
                public void visitCode() {
                    // randomly changing method impl
                    if (RANDOM.nextBoolean()) {
                        System.out.println("Changing to sin(x) + sin(y)");
                        mv.visitVarInsn(Opcodes.DLOAD, 1);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "java/lang/Math", "sin",
                                "(D)D", false);
                        mv.visitVarInsn(Opcodes.DLOAD, 3);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "java/lang/Math", "sin",
                                "(D)D", false);
                        mv.visitInsn(Opcodes.DADD);
                        mv.visitInsn(Opcodes.DRETURN);
                        mv.visitEnd();
                    } else {
                        System.out.println("Changing to 2*sin((x+y)/2)*cos((x+y)/2)");
                        mv.visitLdcInsn(2.0);
                        mv.visitVarInsn(Opcodes.DLOAD, 1);
                        mv.visitVarInsn(Opcodes.DLOAD, 3);
                        mv.visitInsn(Opcodes.DADD);
                        mv.visitLdcInsn(2.0);
                        mv.visitInsn(Opcodes.DDIV);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "java/lang/Math", "sin",
                                "(D)D", false);
                        mv.visitInsn(Opcodes.DMUL);
                        mv.visitVarInsn(Opcodes.DLOAD, 1);
                        mv.visitVarInsn(Opcodes.DLOAD, 3);
                        mv.visitInsn(Opcodes.DSUB);
                        mv.visitLdcInsn(2.0);
                        mv.visitInsn(Opcodes.DDIV);
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                "java/lang/Math", "cos",
                                "(D)D", false);
                        mv.visitInsn(Opcodes.DMUL);
                        mv.visitInsn(Opcodes.DRETURN);
                        mv.visitEnd();
                    }
                }
            };
        } else
            return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

}
