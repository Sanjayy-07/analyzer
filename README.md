# Java Architecture Quality Assessment & Automated Refactoring Analyzer

A developer tool: upload a real Java software project, get a real Java dependency
graph and architecture metrics, a trained ML model's refactoring-risk
prediction, AI-assisted explanation, automatically-generated architecture
alternatives, and (for supported transformations) an actually-refactored,
re-validated Java project you can download.

## Problem Statement & Novelty

Manually untangling a codebase's architecture — finding circular
dependencies, God classes, and risky components — doesn't scale as a
project grows. This tool combines real static analysis, real graph
algorithms, a trained ML classifier, AI-assisted reasoning, and an
automated (where safely supported) refactoring + validation loop into one
pipeline:

```
UPLOAD → SAFE EXTRACTION → PROJECT DISCOVERY → LANGUAGE DETECTION →
SOURCE PARSING → COMMON PROJECT MODEL → DEPENDENCY GRAPH →
ARCHITECTURE METRICS → SMELL DETECTION → ML RISK PREDICTION →
AI REASONING → CANDIDATE GENERATION → USER CONSTRAINTS →
SCORING/RANKING → ACCEPT/REJECT (reject-and-regenerate) →
AUTOMATED REFACTORING → RE-ANALYSIS → BUILD/TEST/STRUCTURAL VALIDATION →
PASS OR RECOVERY → FINAL ZIP + REPORT
```

It is not "just" a chatbot, a dependency graph, an ML classifier, or a
refactoring tool — it's the full loop, with each stage's output feeding the
next as real data, not narration.

## Architecture

Two Maven modules:

- **`core/`** — the analysis/ML/refactoring engine. Pure Java, **zero
  external dependencies** (see "Why no external libraries in core" below).
  This is where almost all of the interesting logic lives, and the only
  part with a CLI entry point (`com.archanalyzer.pipeline.Main`) you can
  run standalone.
- **`webapp/`** — the Servlet + JSP + Spring MVC + Hibernate/JPA + MySQL
  web application, wired directly on top of `core`. See
  `docs/SYLLABUS_MAPPING.md` for exactly which syllabus module maps to
  which file here.

```
com.archanalyzer
├── model        Common Project Model (language-neutral)
├── archive      ZIP/TAR/TAR.GZ extraction (Factory + Adapter patterns)
├── parser       Java parsing via the JDK Compiler Tree API (Java-only analysis)
├── graph        DependencyGraph + BFS/DFS/Tarjan SCC/topo sort/centrality
├── metrics      Architecture Health / Dependency Score / per-component metrics
├── smell        Rule-based architectural smell detection
├── impact       Change Impact Analyzer (graph traversal, not AI guessing)
├── ml           FeatureVector, trained Random Forest + Logistic Regression
│                inference (pure Java), RiskPredictionService
├── optimization ConstraintSet, CandidateGenerator, ScoringEngine,
│                RejectRegenerateManager
├── refactor     RefactoringEngine (real Interface Extraction transformation)
├── validate     Baseline, BuildValidator (real recompilation), Structural/
│                ValidationAggregator
├── report       ReportGenerator (Markdown)
└── pipeline     ProjectDiscovery, ProjectPackager, AnalyzerPipeline (orchestrator), Main (CLI)
```

### Why no external libraries in `core`

Every engine in `core` is built on the JDK standard library alone:

- **Archive extraction** uses `java.util.zip` (ZIP, GZIP) plus a
  hand-written ~150-line POSIX `ustar` TAR reader — genuinely correct for
  those three formats, with **no** JavaParser/commons-compress dependency
  needed.
- **Java parsing** uses the JDK's own Compiler Tree API
  (`com.sun.source.tree` / `com.sun.source.util`, module `jdk.compiler`) —
  the same public API `javac` itself and every annotation processor is
  built on. This means real class/method/field/import extraction and a
  genuine cyclomatic-complexity count with zero external parser library.
- **Graph algorithms, metrics, ML inference** are all plain Java + JDK
  collections.

This was a deliberate choice made in this build environment (no internet
access to download Maven dependencies), and it turned out to double as a
better demonstration of "real, from-scratch" engineering for a coursework
project than depending on a black-box library would have. `webapp/`, by
contrast, *does* use external libraries (Spring, Hibernate, MySQL
connector) because those are the actual syllabus topics (Modules 6 & 7) —
faking Spring/Hibernate by hand would defeat the point.

