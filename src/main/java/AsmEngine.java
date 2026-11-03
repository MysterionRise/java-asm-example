import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

/// Rewrites `calculation` with the [ASM](https://asm.ow2.io/) library, the classic third-party choice.
///
/// The real work happens in [SinusVisitor]; this class only wires the `ClassReader` to the `ClassWriter`.
/// Passing the reader to the writer lets ASM copy every untouched method verbatim instead of re-encoding it.
final class AsmEngine implements BytecodeEngine {

    @Override
    public String id() {
        return "asm";
    }

    @Override
    public byte[] rewrite(byte[] classBytes, TrigForm form) {
        var reader = new ClassReader(classBytes);
        var writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES);
        reader.accept(new SinusVisitor(writer, form), 0);
        return writer.toByteArray();
    }

    @Override
    public String toString() {
        return id();
    }
}
