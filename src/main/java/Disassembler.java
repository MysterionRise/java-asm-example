import module java.base;

/// Reads class bytes with the Class-File API (JEP 484) to show and to recognise what `calculation` does.
///
/// It does not care which engine produced the bytes, so listings of ASM output and Class-File API output are
/// directly comparable.
final class Disassembler {

    private Disassembler() {
    }

    /// A javap-style listing of the method's instructions, one per line.
    static String listing(byte[] classBytes, String methodName) {
        var out = new StringBuilder();
        for (CodeElement element : code(classBytes, methodName)) {
            String line = switch (element) {
                case LoadInstruction load -> "%-13s // slot %d".formatted(load.opcode(), load.slot());
                case ConstantInstruction constant -> "%-13s %s".formatted(constant.opcode(), constant.constantValue());
                case InvokeInstruction invoke -> "%-13s %s.%s%s".formatted(invoke.opcode(),
                        invoke.owner().name().stringValue(), invoke.name().stringValue(), invoke.type().stringValue());
                case Instruction other -> other.opcode().toString();
                default -> null; // labels, line numbers, local variable tables: not instructions
            };
            if (line != null) {
                out.append("    ").append(line).append('\n');
            }
        }
        return out.toString();
    }

    /// Which [TrigForm] `calculation` currently implements: only [TrigForm#PRODUCT] calls `Math.cos`.
    static TrigForm detect(byte[] classBytes) {
        boolean callsCos = code(classBytes, BytecodeEngine.TARGET_METHOD).elementStream()
                .anyMatch(element -> element instanceof InvokeInstruction invoke
                        && invoke.name().equalsString("cos"));
        return callsCos ? TrigForm.PRODUCT : TrigForm.SUM;
    }

    /// The method's opcodes in order; the tests use it to compare the two engines.
    static List<Opcode> opcodes(byte[] classBytes, String methodName) {
        return code(classBytes, methodName).elementStream()
                .<Opcode>mapMulti((element, sink) -> {
                    if (element instanceof Instruction instruction) {
                        sink.accept(instruction.opcode());
                    }
                })
                .toList();
    }

    private static CodeModel code(byte[] classBytes, String methodName) {
        return ClassFile.of().parse(classBytes).methods().stream()
                .filter(method -> method.methodName().equalsString(methodName))
                .findFirst()
                .flatMap(MethodModel::code)
                .orElseThrow(() -> new IllegalArgumentException("no method '" + methodName + "' with code"));
    }
}
