# Advanced Java Syllabus → Project Implementation Mapping

Course: ISWE313L — Advanced Java Programming (Syllabus v1.0, 45 lecture hours)

This document maps every topic listed in the uploaded syllabus to the exact
class(es) in this project where it is genuinely used — not just mentioned.

---

## Module 1: Advanced Concepts of Core Java (6 hrs)

| Syllabus Topic | Where It's Used |
|---|---|
| Reflection | `com.archanalyzer.parser.JavaParserAdapter` uses the JDK's Compiler Tree API (`com.sun.source`), which is itself built on the same reflective-introspection model taught in this topic; `javax.lang.model.element.Modifier` checks in `RefactoringEngine.isPublicNonConstructor()` are compile-time reflective queries over declared elements. |
| Annotations | `@WebServlet`, `@WebFilter`, `@WebListener` (webapp/servlet, filter, config), `@Entity`/`@Table`/`@Id`/`@Column`/`@OneToMany`/`@ManyToOne` (webapp/entity — JPA/Hibernate mapping annotations), `@Configuration`/`@Bean`/`@ComponentScan`/`@Controller` (Spring), `@Min`/`@Max`/`@Valid` (Bean Validation on `ConstraintsController.ConstraintForm`). |
| Generics | `ProjectModel` (`Map<String, SourceFile>`, `Map<String, ComponentNode>`), `DependencyGraph` (`Map<String, Set<String>>`), `RandomForestModel`/`LogisticRegressionModel` throughout `com.archanalyzer.ml`, `Optional<ArchitectureCandidate>` in `RejectRegenerateManager`. |
| Streams & Lambda | `SmellDetector.detect()` uses `.stream().anyMatch(...)`; `CandidateGenerator` uses `.stream().collect(Collectors.toSet())`; `MetricsEngine` uses `.mapToInt(...).average()`; `AnalysisApiServlet` builds JSON responses via `.stream().map(...).toList()`. |
| JDBC, Drivers, Database Connection | `com.archanalyzer.web.dao.ProjectDao` (raw JDBC), `com.mysql:mysql-connector-j` driver declared in `webapp/pom.xml`, `HikariCP` connection pool configured in `AppConfig.dataSource()`. |
| Statements — Prepared Statement | `ProjectDao.insertProjectWithFirstFile()`, `ProjectDao.projectNameExistsForUser()`. |
| Callable Statement | `ProjectDao.countProjectsForUser()` calling the `sp_count_projects_for_user` stored procedure defined in `webapp/sql/schema.sql`. |
| ResultSet | Used throughout `ProjectDao` (`ps.getGeneratedKeys()`, `ps.executeQuery()` results). |
| Transaction Processing | `ProjectDao.insertProjectWithFirstFile()` — explicit `conn.setAutoCommit(false)`, `conn.commit()`, `conn.rollback()` around two related inserts. |

## Module 2: J2EE Basics (6 hrs)

