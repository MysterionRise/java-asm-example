import module java.base; // JEP 511 (final in Java 25): one import for java.lang.classfile, java.lang.constant, ...

/// Rewrites `calculation` with the JDK's own Class-File API (`java.lang.classfile`, JEP 484, final in Java 24).
/// No third-party library involved: this is the API the JDK itself uses for lambdas, proxies and `jlink`.
final class ClassFileEngine implements BytecodeEngine {

    private static final ClassDesc MATH = ClassDesc.of("java.lang.Math");
    private static final MethodTypeDesc DOUBLE_TO_DOUBLE =
            MethodTypeDesc.of(ConstantDescs.CD_double, ConstantDescs.CD_double);

    @Override
    public String id() {
        return "classfile";
    }

    @Override
    public byte[] rewrite(byte[] classBytes, TrigForm form) {
        var classFile = ClassFile.of();
        var replaceBody = new CodeTransform() {
            @Override
            public void accept(CodeBuilder builder, CodeElement element) {
                // Drop every element of the original body; labels, line numbers and all.
            }

            @Override
            public void atEnd(CodeBuilder builder) {
                for (Insn insn : form.program()) {
                    emit(builder, insn);
                }
                // Max stack, max locals and stack map frames are computed by the API.
            }
        };
        return classFile.transformClass(classFile.parse(classBytes), ClassTransform.transformingMethodBodies(
                method -> method.methodName().equalsString(TARGET_METHOD), replaceBody));
    }

    private static void emit(CodeBuilder code, Insn insn) {
        switch (insn) {
            case Insn.LoadArg(int slot) -> code.dload(slot);
            case Insn.Const(double value) -> code.loadConstant(value);
            case Insn.Call(String function) -> code.invokestatic(MATH, function, DOUBLE_TO_DOUBLE);
            case Insn.Arith op -> {
                switch (op) {
                    case ADD -> code.dadd();
                    case SUB -> code.dsub();
                    case MUL -> code.dmul();
                    case DIV -> code.ddiv();
                }
            }
            case Insn.Return _ -> code.dreturn();
        }
    }

    @Override
    public String toString() {
        return id();
    }
}
