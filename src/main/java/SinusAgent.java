import module java.base;
import module java.instrument;

/// Java agent that hot-swaps `SinusCalculation.calculation` inside the running JVM.
///
/// `java -jar` starts it automatically before `main` because the manifest names it in `Launcher-Agent-Class`;
/// a `java -cp` run can attach it with `-javaagent:<the same jar>`. Nothing is written to disk: every
/// [#retransform] asks the JVM to re-deliver the class's original bytes to the transformer, which hands back
/// a rewritten copy, so the live method flips between the forms as often as you like.
public final class SinusAgent {

    /// What the transformer should produce on the next retransformation.
    private record Target(BytecodeEngine engine, TrigForm form) { }

    /// Set exactly once, by the JVM, when the agent starts (JEP 502 StableValue, preview in Java 25).
    private static final StableValue<Instrumentation> INSTRUMENTATION = StableValue.of();

    private static volatile Target target;
    private static volatile byte[] lastTransformed;
    private static volatile RuntimeException lastFailure;

    private SinusAgent() {
    }

    /// Entry point for `Launcher-Agent-Class` (executable jar) and `Agent-Class` (dynamic attach).
    public static void agentmain(String args, Instrumentation instrumentation) {
        if (INSTRUMENTATION.trySet(instrumentation)) {
            instrumentation.addTransformer(new Transformer(), true);
        }
    }

    /// Entry point for `-javaagent:` (`Premain-Class`).
    public static void premain(String args, Instrumentation instrumentation) {
        agentmain(args, instrumentation);
    }

    static boolean installed() {
        return INSTRUMENTATION.isSet();
    }

    /// Replaces the live body of `calculation` with `form`, assembled by `engine`.
    static void retransform(BytecodeEngine engine, TrigForm form) throws UnmodifiableClassException {
        target = new Target(engine, form);
        lastFailure = null;
        INSTRUMENTATION.orElseThrow().retransformClasses(SinusCalculation.class);
        if (lastFailure != null) { // the JVM swallows transformer exceptions and keeps the old class; surface them
            throw new IllegalStateException("retransformation failed", lastFailure);
        }
    }

    /// The bytes handed to the JVM by the most recent retransformation, if there was one.
    static Optional<byte[]> currentBytes() {
        return Optional.ofNullable(lastTransformed);
    }

    private static final class Transformer implements ClassFileTransformer {

        @Override
        public byte[] transform(Module module, ClassLoader loader, String className, Class<?> classBeingRedefined,
                                ProtectionDomain protectionDomain, byte[] classfileBuffer) {
            Target current = target;
            if (current == null || !"SinusCalculation".equals(className)) {
                return null; // not ours, or the demo has not asked for a swap yet: leave the class as it is
            }
            try {
                byte[] rewritten = current.engine().rewrite(classfileBuffer, current.form());
                lastTransformed = rewritten;
                return rewritten;
            } catch (RuntimeException e) {
                lastFailure = e;
                return null;
            }
        }
    }
}
