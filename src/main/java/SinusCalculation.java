import module java.base;

/// Entry point of the demo.
///
/// Computes `2 * sin((x + y) / 2) * cos((x - y) / 2)` with [#calculation], then rewrites that method's bytecode
/// into an equivalent [TrigForm] using a [BytecodeEngine]:
///
/// - `--mode rewrite` (default) writes the new class file back to where this class was loaded from, a directory
///   or the running jar itself, so the *next* run executes the new bytecode;
/// - `--mode live` asks [SinusAgent] to retransform the class inside the running JVM, round after round,
///   without touching the disk.
public class SinusCalculation {

    private static final String CLASS_FILE = "SinusCalculation.class";

    /// This class file as loaded from the class path, read once on first use (JEP 502 StableValue, preview).
    private static final Supplier<byte[]> LOADED_BYTES = StableValue.supplier(SinusCalculation::readOwnClassFile);

    /// The method every engine rewrites. Keep it exactly like this: the [TrigForm] programs assume an instance
    /// method with `x` in local slot 1 and `y` in slot 3.
    private double calculation(double x, double y) {
        return 2.0d * Math.sin((x + y) / 2.0d) * Math.cos((x - y) / 2.0d);
    }

    /// JEP 512 (final in Java 25): an instance `main`, no `public static`, works with `java -jar` as well.
    void main(String[] args) throws Exception {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(Options.USAGE);
            System.exit(2);
            return;
        }
        switch (options.mode()) {
            case REWRITE -> rewriteOnDisk(options);
            case LIVE -> hotSwapInMemory(options);
        }
    }

    private void rewriteOnDisk(Options options) throws URISyntaxException {
        byte[] loaded = LOADED_BYTES.get();
        TrigForm current = Disassembler.detect(loaded);
        IO.println("Loaded implementation: " + describe(current));
        IO.println(result(options));
        if (options.dump()) {
            IO.print(Disassembler.listing(loaded, BytecodeEngine.TARGET_METHOD));
        }

        TrigForm next = options.form().next(current);
        byte[] rewritten = options.engine().rewrite(loaded, next);
        IO.println("Rewriting with the " + options.engine().id() + " engine to: " + describe(next));
        if (options.dump()) {
            IO.print(Disassembler.listing(rewritten, BytecodeEngine.TARGET_METHOD));
        }

        Path codeSource = Path.of(SinusCalculation.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try {
            IO.println("Wrote " + writeBack(codeSource, rewritten));
            IO.println("Run the same command again: calculation() will then execute the new bytecode.");
        } catch (IOException e) {
            // Windows keeps the running jar locked. Linux and macOS let the JVM keep reading the replaced file
            // through its open handle, so there the jar can be swapped underneath the running program.
            IO.println("Could not rewrite " + codeSource + ": " + e);
            IO.println("Run from the class directory instead: java --enable-preview -cp \"target/classes"
                    + File.pathSeparator + "target/dependencies/*\" SinusCalculation " + options.x() + " " + options.y());
        }
    }

    private void hotSwapInMemory(Options options) throws Exception {
        if (!SinusAgent.installed()) {
            System.err.println("""
                    Live mode needs the SinusAgent Java agent. `java -jar` starts it automatically \
                    (Launcher-Agent-Class in the manifest); for a -cp run add \
                    -javaagent:target/java-asm-example-1.0-SNAPSHOT.jar""");
            System.exit(2);
        }
        byte[] loaded = LOADED_BYTES.get();
        TrigForm current = Disassembler.detect(loaded);
        IO.println("Hot-swapping calculation() inside the running JVM with the " + options.engine().id()
                + " engine, " + options.rounds() + " rounds");
        IO.println(round(0, current, options) + "   (bytecode as loaded from disk)");
        if (options.dump()) {
            IO.print(Disassembler.listing(loaded, BytecodeEngine.TARGET_METHOD));
        }
        for (int round = 1; round <= options.rounds(); round++) {
            SinusAgent.retransform(options.engine(), options.form().next(current));
            byte[] live = SinusAgent.currentBytes().orElseThrow();
            current = Disassembler.detect(live);
            IO.println(round(round, current, options) + "   (retransformed in memory)");
            if (options.dump()) {
                IO.print(Disassembler.listing(live, BytecodeEngine.TARGET_METHOD));
            }
        }
        IO.println("The class file on disk was not modified.");
    }

    private String result(Options options) {
        return "calculation(" + options.x() + ", " + options.y() + ") = " + calculation(options.x(), options.y());
    }

    private String round(int round, TrigForm form, Options options) {
        return "round " + round + "  " + "%-8s".formatted(form) + " " + result(options);
    }

    private static String describe(TrigForm form) {
        return form + "  " + form.formula();
    }

    /// Writes the class file back to its origin: a plain file in a directory, or an entry of the (running) jar,
    /// replaced through the zip file system.
    private static String writeBack(Path codeSource, byte[] bytes) throws IOException {
        if (Files.isDirectory(codeSource)) {
            Path file = codeSource.resolve(CLASS_FILE);
            Files.write(file, bytes);
            return file.toString();
        }
        try (FileSystem jar = FileSystems.newFileSystem(codeSource)) {
            Files.write(jar.getPath(CLASS_FILE), bytes);
        }
        return codeSource + "!/" + CLASS_FILE;
    }

    private static byte[] readOwnClassFile() {
        try (InputStream in = SinusCalculation.class.getResourceAsStream(CLASS_FILE)) {
            if (in == null) {
                throw new IllegalStateException(CLASS_FILE + " not found on the class path");
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    enum Mode {
        REWRITE, LIVE;

        static Mode parse(String text) {
            return switch (text.toLowerCase(Locale.ROOT)) {
                case "rewrite" -> REWRITE;
                case "live" -> LIVE;
                default -> throw new IllegalArgumentException("unknown mode '" + text + "', expected rewrite|live");
            };
        }
    }

    /// The parsed command line.
    record Options(double x, double y, Mode mode, BytecodeEngine engine, TrigForm.Choice form, int rounds,
                   boolean dump) {

        static final String USAGE = """
                usage: java --enable-preview -jar target/java-asm-example-1.0-SNAPSHOT.jar <x> <y> [options]
                  --mode rewrite|live             rewrite the class file on disk (default) or hot-swap in the running JVM
                  --engine asm|classfile          bytecode library: ASM (default) or the JDK's java.lang.classfile
                  --form sum|product|flip|random  next form: a fixed one, the other one (default), or a coin toss
                  --rounds N                      hot-swaps to perform in live mode (default 4)
                  --dump                          print a javap-style listing of calculation() before and after""";

        static Options parse(String... args) {
            if (args.length < 2) {
                throw new IllegalArgumentException("Expecting 2 parameters X and Y");
            }
            double x = parseDouble(args[0], "x");
            double y = parseDouble(args[1], "y");
            Mode mode = Mode.REWRITE;
            BytecodeEngine engine = new AsmEngine();
            TrigForm.Choice form = TrigForm.Choice.Flip.INSTANCE;
            int rounds = 4;
            boolean dump = false;
            for (int i = 2; i < args.length; i++) {
                String option = args[i];
                switch (option) {
                    case "--dump" -> dump = true;
                    case "--mode" -> mode = Mode.parse(valueOf(option, args, ++i));
                    case "--engine" -> engine = BytecodeEngine.of(valueOf(option, args, ++i));
                    case "--form" -> form = TrigForm.Choice.parse(valueOf(option, args, ++i));
                    case "--rounds" -> rounds = parseRounds(valueOf(option, args, ++i));
                    default -> throw new IllegalArgumentException("unknown option '" + option + "'");
                }
            }
            return new Options(x, y, mode, engine, form, rounds, dump);
        }

        private static String valueOf(String option, String[] args, int index) {
            if (index >= args.length) {
                throw new IllegalArgumentException(option + " needs a value");
            }
            return args[index];
        }

        private static double parseDouble(String text, String name) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException _) { // JEP 456 (final in Java 22): unnamed variable
                throw new IllegalArgumentException(name + " must be a number, got '" + text + "'");
            }
        }

        private static int parseRounds(String text) {
            int rounds;
            try {
                rounds = Integer.parseInt(text);
            } catch (NumberFormatException _) {
                throw new IllegalArgumentException("--rounds must be a whole number, got '" + text + "'");
            }
            return switch (rounds) { // JEP 507 (preview in Java 25): primitive type patterns with a guard
                case int n when n >= 1 -> n;
                case int n -> throw new IllegalArgumentException("--rounds must be at least 1, got " + n);
            };
        }
    }
}
