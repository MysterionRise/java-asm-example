import java.util.List;
import java.util.Locale;
import java.util.random.RandomGenerator;

/// The two mathematically equivalent expressions this demo swaps between, each both as plain Java
/// (the oracle for tests and output) and as the bytecode program ([Insn] list) the engines write into
/// `SinusCalculation.calculation`.
enum TrigForm {

    /// `sin(x) + sin(y)`, the short form.
    SUM("sin(x) + sin(y)",
        List.of(Insn.X, Insn.SIN, Insn.Y, Insn.SIN, Insn.Arith.ADD, Insn.RET)),

    /// `2 * sin((x + y) / 2) * cos((x - y) / 2)`, the sum-to-product identity. This is also, instruction for
    /// instruction, what `javac` compiles for the original source of `SinusCalculation.calculation`.
    PRODUCT("2 * sin((x + y) / 2) * cos((x - y) / 2)",
        List.of(Insn.TWO,
                Insn.X, Insn.Y, Insn.Arith.ADD, Insn.TWO, Insn.Arith.DIV, Insn.SIN,
                Insn.Arith.MUL,
                Insn.X, Insn.Y, Insn.Arith.SUB, Insn.TWO, Insn.Arith.DIV, Insn.COS,
                Insn.Arith.MUL,
                Insn.RET));

    private final String formula;
    private final List<Insn> program;

    TrigForm(String formula, List<Insn> program) {
        this.formula = formula;
        this.program = program;
    }

    String formula() {
        return formula;
    }

    List<Insn> program() {
        return program;
    }

    /// The same expression evaluated by ordinary Java.
    double reference(double x, double y) {
        return switch (this) {
            case SUM -> Math.sin(x) + Math.sin(y);
            case PRODUCT -> 2.0d * Math.sin((x + y) / 2.0d) * Math.cos((x - y) / 2.0d);
        };
    }

    TrigForm other() {
        return this == SUM ? PRODUCT : SUM;
    }

    static TrigForm parse(String text) {
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "sum" -> SUM;
            case "product" -> PRODUCT;
            default -> throw new IllegalArgumentException(
                    "unknown form '" + text + "', expected sum|product|flip|random");
        };
    }

    /// How the next form is chosen (`--form`): a fixed one, the other one (`flip`), or a coin toss (`random`).
    sealed interface Choice {

        record Fixed(TrigForm form) implements Choice { }

        enum Flip implements Choice { INSTANCE }

        enum Random implements Choice { INSTANCE }

        default TrigForm next(TrigForm current) {
            return switch (this) {
                case Fixed(TrigForm form) -> form;
                case Flip _ -> current.other();
                case Random _ -> RandomGenerator.getDefault().nextBoolean() ? SUM : PRODUCT;
            };
        }

        static Choice parse(String text) {
            return switch (text.toLowerCase(Locale.ROOT)) {
                case "flip" -> Flip.INSTANCE;
                case "random" -> Random.INSTANCE;
                default -> new Fixed(TrigForm.parse(text));
            };
        }
    }
}
