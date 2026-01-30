# Mal with Java25

## Feature Overview

This project is an implementation of the Mal (Make a Lisp) interpreter using Java 25. It aims to provide a simple and 
educational way to understand how Lisp interpreters work while leveraging the features of the latest Java version.

It supports the core functionalities of Mal, including:

- Basic data types (numbers, strings, lists, etc.)
- Arithmetic operations
- Function definitions and calls
- Macros
- Conditionals and loops
- REPL (Read-Eval-Print Loop) for interactive programming
- Extensibility for adding new features
- Interoperability with Java

Some additional features include:

- Lazy-seq implementation for efficient sequence handling
- Multithreading using `spawn` and `join` based on Java's virtual threads

Next steps:

- Transducers

## Getting Started

To run the Mal interpreter, ensure you have Java 25 installed on your system. The easiest way is using sdkman:

```
sdk install java 25.0.2-tem
```

You can then compile and run the project using make:

```
make clean dist
```

To start the REPL, run:

```
java -jar mal.jar
```
