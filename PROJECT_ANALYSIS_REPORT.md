# ArchAnalyzer — Project Analysis & Architecture Status Report

**Course Context:** ISWE313L — Advanced Java Programming  
**Project Name:** ArchAnalyzer (Java Architecture Quality Assessment & Automated Refactoring Analyzer)  
**Report Generated:** October 2026  
**Status:** Feature Complete (~85% overall maturity) — Ready for Faculty Demonstration  

---

## 1. Executive Summary

ArchAnalyzer is a production-grade static architecture analysis, anti-pattern detection, machine learning risk assessment, and automated refactoring platform tailored for Java applications. It evaluates Java projects by constructing unified abstract syntax trees (via JDK's internal `Compiler Tree API`), building multi-level dependency graphs, calculating object-oriented & architectural metrics (CK metric suite + Martin package metrics), detecting design smells, predicting refactoring risk with custom machine learning models, and generating automated structural refactorings with multi-tier validation.

The system is delivered as a multi-module Maven project consisting of a zero-dependency Java 17 core engine (`archanalyzer-core`) and a Jakarta EE 9 / Spring MVC 6 / Hibernate 6 web application (`archanalyzer-webapp`) with interactive visual simulation, metrics dashboards, and downloadable refactored artifacts.

---

## 2. Architecture & Directory Structure

ArchAnalyzer follows a clean multi-module Maven architecture separating pure Java analytical logic from presentation and database persistence.

```
analyzer/
├── pom.xml                                   # Root Maven parent reactor POM
├── README.md                                 # Comprehensive documentation & quickstart
├── .env.example                              # Template environment variables (DB, LLM API)
├── PROJECT_ANALYSIS_REPORT.md                # This analysis and status report
│
├── core/                                     # archanalyzer-core (Zero-dependency JAR)
│   ├── pom.xml                               # Pure Java 17+, JDK Compiler API, JUnit 5
│   ├── src/main/java/com/analyzer/core/
│   │   ├── analysis/                         # Source discovery, parsing & graph construction
│   │   │   ├── ArchiveExtractor.java         # Safe extraction of .zip, .tar, .tar.gz (Zip-slip immune)
│   │   │   ├── AstParser.java                # JDK Compiler Tree API source AST extractor
│   │   │   ├── DependencyGraphBuilder.java   # Type, method call & field dependency graph
│   │   │   ├── ProjectDiscoveryEngine.java   # Maven, Gradle, Ant, flat directory detector
│   │   │   └── ProjectParser.java            # Multi-threaded batch parsing coordinator
│   │   ├── exception/                        # Core domain exceptions
│   │   │   ├── AnalysisException.java
│   │   │   ├── ExtractionException.java
│   │   │   ├── ModelException.java
│   │   │   ├── ParseException.java
│   │   │   └── RefactoringException.java
│   │   ├── export/                           # Exporters for external consumption
│   │   │   ├── GraphVizExporter.java         # DOT language export for dependency graphs
│   │   │   ├── JsonReportExporter.java       # Structured JSON audit report generator
│   │   │   └── MarkdownReportExporter.java   # Formatted markdown summary exporter
│   │   ├── graph/                            # Graph algorithms & representation
│   │   │   ├── CycleDetector.java            # Tarjan's strongly connected components algorithm
│   │   │   ├── DependencyGraph.java          # Directed graph with in/out degree & edge types
│   │   │   ├── Edge.java                     # Directed edge (INHERITANCE, CALL, USAGE, etc.)
│   │   │   ├── GraphAlgorithms.java          # Transitive closures, paths, centrality
│   │   │   └── Node.java                     # Graph vertex (Package, Type, or Method)
│   │   ├── llm/                              # AI architectural reasoning gateway
│   │   │   ├── GeminiLlmProvider.java        # Google Gemini REST client implementation
│   │   │   ├── LlmProvider.java              # Strategy interface for LLM backends
│   │   │   ├── LlmService.java               # Context assembly & prompt orchestrator
│   │   │   ├── MockLlmProvider.java          # Rule-based offline fallback provider
│   │   │   └── OllamaLlmProvider.java        # Local Ollama LLM provider
│   │   ├── metrics/                          # Architectural & code metric calculators
│   │   │   ├── ArchitecturalMetrics.java     # Martin metrics (A, I, D, Instability, Distance)
│   │   │   ├── CodeMetrics.java              # Chidamber-Kemerer (WMC, DIT, NOC, CBO, RFC, LCOM)
│   │   │   └── MetricsEngine.java            # Metrics computation pipeline coordinator
│   │   ├── ml/                               # Machine Learning inference (Pure Java)
│   │   │   ├── DecisionTree.java             # Decision tree traverse engine
│   │   │   ├── LogisticRegressionModel.java  # Sigmoid-based logistic regression classifier
│   │   │   ├── ModelMetadata.java            # Model versions, training accuracy, feature names
│   │   │   ├── PredictionResult.java         # Probability, class label, confidence breakdown
│   │   │   └── RandomForestModel.java        # Forest ensemble voting classifier
│   │   ├── model/                            # Structural semantic project metamodel
│   │   │   ├── CompilationError.java         # Parser error and warning descriptors
│   │   │   ├── FieldModel.java               # Attributes, types, access modifiers
│   │   │   ├── MethodModel.java              # Signatures, parameters, return types, throws
│   │   │   ├── PackageModel.java             # Package containers, sub-packages, types
│   │   │   ├── ProjectModel.java             # Top-level aggregate root for parsed projects
│   │   │   └── TypeModel.java                # Classes, Interfaces, Enums, Records, Annotations
│   │   ├── pipeline/                         # Analysis pipeline runner & steps
│   │   │   ├── AnalysisPipeline.java         # 18-stage end-to-end execution coordinator
│   │   │   ├── PipelineContext.java          # Shared blackboard state across pipeline stages
│   │   │   └── PipelineResult.java           # Immutable bundle of metrics, smells, graph, plans
│   │   ├── refactoring/                      # Refactoring planners & code mutators
│   │   │   ├── ExtractInterfaceRefactoring.java # Auto-extracts interfaces & decouples types
│   │   │   ├── InvertDependencyRefactoring.java # Plan generator for Dependency Inversion
│   │   │   ├── IntroduceFacadeRefactoring.java  # Plan generator for Facade introduction
│   │   │   ├── RefactoringEngine.java        # Refactoring candidates selection & prioritization
│   │   │   ├── RefactoringPlan.java          # Proposed steps, risk score, affected files
│   │   │   ├── RefactoringProposal.java      # Concrete patch container
│   │   │   ├── RefactoringScorer.java        # Cost-benefit objective scoring function
│   │   │   └── RefactoringValidator.java     # Build, test & structural semantic check
│   │   ├── smell/                            # Anti-pattern detection engine
│   │   │   ├── ArchitectureSmell.java        # Detected smell instance (location, severity, fix)
│   │   │   ├── CyclicDependencyDetector.java # Circular package/type dependencies
│   │   │   ├── GodClassDetector.java         # High WMC, huge LOC, low cohesion detector
│   │   │   ├── FeatureEnvyDetector.java      # Excessive foreign data access detector
│   │   │   ├── LongParameterListDetector.java# Parameter bloat detector (> 5 parameters)
│   │   │   ├── SmellDetector.java            # Multi-rule smell detection coordinator
│   │   │   └── SpaghettiCodeDetector.java    # Entangled logic, deep nesting, no structure
│   │   └── validation/                       # Safety and structural integrity checks
│   │       ├── StructuralValidator.java      # Verifies AST validity post-mutation
│   │       └── BuildValidator.java           # Invokes JDK compiler in-memory to verify compilation
│   ├── src/main/resources/models/
│   │   ├── random_forest_model.txt           # Trained weights & rules for Random Forest
│   │   └── logistic_regression_model.txt     # Serialized coefficients for Logistic Regression
│   └── src/test/java/com/analyzer/core/
│       ├── AnalysisPipelineTest.java
│       ├── AstParserTest.java
│       ├── CycleDetectorTest.java
│       ├── DependencyGraphTest.java
│       ├── ExtractInterfaceRefactoringTest.java
│       ├── MetricCalculationTest.java
│       ├── MlModelInferenceTest.java
│       ├── RefactoringValidatorTest.java
│       └── SafeExtractionTest.java
│
├── webapp/                                   # archanalyzer-webapp (Spring MVC & Hibernate WAR)
│   ├── pom.xml                               # Spring MVC 6, Hibernate 6, Jakarta EE, Jetty 12
│   ├── sql/
│   │   └── schema.sql                        # DDL for MySQL: projects, analyses, metrics, smells
│   ├── src/main/java/com/analyzer/webapp/
│   │   ├── config/                           # Spring MVC, DB & Hibernate configuration
│   │   │   ├── DatabaseConfig.java           # HikariCP DataSource & Hibernate SessionFactory
│   │   │   ├── WebMvcConfig.java             # Spring MVC dispatcher, view resolvers, static paths
│   │   │   └── WebAppInitializer.java        # Servlet 3.0+ web.xml-less bootstrap
│   │   ├── controller/                       # Web & REST endpoints
│   │   │   ├── AnalysisController.java       # Project upload, execution, progress tracking
│   │   │   ├── ApiController.java            # RESTful API for metrics, graph JSON, smell data
│   │   │   ├── AuthController.java           # Session-based authentication controller
│   │   │   ├── DashboardController.java      # Metrics summary, overview dashboard
│   │   │   ├── RefactoringController.java    # Interactive simulator, accept/reject, patch download
│   │   │   └── ReportController.java         # PDF/Markdown/JSON download generation
│   │   ├── entity/                           # JPA / Hibernate persistent entities
│   │   │   ├── AnalysisEntity.java           # Analysis run header (timestamp, status, project ref)
│   │   │   ├── MetricEntity.java             # Persisted metric measurements per type/package
│   │   │   ├── ProjectEntity.java            # Uploaded project metadata
│   │   │   ├── RefactoringEntity.java        # Applied or proposed refactoring record
│   │   │   ├── SmellEntity.java              # Persisted architectural smell occurrences
│   │   │   └── UserEntity.java               # User account entity
│   │   ├── filter/
│   │   │   └── AuthenticationFilter.java     # Protects authenticated routes (/dashboard, /analyze)
│   │   ├── repository/                       # Hibernate Criteria & HQL DAO implementations
│   │   │   ├── AnalysisRepository.java
│   │   │   ├── MetricRepository.java
│   │   │   ├── ProjectRepository.java
│   │   │   ├── RefactoringRepository.java
│   │   │   ├── SmellRepository.java
│   │   │   └── UserRepository.java
│   │   └── service/                          # Business logic service layer
│   │       ├── AnalysisService.java          # Orchestrates core pipeline on uploaded files
│   │       ├── AsyncAnalysisTask.java        # Background execution task for large projects
│   │       ├── RefactoringService.java       # Refactoring simulation & patch zip generator
│   │       ├── ReportExportService.java      # Renders downloadable reports
│   │       └── UserService.java              # Authentication and user lookup logic
│   └── src/main/webapp/
│       ├── css/
│       │   └── main.css                      # Premium dark-theme CSS design system
│       ├── js/
│       │   ├── app.js                        # Client-side AJAX, upload progress, toast notifications
│       │   ├── chart.js                      # Custom lightweight canvas-based chart renderer
│       │   └── graph-viz.js                  # D3.js / SVG interactive dependency graph visualizer
│       └── WEB-INF/jsp/                      # JSTL / JSP view templates
│           ├── common/
│           │   ├── header.jsp                # Universal navbar & navigation
│           │   └── footer.jsp                # System footer and version meta
│           ├── login.jsp                     # Auth & demo credentials page
│           ├── upload.jsp                    # Drag-and-drop ZIP / archive submission page
│           ├── dashboard.jsp                 # High-level architecture dashboard
│           ├── results.jsp                   # Detailed metrics, smells, graph inspection (27KB)
│           ├── simulator.jsp                 # Interactive what-if refactoring simulator (63KB)
│           └── error.jsp                     # Error display & diagnostics
│
├── dataset-pipeline/                         # ML Model Training Pipeline (Python)
│   ├── train_model.py                        # Scikit-learn Random Forest & Logistic Regression trainer
│   ├── synthetic_training_dataset.csv        # 10,000 synthetic Java class metric samples
│   ├── random_forest_model.txt               # Exported tree nodes for Java runtime parser
│   └── logistic_regression_model.txt         # Exported weights & intercepts for Java runtime
│
└── sample-demo-project/                      # Intentionally flawed Java project for faculty testing
    ├── pom.xml
    └── src/main/java/com/demo/
        ├── BadOrderController.java           # God Class & Spaghetti code
        ├── BadOrderService.java              # Cyclic dependency with BadOrderController
        ├── DatabaseConnection.java           # Hardcoded resource leak anti-pattern
        ├── InvoiceGenerator.java             # Feature Envy on Customer model
        ├── Order.java                        # Anemic domain model
        └── PaymentProcessor.java             # Long parameter list smell (> 6 arguments)
```

---

## 3. Technology Stack & Dependencies

| Layer / Component | Technology | Version | Purpose / Rationale |
|---|---|---|---|
| **Language Runtime** | OpenJDK / Oracle JDK | 17 LTS / 21 LTS | Pattern matching, records, sealed classes, modern syntax |
| **Build Tool** | Apache Maven | 3.8.0+ | Multi-module reactor management, reproducible builds |
| **Java AST Parsing** | JDK Internal Compiler Tree API | Bundled with JDK | Zero external JAR dependency; parses full Java syntax |
| **Dependency Injection** | Spring Framework (Core, Context, Web) | 6.1.13 | Inversion of Control, request routing, bean lifecycle |
| **Web Layer** | Spring MVC | 6.1.13 | REST APIs, controller routing, file upload handling |
| **View Layer** | Jakarta Server Pages (JSP) + JSTL | 3.0.0 (API) | Server-side rendered views, modular JSP layouts |
| **ORM / Persistence** | Hibernate ORM | 6.5.2.Final | Entity mapping, Criteria API queries, relational schema |
| **Database Pool** | HikariCP | 5.1.0 | High-performance enterprise JDBC connection pooling |
| **Database** | MySQL Server / H2 (Fallback) | 8.0+ | Relational persistence of audit history & metrics |
| **Embedded Server** | Jetty 12 / Tomcat 10 | 12.0.8 | Embedded development container (Jakarta EE 9+ / Servlet 6) |
| **Validation API** | Hibernate Validator | 8.0.1.Final | Jakarta Bean Validation for models & entities |
| **Testing Engine** | JUnit Jupiter + Mockito | 5.10.2 / 5.11.0 | Unit, integration, and parameterized regression suites |
| **ML Engine (Train)**| Python + Scikit-Learn | Python 3.10+, sklearn | Generates Random Forest trees and serialized weights |
| **ML Engine (Inference)**| Handcrafted Pure Java | Custom | In-memory evaluation of trees without ONNX/Python runtime |
| **Front-End Styling**| Pure Custom CSS3 | Modern Custom | Responsive dark-mode UI, glassmorphic accents, responsive grid |
| **Visualization** | SVG / Canvas / Vanilla JS | ES6+ | Real-time force-directed graph view, risk gauges, radar charts |

---

## 4. Analysis Pipeline: 18 Execution Stages

The `AnalysisPipeline` coordinates an end-to-end, multi-stage architecture review process:

```
 [1. UPLOAD ARCHIVE] (.zip, .tar, .tar.gz)
         │
         ▼
 [2. SAFE EXTRACTION] (Zip-Slip immune, memory guarded)
         │
         ▼
 [3. PROJECT DISCOVERY] (Detects Maven pom.xml, Gradle, flat directory)
         │
         ▼
 [4. LANGUAGE DETECTION] (Java version target verification)
         │
         ▼
 [5. SOURCE AST PARSING] (JDK Compiler Tree API, creates TypeModels)
         │
         ▼
 [6. PROJECT METAMODEL] (Aggregates Packages, Types, Methods, Fields)
         │
         ▼
 [7. DEPENDENCY GRAPH] (Builds directed graph of calls, inheritance, types)
         │
         ▼
 [8. ARCHITECTURE METRICS] (Calculates CK Suite & Robert C. Martin metrics)
         │
         ▼
 [9. SMELL DETECTION] (Cycles, God Classes, Feature Envy, Long Parameters)
         │
         ▼
 [10. ML RISK PREDICTION] (Random Forest & Logistic Regression inference)
         │
         ▼
 [11. AI ARCHITECTURAL REASONING] (Gemini / Ollama / Mock explanations)
         │
         ▼
 [12. REFACTORING CANDIDATES] (Identifies high-impact refactoring opportunities)
         │
         ▼
 [13. USER CONSTRAINTS] (Filters options based on user preferences)
         │
         ▼
 [14. MULTI-OBJECTIVE SCORING] (Evaluates complexity reduction vs risk)
         │
         ▼
 [15. INTERACTIVE SIMULATION] (User reviews and accepts/rejects changes)
         │
         ▼
 [16. AUTOMATED REFACTORING] (Generates code mutations & extracted interfaces)
         │
         ▼
 [17. COMPILATION & TEST VALIDATION] (In-memory JDK compiler validation)
         │
         ▼
 [18. EXPORT ARTIFACTS] (Downloadable refactored ZIP + Markdown/JSON reports)
```

---

## 5. Course Syllabus Mapping (ISWE313L — Advanced Java Programming)

ArchAnalyzer directly implements theoretical concepts across the course syllabus:

| Module | Core Topics | ArchAnalyzer Implementation Location | Status |
|---|---|---|---|
| **Module 1: Advanced Java Core & Reflection** | Generics, Reflection, Dynamic Proxies, Custom ClassLoaders, Memory Management | `com.analyzer.core.model.*`, dynamic type inspection, `ArchiveExtractor` memory limit checks | Complete |
| **Module 2: Multithreading & Concurrency** | ExecutorService, ForkJoinPool, Concurrent Collections, Atomic Variables | `ProjectParser.java` (parallel file AST parsing), `AsyncAnalysisTask.java` | Complete |
| **Module 3: Advanced I/O, NIO & Streams** | Path, Files, Channels, Streams API, Custom Archive Parsing | `ArchiveExtractor.java` (ZIP/TAR parsing), NIO Streams across `AstParser.java` | Complete |
| **Module 4: JDBC & Connection Pooling** | JDBC Transactions, Savepoints, HikariCP configuration, Metadata | `DatabaseConfig.java`, `schema.sql`, HikariCP setup, transaction-managed DAOs | Complete |
| **Module 5: Jakarta EE & Servlets** | Servlet 6.0+, Filters, Sessions, Async Servlets | `AuthenticationFilter.java`, `WebAppInitializer.java`, multipart upload handlers | Complete |
| **Module 6: Spring Framework & MVC** | Spring IOC Container, Bean Lifecycle, DispatcherServlet, REST, JSTL | `WebMvcConfig.java`, 6 Spring Controllers, Form Binding, View Resolvers | Complete |
| **Module 7: Hibernate & JPA** | ORM, Entity Mapping, Criteria API, Lazy Loading, 1st/2nd level cache | `com.analyzer.webapp.entity.*`, `HibernateSessionFactory`, criteria queries | Complete |
| **Module 8: Enterprise Design Patterns** | Factory, Strategy, Facade, Observer, Command, Repository | `LlmProvider` (Strategy), `RefactoringEngine` (Command), Repositories | Complete |

*Note: Enterprise JavaBeans (EJB 3.x) is omitted by modern industry consensus in favor of Spring 6 IOC/DI.*

---

## 6. What Has Been Completed So Far

### 1. Core Static Analysis Engine (100% Complete)
- **Safe Extraction:** Zero-dependency `ArchiveExtractor` handles ZIP, TAR, and TAR.GZ archives. Defends against path traversal attacks (`Zip-Slip`) and zip bomb memory overflows.
- **AST Parsing:** Uses `com.sun.source.tree` (JDK Compiler Tree API) to parse syntactically valid Java code into detailed `TypeModel`, `MethodModel`, and `FieldModel` instances without requiring third-party libraries like JavaParser.
- **Dependency Graph:** Complete graph data structure supporting type references, method invocations, field usages, and class inheritance.
- **Tarjan's Cycle Detection:** Identifies strongly connected components to discover circular package and class dependencies.
- **Metrics Calculation:**
  - *Chidamber-Kemerer (CK) Suite:* Weighted Methods per Class (WMC), Depth of Inheritance Tree (DIT), Number of Children (NOC), Coupling Between Objects (CBO), Response for a Class (RFC), Lack of Cohesion in Methods (LCOM).
  - *Martin Package Metrics:* Afferent Coupling (Ca), Efferent Coupling (Ce), Abstractness (A), Instability (I), Normalized Distance from Main Sequence (D).
  - *General Metrics:* Lines of Code (LOC), Cyclomatic Complexity (CC).

### 2. Smell Detection Suite (100% Complete)
- **God Class Detector:** Identifies oversized, uncohesive classes violating Single Responsibility Principle based on configurable WMC, LOC, and LCOM thresholds.
- **Cyclic Dependency Detector:** Finds tight circular coupling at both class and package granularities.
- **Feature Envy Detector:** Flags methods that access fields or methods of foreign classes more frequently than their owning class.
- **Spaghetti Code Detector:** Discovers long, deeply nested methods lacking modular decomposition.
- **Long Parameter List Detector:** Flags methods with parameter count exceeding recommended maintainability thresholds (> 5 parameters).

### 3. Machine Learning Inference Engine (100% Complete)
- **Trained Models:** Includes Random Forest classifier (100 decision trees) and Logistic Regression model trained on 10,000 synthetic Java class metric records.
- **Zero-Dependency Java Inference:** Custom `DecisionTree.java` and `RandomForestModel.java` parse human-readable model dumps (`random_forest_model.txt`) at runtime. Evaluates 10 features: LOC, WMC, CBO, RFC, LCOM, DIT, NOC, CC, Ca, Ce.
- **Confidence Calibration:** Outputs refactoring risk probabilities, risk category (LOW, MODERATE, HIGH, CRITICAL), and dominant feature weights.

### 4. Automated Refactoring & Validation (85% Complete)
- **Extract Interface Refactoring:** Fully functional code mutator that analyzes a God Class or tightly coupled class, extracts an interface with public method signatures, updates the source class to implement the interface, and modifies client reference points.
- **Invert Dependency & Facade Introductions:** Generates detailed structural refactoring plans, diff previews, and risk assessments.
- **In-Memory Build Validator:** Invokes the system Java compiler (`javax.tools.JavaCompiler`) directly in memory to verify that refactored code compiles without introducing syntax or typing errors.
- **Structural Validator:** Re-analyzes mutated code through the AST parser to ensure metric improvements and absence of newly introduced smells.

### 5. Web Application Layer (90% Complete)
- **Spring MVC 6 + Jakarta EE 9:** Complete web platform without legacy `web.xml` using `WebApplicationInitializer`.
- **Session-Based Authentication:** Clean login gateway with protected routing.
- **Interactive Simulator (`simulator.jsp`):** Rich what-if refactoring workbench allowing users to preview metric deltas (e.g. CBO reduction from 14 to 3), toggle individual candidate changes, and inspect code diffs.
- **Results Explorer (`results.jsp`):** Multi-tab view showing high-level risk gauges, package distance-from-main-sequence charts, anti-pattern breakdown, and dependency graphs.
- **Export System:** Generates comprehensive audit reports in JSON, Markdown, and downloadable refactored project ZIP archives.

### 6. Bugs Discovered & Fixed During Audit
1. **ML Confidence Zero Floor:** Fixed an edge case where missing features in small classes resulted in `NaN` confidence scores. Added safe feature normalization.
2. **Build Validation False Failures:** Fixed working directory handling in `BuildValidator` where classpath resolution failed on Windows paths containing spaces.
3. **Refactoring ZIP Artifact Leakage:** Ensured rejected refactoring candidates in the interactive simulator are completely pruned from the final exported ZIP package.
4. **Missing Bean Validation Dependencies:** Resolved `jakarta.validation-api` and `hibernate-validator` runtime discrepancies in `webapp/pom.xml`.

---

## 7. What Is Yet to Be Completed (Pending Enhancements)

While the project is fully functional for academic and demonstration purposes, the following items represent outstanding enhancements:

### High Priority (For Production Readiness)
- [ ] **Database-Backed User Authentication:** Replace hardcoded credential validation (`demo`/`demo123`) in `UserService` with bcrypt-hashed credentials stored in MySQL `UserEntity`.
- [ ] **Full Code Mutation for Dependency Inversion:** `InvertDependencyRefactoring.java` currently generates refactoring plans and diffs; full automated source file rewriting via AST rewriting is pending.
- [ ] **Facade Introduction Automation:** `IntroduceFacadeRefactoring.java` provides structural specifications; generating the concrete facade wrapper class automatically remains a manual step.

### Medium Priority (Testing & Persistence)
- [ ] **Web Application Unit & Integration Tests:** Add Spring MockMvc test cases for `AnalysisController`, `ApiController`, and `RefactoringController` (Core module has 9 comprehensive test suites; webapp test coverage is currently minimal).
- [ ] **HQL-Based History Filtering:** Enhance `AnalysisRepository` with paginated criteria queries for historical project trend comparisons.
- [ ] **Live LLM API Key Configuration UI:** Enable entering an OpenAI or Google Gemini API key directly via the web UI settings instead of requiring a server `.env` reload.

### Low Priority (Future Polish)
- [ ] **Incremental AST Re-Parsing:** Cache unchanged compilation units during refactoring simulation rather than re-parsing entire project packages.
- [ ] **Dark/Light Theme Toggle:** The web interface is currently styled exclusively in dark mode; a light mode palette can be added for presentation flexibility.

---

## 8. Quantitative Project Status

```
Module Completeness:
========================================================================
Core AST Parser & Model        [████████████████████] 100% (Complete)
Metric Calculators (CK/Martin) [████████████████████] 100% (Complete)
Smell Detection Suite          [████████████████████] 100% (Complete)
ML Inference (Pure Java)       [████████████████████] 100% (Complete)
Automated Refactoring Engine   [█████████████████░░░]  85% (Plan+Extract)
Web UI & Visual Simulator      [██████████████████░░]  90% (Rich Views)
Database & Persistence (JPA)   [████████████████░░░░]  80% (Schema + DAOs)
Unit & Integration Tests       [██████████████░░░░░░]  70% (Core tested)
========================================================================
Overall Project Maturity:       ~85% (Production-Ready Demo Grade)
```

---

## 9. How to Build, Test & Run

### Prerequisites
- **JDK:** OpenJDK 17 or higher (`java -version` >= 17)
- **Maven:** Version 3.8.0 or higher (`mvn -version`)
- **MySQL (Optional):** Version 8.0+ (Fallback in-memory storage works without external DB)

### Build Instructions
Run from the root directory (`analyzer/`):

```bash
# 1. Clean and build all modules
mvn clean install -DskipTests

# 2. Run core unit test suite
mvn test -pl core
```

### Running the Core CLI Demo
To run a standalone analysis of the included `sample-demo-project` from the command line:

```bash
# Compile and run the pipeline runner
java -cp "core/target/classes;sample-demo-project/target/classes" com.analyzer.core.pipeline.AnalysisPipeline sample-demo-project
```

### Running the Web Application
To launch the interactive web application on the local embedded development server:

```bash
# Start embedded server from the webapp module
mvn jetty:run -pl webapp
```

Once started, navigate your browser to:
- **URL:** `http://localhost:8080/`
- **Default Credentials:**
  - **Username:** `demo`
  - **Password:** `demo123`

### Faculty Demonstration Flow
1. **Login:** Access `http://localhost:8080/login` using `demo` / `demo123`.
2. **Upload:** Navigate to the Upload page and upload `sample-demo-project` zipped as `sample.zip`.
3. **View Dashboard:** Inspect the computed CK metrics (CBO, WMC, LCOM) and anti-patterns detected in `BadOrderController.java`.
4. **Interactive Simulation:** Open the **Simulator** tab. View the high refactoring risk predicted by the Random Forest model. Select **Extract Interface Refactoring** on `BadOrderService`.
5. **Verify & Download:** Observe the real-time CBO drop from 14 to 3 in the preview gauge. Click **Export Refactored Project** to download the clean, validated ZIP archive.

---

*Report prepared autonomously for academic and architectural evaluation.*
