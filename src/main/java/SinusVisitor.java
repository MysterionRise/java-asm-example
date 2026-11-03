import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Objects;

/// ASM `ClassVisitor` that swaps the body of `calculation(double, double)` for the bytecode of a [TrigForm]
/// and passes every other member through untouched.
final class SinusVisitor extends ClassVisitor {

    private final TrigForm form;

    SinusVisitor(ClassVisitor next, TrigForm form) {
        Objects.requireNonNull(form, "form"); // JEP 513 (final in Java 25): statements before super(...)
        super(Opcodes.ASM9, next);
        this.form = form;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                     String[] exceptions) {
        MethodVisitor target = super.visitMethod(access, name, descriptor, signature, exceptions);
        if (!BytecodeEngine.TARGET_METHOD.equals(name)) {
            return target;
        }
        return new MethodVisitor(Opcodes.ASM9, target) {

            @Override
            public void visitCode() {
                super.visitCode();
                for (Insn insn : form.program()) {
                    emit(insn);
                }
                mv.visitMaxs(0, 0); // COMPUTE_FRAMES recomputes max stack / max locals
                mv.visitEnd();
                mv = null;          // the original body that the reader streams next is silently discarded
            }

            private void emit(Insn insn) {
                switch (insn) {
                    case Insn.LoadArg(int slot) -> mv.visitVarInsn(Opcodes.DLOAD, slot);
                    case Insn.Const(double value) -> mv.visitLdcInsn(value);
                    case Insn.Call(String function) ->
                            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Math", function, "(D)D", false);
                    case Insn.Arith op -> mv.visitInsn(switch (op) {
                        case ADD -> Opcodes.DADD;
                        case SUB -> Opcodes.DSUB;
                        case MUL -> Opcodes.DMUL;
                        case DIV -> Opcodes.DDIV;
                    });
                    case Insn.Return _ -> mv.visitInsn(Opcodes.DRETURN);
                }
            }
        };
    }
}
