# KodeGraph

**KodeGraph** is a static analysis CLI tool for Kotlin projects. It scans Kotlin source files, builds a dependency graph of your classes, and exports it as either a [PlantUML](https://plantuml.com/) diagram or an interactive HTML visualizer — giving you a visual map of your codebase's architecture.

---

## ✨ Features

- 🔍 **Kotlin PSI-based scanning** — parses source files without needing to compile them
- 🧩 **Detects all class types** — `class`, `data class`, `interface`, `enum`, `annotation`, and `BroadcastReceiver`
- 🔗 **Extracts dependencies from:**
    - Constructor parameters
    - `lateinit` properties
    - Property initializers
    - Delegated properties (e.g. `by inject<T>()`, `by viewModels<T>()`)
- 🔁 **Tracks interface implementations**
- 📊 **Exports to PlantUML** (`.puml`) — ready to render as SVG, PNG, etc.
- 🌐 **Exports to Interactive HTML** (`.html`) — a self-contained web app visualizing dependencies with full search, type filtering, package coloring, and detailed inspector panel
- 📦 **Distributed as a self-contained fat JAR**

---

## 🏗️ Project Structure

KodeGraph is a multi-module Gradle project:

- **`model/`** — Data model: `KGraph`, `KGClass`, `KGClassType`, `KGDependency`
- **`engine/`** — Kotlin PSI scanner (`kotlin-compiler-embeddable`)
- **`exporter/`** — Export interfaces + PlantUML and Interactive HTML implementations (with automated TypeScript build toolchain)
- **`core/`** — Main entry point (`KodeGraph`, `AnalysisResult`)
- **`cli/`** — Command-line interface (fat JAR via Shadow plugin)

### Module Responsibilities

| Module     | Responsibility |
|------------|----------------|
| `model`    | Plain data classes representing the graph (nodes and edges) |
| `engine`   | Walks `.kt` files, parses them using Kotlin PSI, and builds a `KGraph` |
| `exporter` | `GraphExporter` interface + `PlantUmlExporter` and `HtmlGraphExporter` implementations (with automated TypeScript build toolchain) |
| `core`     | `KodeGraph.analyze(sourceRoot)` facade returning an `AnalysisResult` |
| `cli`      | `main()` entry point; packages everything into a runnable fat JAR |

---

## 🚀 Usage

### Run the JAR

```bash
java -jar kodeGraph-cli-alpha-0.1.0.jar <source-root> [output-file]
```

| Argument       | Description                                                   |
|----------------|---------------------------------------------------------------|
| `source-root`  | Path to the root directory containing your Kotlin sources     |
| `output-file`  | *(Optional)* Output file path. If it ends in `.html`, outputs an interactive web-based diagram. Defaults to `dependency-graph.puml` |

**Example:**

```bash
java -jar kodeGraph-cli-alpha-0.1.0.jar ./src/main/kotlin output/my-app.puml
```

**Output:**

```text
Scanning Kotlin sources in: /path/to/src/main/kotlin
PlantUML written to: output/my-app.puml
Done in 342 ms
```

### Render the diagram

Use [PlantUML](https://plantuml.com/download) to render the `.puml` file:

```bash
java -jar plantuml.jar output/my-app.puml
```

Or use an online renderer at [https://www.plantuml.com/plantuml](https://www.plantuml.com/plantuml).

---

## 🛠️ Build from Source

### Requirements

- JDK 17+
- No pre-installed Kotlin needed (toolchain is configured)

### Build the fat JAR

```bash
./gradlew :cli:shadowJar
```

The output JAR will be at:

```text
cli/build/libs/kodeGraph-cli-alpha-0.1.0.jar
```

### Build all modules

```bash
./gradlew build
```

---

## 🧱 Tech Stack

| Technology                        | Version   | Usage                            |
|-----------------------------------|-----------|----------------------------------|
| Kotlin                            | 2.2.0     | Language                         |
| JVM                               | 17        | Runtime target                   |
| `kotlin-compiler-embeddable`      | 1.9.22    | PSI-based source parsing         |
| Shadow (Gradle plugin)            | 8.1.1     | Fat JAR packaging                |
| PlantUML                          | —         | Diagram output format            |
| Node.js / npm (Gradle plugin)     | 7.1.0     | Sandboxed TypeScript compilation  |
| TypeScript                        | 5.3.3     | Visualizer script source         |

---

## 📐 Architecture Overview

```
cli
└── core
    ├── engine
    │   └── model
    └── exporter
        └── model
```

---

## 📄 Example Output

```text
@startuml
skinparam linetype ortho
title Dependency Graph

interface "UserRepository" as UserRepository
class "UserService" as UserService
class "UserController" as UserController

class UserService implements UserRepository
UserController --> UserService

@enduml
```

---

## 📝 License

MIT License

Copyright (c) 2026 Linkwolf5

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
