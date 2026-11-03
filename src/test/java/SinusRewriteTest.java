import module java.base;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.ClassReader; // single-type import wins over java.lang.classfile.ClassReader
import org.objectweb.asm.util.CheckClassAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Both engines must produce verifiable, numerically equivalent and mutually identical bytecode.
class SinusRewriteTest {

    private static final String METHOD = BytecodeEngine.TARGET_METHOD;
    private static final MethodType CALCULATION_TYPE =
            MethodType.methodType(double.class, double.class, double.class);
    private static final double TOLERANCE = 1e-9;
    private static final int SAMPLES = 1_000;

    /// `SinusCalculation.class` exactly as it sits on the test class path.
    private static final byte[] ORIGINAL = originalBytes();

    static Stream<Arguments> engineAndForm() {
        return BytecodeEngine.all().stream()
                .flatMap(engine -> Stream.of(TrigForm.values()).map(form -> Arguments.of(engine, form)));
    }

    @ParameterizedTest(name = "{0} engine, {1}")
    @MethodSource("engineAndForm")
    void rewrittenClassPassesBothVerifiers(BytecodeEngine engine, TrigForm form) {
        byte[] rewritten = engine.rewrite(ORIGINAL, form);

        assertEquals(List.of(), ClassFile.of().verify(rewritten), "JDK class-file verifier");

        var asmReport = new StringWriter();
        CheckClassAdapter.verify(new ClassReader(rewritten), false, new PrintWriter(asmReport));
        assertEquals("", asmReport.toString(), "ASM CheckClassAdapter");
    }

    @ParameterizedTest(name = "{0} engine, {1}")
    @MethodSource("engineAndForm")
    void rewrittenMethodMatchesOriginalBytecodeAndReferenceFormula(BytecodeEngine engine, TrigForm form)
            throws Throwable {
        MethodHandle original = calculationOf(ORIGINAL);
        MethodHandle rewritten = calculationOf(engine.rewrite(ORIGINAL, form));
        RandomGenerator random = RandomGeneratorFactory.of("L64X128MixRandom").create(42L);

        for (int i = 0; i < SAMPLES; i++) {
            double x = random.nextDouble(-50.0, 50.0);
            double y = random.nextDouble(-50.0, 50.0);
            double actual = (double) rewritten.invokeExact(x, y);
            assertEquals(form.reference(x, y), actual, TOLERANCE, () -> "reference formula at " + x + ", " + y);
            assertEquals((double) original.invokeExact(x, y), actual, TOLERANCE,
                    () -> "original bytecode at " + x + ", " + y);
        }
    }

    @ParameterizedTest(name = "{0} engine, {1}")
    @MethodSource("engineAndForm")
    void rewrittenFormIsRecognisable(BytecodeEngine engine, TrigForm form) {
        assertEquals(form, Disassembler.detect(engine.rewrite(ORIGINAL, form)));
    }

    @ParameterizedTest(name = "{0} engine, {1}")
    @MethodSource("engineAndForm")
    void otherMethodsAreLeftAlone(BytecodeEngine engine, TrigForm form) {
        byte[] rewritten = engine.rewrite(ORIGINAL, form);
        assertEquals(Disassembler.opcodes(ORIGINAL, "main"), Disassembler.opcodes(rewritten, "main"));
    }

    @ParameterizedTest
    @EnumSource(TrigForm.class)
    void bothEnginesEmitTheSameInstructions(TrigForm form) {
        var asm = Disassembler.opcodes(new AsmEngine().rewrite(ORIGINAL, form), METHOD);
        var classFile = Disassembler.opcodes(new ClassFileEngine().rewrite(ORIGINAL, form), METHOD);

        assertEquals(asm, classFile);
        assertEquals(form.program().size(), asm.size(), "one JVM instruction per Insn");
    }

    @Test
    void rewritingCanBeChainedAcrossEnginesAndForms() {
        byte[] bytes = ORIGINAL;
        for (TrigForm form : List.of(TrigForm.SUM, TrigForm.PRODUCT, TrigForm.SUM)) {
            for (BytecodeEngine engine : BytecodeEngine.all()) {
                bytes = engine.rewrite(bytes, form);
                assertEquals(form, Disassembler.detect(bytes));
                assertEquals(List.of(), ClassFile.of().verify(bytes));
            }
        }
    }

    /// Defines `classBytes` as a hidden class (same package as this test, so our lookup may do that) and
    /// returns its `calculation` bound to a fresh instance.
    private static MethodHandle calculationOf(byte[] classBytes) throws Throwable {
        MethodHandles.Lookup lookup = MethodHandles.lookup().defineHiddenClass(classBytes, true);
        Class<?> hidden = lookup.lookupClass();
        Object instance = lookup.findConstructor(hidden, MethodType.methodType(void.class)).invoke();
        return lookup.findVirtual(hidden, METHOD, CALCULATION_TYPE).bindTo(instance);
    }

    private static byte[] originalBytes() {
        try (InputStream in = SinusCalculation.class.getResourceAsStream("SinusCalculation.class")) {
            return Objects.requireNonNull(in, "SinusCalculation.class on the class path").readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
