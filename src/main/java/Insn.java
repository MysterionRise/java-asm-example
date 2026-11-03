/// The tiny instruction set needed to express a [TrigForm]: just enough JVM `double` arithmetic.
///
/// Each [TrigForm] is a `List<Insn>`. Both bytecode engines ([AsmEngine] and [ClassFileEngine]) walk that list
/// with a pattern-matching `switch` over this sealed hierarchy, so the bytecode of every form is written exactly
/// once and the two libraries are guaranteed to emit the same instruction sequence.
sealed interface Insn {

    /// `DLOAD slot`: push a `double` parameter of `calculation(double x, double y)`.
    ///
    /// The method is an instance method, so slot 0 is `this`; `x` occupies slots 1-2 and `y` slots 3-4
    /// (a `double` takes two local variable slots).
    record LoadArg(int slot) implements Insn {

        /// Members of an interface are implicitly public, so the compact constructor must be too.
        public LoadArg {
            switch (slot) { // JEP 507 (preview in Java 25): a primitive type pattern makes the switch exhaustive
                case 1, 3 -> { }
                case int other -> throw new IllegalArgumentException(
                        "slot " + other + " holds neither x (slot 1) nor y (slot 3)");
            }
        }
    }

    /// `LDC2_W value`: push a `double` constant.
    record Const(double value) implements Insn { }

    /// `INVOKESTATIC java/lang/Math.<mathFunction>(D)D`.
    record Call(String mathFunction) implements Insn { }

    /// `DADD`, `DSUB`, `DMUL`, `DDIV`: pop two doubles, push the result.
    enum Arith implements Insn { ADD, SUB, MUL, DIV }

    /// `DRETURN`.
    enum Return implements Insn { DOUBLE }

    // Shorthands for writing programs, see TrigForm.
    Insn X = new LoadArg(1);
    Insn Y = new LoadArg(3);
    Insn TWO = new Const(2.0d);
    Insn SIN = new Call("sin");
    Insn COS = new Call("cos");
    Insn RET = Return.DOUBLE;
}
