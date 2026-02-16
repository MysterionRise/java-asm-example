# java-asm-example

![Java 21](https://img.shields.io/badge/Java-21-blue)
![ASM 9.9.1](https://img.shields.io/badge/ASM-9.9.1-green)
![Build](https://github.com/MysterionRise/java-asm-example/actions/workflows/build.yml/badge.svg)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache_2.0-yellow.svg)](LICENSE)

A self-modifying bytecode demo using the [ASM](https://asm.ow2.io/) library. The program computes a trigonometric expression, then rewrites its own `.class` file with an equivalent implementation — so the next run executes different bytecode.

## Prerequisites

- Java 21+
- Maven 3.6+

## Build & Run

```bash
mvn clean package
java -jar target/java-asm-example-1.0-SNAPSHOT.jar 1.0 2.0
```

Run it again to see the rewritten bytecode in action:

```bash
java -jar target/java-asm-example-1.0-SNAPSHOT.jar 1.0 2.0
```

## How It Works

### SinusCalculation

Entry point. Computes `2*sin((x+y)/2)*cos((x-y)/2)` with the original `calculation()` method, then uses ASM to read its own compiled class file and rewrite the `calculation` method's bytecode with a different (but mathematically equivalent) implementation. The rewritten class is saved back to `target/classes/SinusCalculation.class`.

### SinusVisitor

A `ClassVisitor` that intercepts the `calculation` method and randomly replaces its bytecode with one of two equivalent trigonometric forms:

- `sin(x) + sin(y)`
- `2*sin((x+y)/2)*cos((x-y)/2)`

## License

[Apache License 2.0](LICENSE)