## Setup & Running

### Prerequisites
- **JDK 17+** (a full JDK, not just a JRE — required for the Compiler Tree
  API and `BuildValidator`'s real recompilation check)
- **Maven 3.8+**
- **MySQL 8+** (only needed for the `webapp` module)

### 1. Build

```bash
mvn -f core/pom.xml install      # builds and installs archanalyzer-core
mvn -f webapp/pom.xml package    # builds the WAR (needs core installed first)
```

Or from the root, since `pom.xml` is a multi-module aggregator:
```bash
mvn install
```

### 2. Run the CLI demo (no database needed)

```bash
cd core
mvn compile
java -cp target/classes com.archanalyzer.pipeline.Main ../sample-demo-project ShopDemo
```

This runs the full pipeline against the bundled `sample-demo-project`
(which contains a deliberate circular dependency and a God class — see
below) and prints the dependency graph findings, ML risk predictions,
candidate architectures, the refactoring that gets applied, validation
results, and where the final ZIP/report were written.

You can also point it at the pre-built `sample-demo-project.zip` to
exercise the real ZIP-extraction path:
```bash
java -cp target/classes com.archanalyzer.pipeline.Main ../sample-demo-project.zip ShopDemo
```

### 3. Run the web application

```bash
mysql -u root -p < webapp/sql/schema.sql
cp .env.example .env   # then edit with your real MySQL credentials
export $(cat .env | xargs)   # or configure these in your servlet container
```
Deploy `webapp/target/archanalyzer.war` to Tomcat 10+ (Jakarta EE 9+
namespace) or run via `mvn tomcat10:run` if you add the Tomcat Maven
plugin. Then visit `http://localhost:8085/archanalyzer/`.

Demo login: `demo` / `demo123` (see `LoginServlet` — replace with real
password hashing before any real deployment).

## Faculty Demonstration Path

1. Log in (`demo` / `demo123`).
2. Go to **Upload Project**, drag in `sample-demo-project.zip`.
3. Dashboard shows: 7 components, a detected circular dependency
   (`OrderService → PaymentService → NotificationService → OrderService`),
   and a God-component smell on `ReportingService` (26 methods).
4. ML Risk table shows each component's LOW/MEDIUM/HIGH prediction with
   confidence and contributing factors, straight from the trained Random
   Forest.
5. Candidate Architectures section shows the ranked strategies generated
   from those exact smells (Dependency Inversion for the cycle, Interface
   Extraction for the God component).
6. The top candidate is auto-applied (Interface Extraction is the only
   fully automated transformation in this build), the project is
   recompiled for real, and Before/After metrics + validation verdict are
   shown.
7. Download the refactored ZIP and the Markdown report from the top nav.

## ML Model: Training & Data

See `dataset-pipeline/train_model.py` — this was **actually run** to
produce the bundled models (`core/src/main/resources/models/*.txt`), not
just described. It:

1. Generates a synthetic-but-structurally-realistic dataset (documented in
   the script's own docstring) — clearly **not** real refactoring history,
   because this build environment has no internet access to fetch one.
2. Splits by synthetic "project" (never mixing one project's components
   across train/test — avoids the leakage the spec warns about).
3. Trains a Logistic Regression baseline and a Random Forest, evaluating
   with precision/recall/F1 per class, balanced accuracy, PR-AUC, and
   Top-20% Recall (the "which components should I look at first" metric).
4. Exports both models to a simple, dependency-free text format the pure-Java
   `RandomForestModel`/`LogisticRegressionModel` classes load and run —
   real tree traversal / real softmax math, not hardcoded numbers.

**To train on real data instead** (do this before treating results as
research-valid): run [RefactoringMiner](https://github.com/tsantalis/RefactoringMiner)
against real Java project histories to get actual historical refactoring
events per component, compute the same 10 features using
`com.archanalyzer.metrics.MetricsEngine` at each historical commit, label
by "was this component refactored within N commits", and swap that in for
`build_synthetic_dataset()` in `train_model.py`. Everything downstream
(the project-level split, training, evaluation, export) is dataset-agnostic
and needs no changes.

## Advanced Java Syllabus Mapping

See **`docs/SYLLABUS_MAPPING.md`** for the full module-by-module mapping
(every topic in your uploaded ISWE313L syllabus → the exact class that
demonstrates it).

## Refactoring Candidate Lifecycle & Rollback Architecture

The pipeline tracks every candidate transformation through an explicit lifecycle to guarantee zero code corruption and deliverable integrity:

### Candidate Statuses
- `PROPOSED`: Candidate generated and scored by `CandidateGenerator` and `ScoringEngine`.
- `Preview Only — Not Applied`: Strategy flagged as preview-only (e.g., Dependency Inversion, Facade Introduction) requiring wider call-site rewrites across the project.
- `ACCEPTED`: Best-ranked auto-appliable candidate selected for automated execution.
- `APPLIED`: Interface extraction transformation successfully executed on the isolated working copy by `RefactoringEngine`.
- `VALIDATED_PASS`: Candidate compiled cleanly and structural metrics improved (e.g., dependency reduction, cycle reduction, health gain). Working copy preserved and packaged into `<projectName>-refactored.zip`.
- `VALIDATED_WARNING`: Candidate compiled cleanly without metric regression, but improvement was flat or build was skipped. Working copy preserved and packaged into `<projectName>-refactored.zip`.
- `VALIDATED_FAIL`: Candidate failed compilation or architecture health regressed. Working copy is immediately rolled back to pristine original source, deliverable is packaged as `<projectName>-original.zip`, report explicitly states `REJECTED & ROLLED BACK`, and recovery candidate is queued.
- `REJECTED`: Candidate rejected due to user constraint violations or validation failure.
- `FAILED`: Candidate encountered an exception during AST transformation; working copy is cleaned and rolled back, and original source is packaged as `<projectName>-original.zip`.

### Deliverable Packaging Matrix
| Execution Outcome | Final Archive Name | Working Copy State | Report Outcome |
| :--- | :--- | :--- | :--- |
| `VALIDATED_PASS` | `<projectName>-refactored.zip` | Refactored Code Preserved | Accepted & Applied |
| `VALIDATED_WARNING` | `<projectName>-refactored.zip` | Refactored Code Preserved | Accepted with Warning |
| `VALIDATED_FAIL` | `<projectName>-original.zip` | Rolled Back to Original | `REJECTED & ROLLED BACK` |
| `FAILED` (I/O or Engine error) | `<projectName>-original.zip` | Rolled Back to Original | Transformation Failed |
| No auto-appliable candidate | `<projectName>-analyzed.zip` | Original Project Untouched | Analysis Only |

## Changelog: Bugs Found and Fixed Through Real Testing

Running the actual CLI pipeline against `sample-demo-project` (on a real
Windows machine with a full JDK — something this build environment
couldn't do) surfaced four genuine bugs, now fixed:

1. **ML confidence always showing an impossible "0%".** With 3 risk
   classes, the winning class's probability can never legitimately be
   below ~33% — seeing 0% for every prediction meant something was
   mathematically broken, not just "a low-confidence model". Root cause:
   inside a `RandomForestClassifier`, each individual tree's own
   `.classes_` attribute holds label-ENCODED INTEGERS (`[0., 1., 2.]`),
   not the original string labels — only the ensemble's `.classes_` has
   the real strings. `dataset-pipeline/train_model.py`'s
   `export_random_forest()` was pairing each tree's leaf probabilities
   against the wrong dictionary keys, so every real class silently stayed
   at its zero-initialized default. Fixed by mapping each tree's local
   encoded index back through the ensemble's `classes_` array before
   writing the leaf probabilities. Verified by reproducing the bug and
   the fix directly against the real generated file before/after.
2. **"Build failed after refactoring" shown even when nothing was
   refactored.** `AnalyzerPipeline` was auto-selecting the top-*scored*
   candidate regardless of whether `RefactoringEngine` could actually
   apply it (only "Interface Extraction" is implemented), then still
   running the build/validate cycle against the *unmodified* project and
   reporting a misleading failure. Fixed: the pipeline now selects the
   best-ranked candidate whose strategy is actually auto-appliable for
   the apply/validate cycle, while still showing the full ranked list
   (including higher-scoring preview-only candidates) for transparency.
3. **The build-validation step itself was failing regardless**, because
   it compiled every `.java` file under the project root — including
   `src/test/java` files that import JUnit, which was never on the
   compiler's classpath. Fixed: `BuildValidator` now compiles only
   `src/main/java` (falling back to the whole tree, minus any `test`
   path segment, for non-Maven-layout projects).
4. **Refactoring working copy and deliverable ZIP leaked rejected changes on validation failure.**
   When candidate validation resulted in `Verdict.FAIL` (e.g. architecture health regression from
   74.6 to 73.8 on `sample-demo-project`), the pipeline marked the candidate `VALIDATED_FAIL` but
   left the modified working copy untouched and unconditionally packaged it as `<projectName>-refactored.zip`.
   Fixed: `AnalyzerPipeline` now reliably wipes and restores `refactoredRoot` from pristine `projectRoot`
   upon validation failure or execution error, packages the pristine project as `<projectName>-original.zip`,
   clearly states `REJECTED & ROLLED BACK` in the Markdown report, and adjusts web UI banners and download links.

## Known Limitations (stated honestly, per this project's own "no fake
functionality" principle)

- **Compiled but not run in this environment — UPDATE: verified by the user.**
  This was built in a sandbox with a JRE but no JDK (`javac` unavailable)
  and no internet access, so it could not be compile-tested here. It has
  since been built with a real `mvn install` on a Windows machine with a
  full JDK: **`archanalyzer-core` (41 source files, zero external
  dependencies) compiled cleanly on the first try.** `archanalyzer-webapp`
  hit exactly one issue — `ConstraintsController` uses Bean Validation
  annotations (`@Valid`, `@Min`, `@Max`) but the `jakarta.validation-api` /
  `hibernate-validator` dependencies were missing from `webapp/pom.xml`;
  this has been fixed (see that file). If you still hit an error after
  pulling the latest `pom.xml`, run `mvn -f webapp/pom.xml clean install`
  to force re-resolution, and report anything else that comes up.
- **RAR / 7Z / ISO are not supported**, by design — implementing correct,
  safe decoders for proprietary compression formats by hand isn't
  realistic, and no external library was available to add in this
  environment. They're detected and rejected with a clear message rather
  than silently failing.
- **Java is the only supported programming language**: The system analyzes
  Java source code (`.java`) and Java project architecture exclusively. Non-Java
  programming languages are excluded from code analysis, while non-source files
  (configs, documentation, build files) are preserved intact. Projects without
  Java source files are rejected with a clear message.
- **Only Interface Extraction is fully auto-applied.** Dependency
  Inversion and Facade Introduction are generated as scored, ranked
  candidates with a concrete change plan, but this build does not
  auto-rewrite their call sites project-wide (that needs full type
  resolution across the whole project, not just a syntax parse, to do
  safely).
- **The ML model is trained on synthetic demo data**, clearly documented
  as such — see the ML section above.
- **The AI/LLM Gateway (PART 14) is architecturally reserved but not
  wired to a live provider** in this codebase — `.env.example` has the
  placeholder config for it. Adding it means an `AiExplanationService`
  class in `core` that sends the same structured summary the
  `ReportGenerator` already assembles (metrics + smells + ML risk +
  constraints) to an LLM API and parses its structured JSON response,
  which is a contained, well-scoped addition given the rest of the
  pipeline.
- **The REST API (`AnalysisApiServlet`) operates on the current session's
  analysis**, not a persisted-by-id lookup across a user's historical
  projects — the JPA entities and DAO patterns needed to extend it to
  `GET /api/projects/{id}/...` for any past project are already in place
  (`ProjectEntity`, `AnalysisEntity`), it's a documented next step rather
  than a missing capability.
- **EJB (Module 5) is intentionally not implemented** — this is a plain
  Servlet/Spring app, not deployed inside a Jakarta EE application server,
  so there's no real EJB container to host it. See
  `docs/SYLLABUS_MAPPING.md` Module 5 for the reasoning.
- **Login uses hardcoded demo credentials** (`demo`/`demo123`) rather than
  a hashed-password database lookup — flagged explicitly in
  `LoginServlet`'s Javadoc as a demo stand-in.

## Testing

- `sample-demo-project/src/test/java/com/demo/shop/OrderServiceTest.java` —
  JUnit 5 tests for the sample project itself.
- The `core` module's `BuildValidator` performs REAL compilation checks as
  part of every pipeline run (not a separate test suite) — this is the
  project's own "does the refactored code still compile" test, exercised
  automatically every time you run the pipeline.
- Recommended additions before treating this as submission-ready: unit
  tests per engine class (`GraphAlgorithmsTest`, `MetricsEngineTest`,
  `SmellDetectorTest`, `RandomForestModelTest` using a tiny hand-computed
  tree, `RefactoringEngineTest` against a fixture class) — the classes are
  small and pure enough that these should be quick to write once you can
  compile-and-run locally.
