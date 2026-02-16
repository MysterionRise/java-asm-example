# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build and package
mvn clean package

# Run (requires build first; takes two double args for x and y)
java -jar target/java-asm-example-1.0-SNAPSHOT.jar 1.0 2.0
```

Requires Java 25+ and Maven. No tests exist in this project.

## What This Project Does

Demonstrates Java bytecode manipulation using the ASM library (v9.9.1). The program:

1. **SinusCalculation** — Entry point. Computes `2*sin((x+y)/2)*cos((x-y)/2)` with the original `calculation()` method, then uses ASM to read its own compiled class file and rewrite the `calculation` method's bytecode with a different implementation.
2. **SinusVisitor** — A `ClassVisitor` that intercepts the `calculation` method and randomly replaces its bytecode with one of two equivalent trigonometric forms: `sin(x)+sin(y)` or `2*sin((x+y)/2)*cos((x-y)/2)`.

The rewritten class file is saved back to `target/classes/SinusCalculation.class`, so running the program again uses the modified bytecode.

## Key Details

- Source files are in the default package (no package declaration) under `src/main/java/`.
- ASM API level used is `Opcodes.ASM9`.
- Packaging is `jar` with a manifest (`Main-Class: SinusCalculation`), so `java -jar` works directly.
- `maven-dependency-plugin` copies ASM to `target/dependencies/` and the manifest classpath references it.
