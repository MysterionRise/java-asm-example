import java.util.List;
import java.util.Locale;

/// One library's way of rewriting the body of [#TARGET_METHOD] in `SinusCalculation`.
///
/// There are exactly two: [AsmEngine] (the ASM library) and [ClassFileEngine] (the JDK's `java.lang.classfile`).
/// Both interpret the same [Insn] program of a [TrigForm], so their output only differs in the library that
/// assembled it.
sealed interface BytecodeEngine permits AsmEngine, ClassFileEngine {

    /// The method the engines rewrite: `SinusCalculation.calculation(double, double)`.
    String TARGET_METHOD = "calculation";

    /// Command-line id, `asm` or `classfile`.
    String id();

    /// Returns a transformed copy of `classBytes` whose [#TARGET_METHOD] computes `form`. The input is not modified.
    byte[] rewrite(byte[] classBytes, TrigForm form);

    static BytecodeEngine of(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "asm" -> new AsmEngine();
            case "classfile" -> new ClassFileEngine();
            default -> throw new IllegalArgumentException("unknown engine '" + id + "', expected asm|classfile");
        };
    }

    static List<BytecodeEngine> all() {
        return List.of(new AsmEngine(), new ClassFileEngine());
    }
}