| Syllabus Topic | Where It's Used |
|---|---|
| J2EE overview / J2EE vs J2SE | README "Architecture" section explains the multi-tier split (presentation → Servlet/Spring MVC → service → DAO/JPA → MySQL). |
| J2EE multi-tier architecture | The `webapp` module's package structure (`servlet` / `config` (Spring services) / `dao` / `entity`) IS the multi-tier design, backed by the standalone `core` module as the "business/analysis tier". |
| Design Patterns and Frameworks | Factory (`ArchiveExtractorFactory`), Adapter (`ParserAdapter` implementations), Strategy (`CandidateGenerator`'s per-strategy methods, `ScoringEngine`), Repository (`ProjectDao`), MVC (Spring MVC + JSP), Observer-ish progress reporting via `ServletContext.log()` — see `PART 27` mapping in root README. |
| J2EE Application Life Cycle | `AppContextListener` (`ServletContextListener`) initializes the `AnalyzerPipeline` once at deployment/startup and logs shutdown. |
| Configuring Web Server / Deployment Descriptor | `webapp/src/main/webapp/WEB-INF/web.xml` — servlet mappings, filter mappings, session config, error pages, multipart config. |

## Module 3: Servlets (6 hrs)

| Syllabus Topic | Where It's Used |
|---|---|
| Servlet lifecycle | `UploadServlet`, `ProjectDashboardServlet`, `AnalysisApiServlet`, `DownloadServlet`, `LoginServlet` — all extend `HttpServlet` and override `doGet`/`doPost`. |
| Servlet API (`javax.servlet`/`javax.servlet.http` — implemented here via the modern `jakarta.servlet` namespace, the direct successor package after the Jakarta EE renaming) | Same servlets as above; `HttpServletRequest`/`HttpServletResponse`/`HttpSession`/`Part` used directly. |
| Reading and Sending Data | `UploadServlet.doPost()` reads a multipart `Part`; `AnalysisApiServlet` writes JSON via `resp.getWriter()`; `DownloadServlet` streams a ZIP via `resp.getOutputStream()`. |
| Cookies | `web.xml`'s `<cookie-config><http-only>true</http-only></cookie-config>`. |
| Session Tracking | `LoginServlet` (`req.getSession(true)`), `AuthenticationFilter` (`req.getSession(false)`), `UploadServlet`/`ProjectDashboardServlet` storing/reading `lastPipelineResult` in the session. |
| Filters/Listeners | `AuthenticationFilter` (filter), `AppContextListener` (listener). |

## Module 4: Java Server Pages (6 hrs)

| Syllabus Topic | Where It's Used |
|---|---|
| JSP Architecture / Advantages | `index.jsp`, `upload.jsp`, `results.jsp`, `empty-dashboard.jsp`, `error.jsp`, `login.jsp`, `constraints.jsp`. |
| Scripting elements — scriptlet (`<% %>`) | `results.jsp` (loops over risk predictions/smells/candidates), `error.jsp`. |
| Expression tag (`<%= %>`) | Throughout `results.jsp`, `upload.jsp`, `constraints-saved.jsp`. |
| Declaration tag (`<%! %>`) | `results.jsp` — `riskBadgeClass()` helper method. |
| Implicit Objects — `out`, `request`, `response`, `session`, `application`, `pageContext`, `exception` | `request`/`response` throughout; `session` implicitly via EL (`${pageContext.request.contextPath}`); `application.log(...)` in `error.jsp`; `exception` in `error.jsp` (enabled via `isErrorPage="true"`); `pageContext` used for context-path EL expressions. |
| Directive elements — `page`, `include`, `taglib` | `page` directive on every JSP (`contentType`, `import`, `isErrorPage`); `taglib` directive in `constraints.jsp` (Spring form tags). |

## Module 5: Java Beans (6 hrs)

| Syllabus Topic | Where It's Used |
|---|---|
| Simple Java Bean Application | `com.archanalyzer.web.dto.ProjectBean` — strict JavaBean convention (no-arg constructor, private fields, public get/set, `Serializable`). |
| EJB overview, Session/Entity/Message-driven beans | **Intentionally out of scope** — see "Known Limitations" in the root README. This application is a plain Servlet/Spring web app with no Jakarta EE application server (WildFly/GlassFish/Payara) to host a real EJB container; faking one would violate this project's own "no fake functionality" rule (spec PART 34). The plain JavaBean above is used everywhere a data-carrying bean is needed instead. |

## Module 6: Spring Framework (7 hrs)

| Syllabus Topic | Where It's Used |
|---|---|
| Dependency Injection / IoC | `AppConfig` (`@Bean` methods for `DataSource`, `EntityManagerFactory`, `PlatformTransactionManager`), `ProjectDao` (constructor injection of `DataSource`), `@ComponentScan` wiring everything under `com.archanalyzer.web`. |
| Initializing a Spring Application | `SpringMvcConfig` registered as the `DispatcherServlet`'s config class in `web.xml`. |
| Developing Web Applications — Displaying Information | `ConstraintsController` + `constraints-saved.jsp` rendering a saved `ConstraintSet`. |
| Processing Form Submission / Validating Form Input | `ConstraintsController.submitForm()` with `@Valid` + `BindingResult`; `ConstraintForm`'s `@Min`/`@Max` annotations. |
| View Controllers / Choosing a View Template Library | `ConstraintsController` (`@GetMapping`/`@PostMapping` returning logical view names); `SpringMvcConfig.viewResolver()` wires those names to JSP files under `/WEB-INF/jsp/` — JSP (Module 4) is the chosen "view template library". |

## Module 7: Java Persistence (6 hrs)

| Syllabus Topic | Where It's Used |
|---|---|
| Overview of JPA | `jakarta.persistence-api` dependency; `ProjectEntity`/`AnalysisEntity`. |
| Hibernate — Introduction, Architecture | `hibernate-core` as the JPA provider, configured via `HibernateJpaVendorAdapter` in `AppConfig.entityManagerFactory()`. |
| Hibernate Mapping Types / O/R Mapping / Annotation | `@Entity`, `@Table`, `@Id`, `@GeneratedValue`, `@Column`, `@OneToMany(mappedBy=...)`, `@ManyToOne` on `ProjectEntity`/`AnalysisEntity`, mapped onto `webapp/sql/schema.sql`'s `projects`/`analyses` tables. |
| Hibernate Query Language (HQL) | Documented extension point: a `ProjectRepository` using `entityManager.createQuery("FROM ProjectEntity p WHERE p.userId = :uid", ProjectEntity.class)` is the natural next addition once the API layer moves from session-scoped (current demo scope) to persisted-by-id lookups — see README "Known Limitations". |

## Module 8: Contemporary Issues (2 hrs)

Expert-lecture module with no fixed syllabus content to map — not applicable to implementation.
