# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build, run the JUnit suite and package (tests live in src/test/java, default package)
mvn clean verify

# Rewrite mode (default): patches the jar itself, the next run executes the new bytecode
java --enable-preview -jar target/java-asm-example-1.0-SNAPSHOT.jar 1.0 2.0 --dump

# Live mode: the Launcher-Agent-Class agent retransforms calculation() inside the running JVM
java --enable-preview -jar target/java-asm-example-1.0-SNAPSHOT.jar 1.0 2.0 --mode live --engine classfile

# Class-directory variant (agent must be attached explicitly for live mode)
java --enable-preview -javaagent:target/java-asm-example-1.0-SNAPSHOT.jar -cp "target/classes:target/dependencies/*" SinusCalculation 1.0 2.0 --mode live
```

Requires Java 25+ and Maven. `--enable-preview` is required at compile, test and run time (pom sets it for javac
and Surefire) because JEP 507 primitive patterns and JEP 502 StableValue are preview features in Java 25.
Options: `--mode rewrite|live`, `--engine asm|classfile`, `--form sum|product|flip|random`, `--rounds N`, `--dump`.

## What This Project Does

Demonstrates Java bytecode manipulation two ways, ASM (v9.11.1) and the JDK Class-File API (`java.lang.classfile`),
on one method: `SinusCalculation.calculation(double x, double y)`, originally `2*sin((x+y)/2)*cos((x-y)/2)`.

- **SinusCalculation** — Entry point (instance `main`, JEP 512). Parses `Options`, then either `rewriteOnDisk`
  (writes the transformed class back to its code source: a file in `target/classes` or the entry inside the running
  jar via `FileSystems.newFileSystem`) or `hotSwapInMemory` (asks `SinusAgent` to retransform, `--rounds` times).
- **TrigForm** — `SUM` (`sin(x)+sin(y)`) and `PRODUCT` (the original); each carries a formula, a `reference()`
  Java evaluation and a `program()` list of `Insn`. `TrigForm.Choice` (`Fixed`/`Flip`/`Random`) implements `--form`.
- **Insn** — Sealed six-instruction DSL (`LoadArg`, `Const`, `Call`, `Arith`, `Return`), the single source of
  truth both engines interpret with a pattern-matching switch.
- **BytecodeEngine** — Sealed interface, `TARGET_METHOD = "calculation"`; `AsmEngine` (+ `SinusVisitor`, the ASM
  `ClassVisitor`) and `ClassFileEngine` (`ClassTransform.transformingMethodBodies` with a body-replacing
  `CodeTransform`).
- **Disassembler** — Class-File API reader: javap-style `listing`, `detect(bytes)` (PRODUCT iff `Math.cos` is
  called), `opcodes` for tests.
- **SinusAgent** — `java.lang.instrument` agent; manifest has `Launcher-Agent-Class`, `Premain-Class`,
  `Agent-Class`, `Can-Retransform-Classes`. `retransform(engine, form)` sets a target and calls
  `retransformClasses`; the transformer keeps the last produced bytes for `--dump`/detection.

## Key Details

- All sources are in the default package under `src/main/java/` and `src/test/java/`; tests must stay in the
  default package because they define rewritten `SinusCalculation` bytes as hidden classes via
  `MethodHandles.lookup().defineHiddenClass` (same-package rule).
- Do not change the shape of `calculation`: both `Insn` programs assume an instance method with `x` in slot 1 and
  `y` in slot 3; `PRODUCT.program()` is instruction-for-instruction what javac emits for the original body.
- Files that use `import module java.base;` must not use `import org.objectweb.asm.*` on-demand imports
  (`ClassReader`, `Label`, `Opcode(s)` exist in both); ASM files use explicit single-type imports.
- `ClassPrinter` is not public API in JDK 24+; `Disassembler` hand-rolls the listing from `CodeModel` elements.
- `maven-dependency-plugin` copies runtime-scope deps (just ASM) to `target/dependencies/`; the manifest
  `Class-Path` references it, so `java -jar` works directly. Test deps: `asm-util` (CheckClassAdapter), JUnit 6.
- CI (`.github/workflows/build.yml`) runs `mvn clean verify` on Oracle JDK 25, then the demo in all modes and greps
  the output; keep the `Loaded implementation:`, `Rewriting with the … engine to:`, `round N  FORM` and
  `retransformed in memory` lines stable or update the workflow.
- Rewriting the running jar relies on POSIX semantics (Linux/macOS); Windows locks the jar and the demo prints a
  fallback hint.
