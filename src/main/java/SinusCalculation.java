import org.objectweb.asm.*;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class SinusCalculation {

    private static final String TRANSFORM_METHOD_NAME = "main";
    private static final String MESSAGE = "Никита - молодец!";

    public static void main(String[] args) throws IOException {
        FileInputStream is = new FileInputStream("/Users/konstantinp/projects/java-asm-example/target/classes/SinusCalculation.class");
//        FileInputStream is = new FileInputStream("SinusCalculation.class");

        ClassReader cr = new ClassReader(is);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        cr.accept(new ClassVisitor(Opcodes.ASM7, cw) {

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if ("MESSAGE".equals(name))
                    value = "Никита - хуй!";
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (name.equals(TRANSFORM_METHOD_NAME)) {
                    return new MethodVisitor(Opcodes.ASM7, methodVisitor) {
                        @Override
                        public void visitLdcInsn(Object value) {
                            if ("Никита - молодец!".equals(value)) value = "Никита - хуй!";
                            super.visitLdcInsn(value);
                        }
                    };
                }
                return methodVisitor;
            }
            //            @Override
//            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
//                System.out.println(name);
//                System.out.println(value);
//                value = "хуй";
//                return super.visitField(access, name, descriptor, signature, value);
//            }

        }, 0);

        System.out.println(MESSAGE);

        FileOutputStream fos = new FileOutputStream("SinusCalculation.class");
        fos.write(cw.toByteArray());
        fos.close();
    }
}
