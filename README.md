Java Bytecode manipulation (at class file level)


### What it's doing:

Calculating sin(x) + sin(y) and replace the implementation with different variation by using ASM library and bytecode manipulation

### How to run:

```
mvn clean package
java -cp 'target/dependencies/asm-7.2.jar:target/classes/' SinusCalculation 1.0 2.0
```
