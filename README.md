# java-asm-example

![Java 25](https://img.shields.io/badge/Java-25-blue)
![ASM 9.11.1](https://img.shields.io/badge/ASM-9.11.1-green)
![Class-File API](https://img.shields.io/badge/java.lang.classfile-JEP_484-orange)
![Build](https://github.com/MysterionRise/java-asm-example/actions/workflows/build.yml/badge.svg)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-yellow.svg)](LICENSE)

A self-modifying bytecode demo. `SinusCalculation` computes `2·sin((x+y)/2)·cos((x−y)/2)` and then rewrites the
bytecode of its own `calculation()` method into the equivalent `sin(x)+sin(y)` (and back again), either

- **on disk** – the running jar (or `target/classes`) is patched, so the *next* run executes different bytecode, or
- **live** – a Java agent retransforms the class inside the running JVM, round after round, without touching the disk,

using either the classic [ASM](https://asm.ow2.io/) library or the JDK's own Class-File API
(`java.lang.classfile`, Java 24+). A javap-style listing shows exactly what changed.

## Prerequisites

- Java 25+ (two Java 25 *preview* features are used, so every `java` command needs `--enable-preview`)
- Maven 3.6+

## Build & run

```bash
mvn clean verify
java --enable-preview -jar target/java-asm-example-1.0-SNAPSHOT.jar 1.0 2.0 --dump
```

```text
Loaded implementation: PRODUCT  2 * sin((x + y) / 2) * cos((x - y) / 2)
calculation(1.0, 2.0) = 1.7507684116335782
    LDC2_W        2.0
    DLOAD_1       // slot 1
    DLOAD_3       // slot 3
    DADD
    LDC2_W        2.0
    DDIV
    INVOKESTATIC  java/lang/Math.sin(D)D
    DMUL
    DLOAD_1       // slot 1
    DLOAD_3       // slot 3
    DSUB
    LDC2_W        2.0
    DDIV
    INVOKESTATIC  java/lang/Math.cos(D)D
    DMUL
    DRETURN
Rewriting with the asm engine to: SUM  sin(x) + sin(y)
    DLOAD_1       // slot 1
    INVOKESTATIC  java/lang/Math.sin(D)D
    DLOAD_3       // slot 3
    INVOKESTATIC  java/lang/Math.sin(D)D
    DADD
    DRETURN
Wrote /…/target/java-asm-example-1.0-SNAPSHOT.jar!/SinusCalculation.class
Run the same command again: calculation() will then execute the new bytecode.
```

Run it again and the first line reads `Loaded implementation: SUM  sin(x) + sin(y)`: the jar really did rewrite
itself, and this run flips it back. (The result is the same either way; that is the point of the identity.)

### Live mode: hot-swap the method inside the running JVM

```bash
java --enable-preview -jar target/java-asm-example-1.0-SNAPSHOT.jar 1.0 2.0 --mode live --engine classfile
```

```text
Hot-swapping calculation() inside the running JVM with the classfile engine, 4 rounds
round 0  PRODUCT  calculation(1.0, 2.0) = 1.7507684116335782   (bytecode as loaded from disk)
round 1  SUM      calculation(1.0, 2.0) = 1.7507684116335782   (retransformed in memory)
round 2  PRODUCT  calculation(1.0, 2.0) = 1.7507684116335782   (retransformed in memory)
round 3  SUM      calculation(1.0, 2.0) = 1.7507684116335782   (retransformed in memory)
round 4  PRODUCT  calculation(1.0, 2.0) = 1.7507684116335782   (retransformed in memory)
The class file on disk was not modified.
```

`java -jar` starts the agent by itself (`Launcher-Agent-Class` in the manifest). On a plain class path attach it
with `-javaagent:`, the jar doubles as the agent jar:

```bash
java --enable-preview -javaagent:target/java-asm-example-1.0-SNAPSHOT.jar \
     -cp "target/classes:target/dependencies/*" SinusCalculation 1.0 2.0 --mode live
```

### Options

```text
usage: java --enable-preview -jar target/java-asm-example-1.0-SNAPSHOT.jar <x> <y> [options]
  --mode rewrite|live             rewrite the class file on disk (default) or hot-swap in the running JVM
  --engine asm|classfile          bytecode library: ASM (default) or the JDK's java.lang.classfile
  --form sum|product|flip|random  next form: a fixed one, the other one (default), or a coin toss
  --rounds N                      hot-swaps to perform in live mode (default 4)
  --dump                          print a javap-style listing of calculation() before and after
```

## How it works

| Class | Role |
|---|---|
| `SinusCalculation` | Entry point (an instance `main`). Owns the `calculation(double, double)` method that gets rewritten, parses the options and drives the two modes. In rewrite mode it finds where its own class was loaded from and writes the new class file back there: a plain file under `target/classes`, or an entry of the running jar replaced through the zip file system. |
| `TrigForm` | The two forms, `SUM` and `PRODUCT`, each as plain Java (the oracle) and as a list of `Insn`, the bytecode program. `TrigForm.Choice` is how `--form` picks the next one. |
| `Insn` | A sealed six-instruction "assembly language" (`LoadArg`, `Const`, `Call`, `Arith`, `Return`), the single source of truth both engines interpret. |
| `BytecodeEngine` | Sealed interface with exactly two implementations. |
| `AsmEngine` + `SinusVisitor` | The ASM implementation: a `ClassVisitor` whose `MethodVisitor` for `calculation` emits the program in `visitCode()` and then drops the original body. |
| `ClassFileEngine` | The same transformation with `java.lang.classfile`: `ClassTransform.transformingMethodBodies(...)` with a `CodeTransform` that discards every original element and emits the program in `atEnd`. |
| `Disassembler` | Reads either engine's output with the Class-File API: the javap-style listing, "which form is this?" detection, and the opcode list the tests compare. |
| `SinusAgent` | `java.lang.instrument` agent. `retransform(engine, form)` makes the JVM hand the original bytes back to a `ClassFileTransformer`, which returns the rewritten class; the JVM swaps the live method. |

The two engines never share a line of bytecode-writing code, yet the tests assert they produce the identical
instruction sequence from the same `Insn` program.

## Fresh Java on display

| Feature | Where |
|---|---|
| Class-File API, JEP 484 (final, 24) | `ClassFileEngine`, `Disassembler` |
| Module import declarations, JEP 511 (final, 25) | `import module java.base;` in the JDK-only sources |
| Instance main methods, JEP 512 (final, 25) | `void main(String[] args)` in `SinusCalculation`, launched by `java -jar` |
| Flexible constructor bodies, JEP 513 (final, 25) | `SinusVisitor` validates before `super(...)` |
| Markdown documentation comments, JEP 467 (final, 23) | `///` comments throughout |
| Record patterns and pattern matching for `switch`, JEP 440/441 (final, 21) | the `switch (insn)` in both engines, `Disassembler`, `TrigForm.Choice` |
| Unnamed variables and patterns, JEP 456 (final, 22) | `catch (NumberFormatException _)`, `case Insn.Return _` |
| Primitive types in patterns, JEP 507 (**preview**, 25) | `case int other ->` in `Insn.LoadArg`, `case int n when n >= 1` in option parsing |
| Stable values, JEP 502 (**preview**, 25) | `StableValue.of()` holding `Instrumentation` in `SinusAgent`, `StableValue.supplier` for the class bytes |
| `java.lang.instrument` retransformation, `Launcher-Agent-Class` | `SinusAgent`, manifest entries in `pom.xml` |
| Hidden classes and method handles (16) | the tests load rewritten bytes with `Lookup.defineHiddenClass` and call them through a `MethodHandle` |

## Tests

`mvn verify` runs the JUnit 6 suite. For every engine × form combination the rewritten class

- passes both the JDK verifier (`ClassFile.verify`) and ASM's `CheckClassAdapter`,
- is loaded as a hidden class and, for 1 000 seeded random inputs, agrees with the original bytecode and with the
  plain-Java formula to 1e-9,
- is recognised as the requested form, leaves `main` untouched, and can be rewritten again by either engine,
- and the two engines emit exactly the same opcode sequence.

The GitHub Actions workflow then runs the demo itself in all four flavours (jar on disk, next run, live hot-swap,
class directory with `-javaagent`) and greps the output.

## Caveats

- **`--enable-preview` is mandatory**: compile, test and run. Without it the JVM refuses the classes that use the
  preview features: `UnsupportedClassVersionError: Preview features are not enabled for SinusAgent (class file
  version 69.65535). Try running with '--enable-preview'`.
- **Rewriting the running jar works on Linux and macOS**, where the JVM keeps reading the replaced file through its
  open handle. Windows locks the jar; the demo reports the failure and suggests running from `target/classes`
  with `-cp "target/classes;target/dependencies/*"` instead, which works everywhere.
- `mvn clean verify` restores the original bytecode whenever the self-modifications get confusing.

## License

[Apache License 2.0](LICENSE)
