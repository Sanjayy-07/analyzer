<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Java ArchAnalyzer — AI-Powered Architecture Analysis & What-If Simulator</title>
    <meta name="description" content="AI-powered Java architecture quality assessment, dependency analysis, ML risk prediction, What-If change simulation, and automated refactoring platform.">
    <link rel="preconnect" href="https://fonts.googleapis.com">
    <link href="https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700;800&display=swap" rel="stylesheet">
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/main.css">
    <style>
        :root {
            --grad-1: #6366f1;
            --grad-2: #a855f7;
            --grad-3: #0ea5e9;
        }
        body { font-family: 'Inter', system-ui, sans-serif; }

        /* ── Hero ── */
        .hero-section {
            min-height: 88vh;
            display: flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            text-align: center;
            padding: 64px 24px 80px;
            position: relative;
            overflow: hidden;
        }
        .hero-bg {
            position: absolute;
            inset: 0;
            background:
                radial-gradient(ellipse 80% 50% at 50% -10%, rgba(99,102,241,0.18) 0%, transparent 70%),
                radial-gradient(ellipse 60% 40% at 80% 60%, rgba(168,85,247,0.1) 0%, transparent 60%),
                radial-gradient(ellipse 50% 35% at 20% 70%, rgba(14,165,233,0.08) 0%, transparent 60%);
            pointer-events: none;
        }
        /* Animated grid */
        .hero-grid {
            position: absolute;
            inset: 0;
            background-image:
                linear-gradient(rgba(99,102,241,0.05) 1px, transparent 1px),
                linear-gradient(90deg, rgba(99,102,241,0.05) 1px, transparent 1px);
            background-size: 48px 48px;
            pointer-events: none;
            mask-image: radial-gradient(ellipse 80% 60% at 50% 0%, black 0%, transparent 70%);
        }

        .hero-content { position: relative; max-width: 820px; }
        .hero-badge {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background: rgba(99,102,241,0.1);
            border: 1px solid rgba(99,102,241,0.3);
            border-radius: 999px;
            padding: 6px 16px;
            font-size: 0.78rem;
            font-weight: 600;
            color: #a5b4fc;
            letter-spacing: 0.4px;
            margin-bottom: 28px;
            animation: fadeSlideDown 0.5s ease;
        }
        .hero-badge .dot {
            width: 7px;
            height: 7px;
            background: #6366f1;
            border-radius: 50%;
            animation: pulse-dot 2s ease infinite;
        }
        @keyframes pulse-dot {
            0%, 100% { opacity: 1; transform: scale(1); }
            50% { opacity: 0.5; transform: scale(0.8); }
        }

        .hero-title {
            font-size: clamp(2.2rem, 5vw, 3.8rem);
            font-weight: 800;
            line-height: 1.15;
            letter-spacing: -0.02em;
            color: #f1f5f9;
            margin: 0 0 20px;
            animation: fadeSlideDown 0.6s ease 0.1s both;
        }
        .hero-title .gradient-text {
            background: linear-gradient(135deg, var(--grad-1), var(--grad-2), var(--grad-3));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            background-clip: text;
        }

        .hero-subtitle {
            font-size: 1.05rem;
            color: #94a3b8;
            line-height: 1.7;
            max-width: 600px;
            margin: 0 auto 40px;
            animation: fadeSlideDown 0.6s ease 0.2s both;
        }

        .hero-actions {
            display: flex;
            gap: 14px;
            justify-content: center;
            flex-wrap: wrap;
            animation: fadeSlideDown 0.6s ease 0.3s both;
        }

        .btn-primary-hero {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background: linear-gradient(135deg, #6366f1, #8b5cf6);
            color: white;
            padding: 14px 28px;
            border-radius: 12px;
            font-weight: 700;
            font-size: 0.95rem;
            text-decoration: none;
            transition: all 0.2s;
            box-shadow: 0 4px 24px rgba(99,102,241,0.35);
            letter-spacing: 0.3px;
        }
        .btn-primary-hero:hover {
            transform: translateY(-2px);
            box-shadow: 0 8px 32px rgba(99,102,241,0.5);
            color: white;
        }

        .btn-secondary-hero {
            display: inline-flex;
            align-items: center;
            gap: 8px;
            background: rgba(255,255,255,0.05);
            color: #cbd5e1;
            padding: 14px 28px;
            border-radius: 12px;
            font-weight: 600;
            font-size: 0.95rem;
            text-decoration: none;
            border: 1px solid rgba(255,255,255,0.1);
            transition: all 0.2s;
        }
        .btn-secondary-hero:hover {
            background: rgba(255,255,255,0.09);
            color: #e2e8f0;
            transform: translateY(-1px);
        }

        /* Stat pills */
        .hero-stats {
            display: flex;
            gap: 24px;
            justify-content: center;
            flex-wrap: wrap;
            margin-top: 56px;
            animation: fadeSlideDown 0.6s ease 0.4s both;
        }
        .stat-pill {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 3px;
        }
        .stat-pill .val { font-size: 1.6rem; font-weight: 800; color: #f1f5f9; }
        .stat-pill .lbl { font-size: 0.73rem; color: #64748b; text-transform: uppercase; letter-spacing: 0.5px; }
        .stat-sep { width: 1px; background: #1e2840; align-self: stretch; }

        @keyframes fadeSlideDown {
            from { opacity: 0; transform: translateY(-12px); }
            to   { opacity: 1; transform: translateY(0); }
        }

        /* ── Feature cards ── */
        .features-section {
            max-width: 1100px;
            margin: 0 auto;
            padding: 0 24px 100px;
        }
        .features-heading {
            text-align: center;
            margin-bottom: 48px;
        }
        .features-heading h2 {
            font-size: 1.9rem;
            font-weight: 700;
            color: #f1f5f9;
        }
        .features-heading p { color: #64748b; }

        .features-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(300px, 1fr));
            gap: 20px;
        }

        .feat-card {
            background: #11151f;
            border: 1px solid #1e2840;
            border-radius: 16px;
            padding: 24px;
            transition: all 0.2s;
            position: relative;
            overflow: hidden;
            text-decoration: none;
            display: block;
            color: inherit;
        }
        .feat-card::before {
            content: '';
            position: absolute;
            inset: 0;
            background: var(--card-grad, transparent);
            opacity: 0;
            transition: opacity 0.3s;
            pointer-events: none;
        }
        .feat-card:hover { border-color: #2d3855; transform: translateY(-3px); box-shadow: 0 12px 40px rgba(0,0,0,0.4); }
        .feat-card:hover::before { opacity: 1; }

        .feat-icon {
            width: 48px;
            height: 48px;
            border-radius: 12px;
            display: flex;
            align-items: center;
            justify-content: center;
            font-size: 1.5rem;
            margin-bottom: 16px;
        }
        .feat-card h3 { font-size: 1.05rem; font-weight: 700; color: #e2e8f0; margin: 0 0 8px; }
        .feat-card p { font-size: 0.85rem; color: #64748b; line-height: 1.6; margin: 0; }
        .feat-badge {
            margin-top: 14px;
            display: inline-block;
            font-size: 0.72rem;
            font-weight: 600;
            padding: 3px 10px;
            border-radius: 999px;
        }

        /* ── How It Works ── */
        .how-section {
            background: #0d1018;
            border-top: 1px solid #1e2840;
            border-bottom: 1px solid #1e2840;
            padding: 80px 24px;
        }
        .how-inner { max-width: 900px; margin: 0 auto; }
        .how-inner h2 { text-align: center; font-size: 1.8rem; font-weight: 700; color: #f1f5f9; margin-bottom: 48px; }
        .steps {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
            gap: 0;
            position: relative;
        }
        .step {
            display: flex;
            flex-direction: column;
            align-items: center;
            text-align: center;
            padding: 20px 16px;
            position: relative;
        }
        .step:not(:last-child)::after {
            content: '→';
            position: absolute;
            right: -14px;
            top: 28px;
            color: #2d3855;
            font-size: 1.2rem;
        }
        .step-num {
            width: 44px;
            height: 44px;
            border-radius: 50%;
            background: linear-gradient(135deg, #6366f1, #8b5cf6);
            color: white;
            font-weight: 700;
            font-size: 0.9rem;
            display: flex;
            align-items: center;
            justify-content: center;
            margin-bottom: 12px;
            box-shadow: 0 4px 12px rgba(99,102,241,0.4);
        }
        .step h4 { font-size: 0.85rem; font-weight: 600; color: #e2e8f0; margin: 0 0 6px; }
        .step p { font-size: 0.75rem; color: #64748b; margin: 0; line-height: 1.5; }

        /* ── CTA bottom ── */
        .cta-section {
            text-align: center;
            padding: 100px 24px;
            background: radial-gradient(ellipse 60% 50% at 50% 50%, rgba(99,102,241,0.1) 0%, transparent 70%);
        }
        .cta-section h2 { font-size: 2.2rem; font-weight: 800; color: #f1f5f9; margin-bottom: 16px; }
        .cta-section p { color: #94a3b8; margin-bottom: 36px; font-size: 1rem; }
    </style>
</head>
<body>

<header class="topbar">
    <div class="brand" style="background:linear-gradient(135deg,#6366f1,#a855f7);-webkit-background-clip:text;-webkit-text-fill-color:transparent;font-size:1.05rem;font-weight:700">⚡ Java ArchAnalyzer</div>
    <nav>
        <a href="${pageContext.request.contextPath}/dashboard" style="color:#94a3b8">Dashboard</a>
        <a href="${pageContext.request.contextPath}/projects/upload" style="margin-left:24px;color:#94a3b8">Upload Project</a>
        <a href="${pageContext.request.contextPath}/simulator" style="margin-left:24px;color:#a5b4fc;font-weight:600">What-If Simulator</a>
    </nav>
</header>

<!-- HERO -->
<section class="hero-section">
    <div class="hero-bg"></div>
    <div class="hero-grid"></div>
    <div class="hero-content">
        <div class="hero-badge">
            <span class="dot"></span>
            AI-Powered · Java Architecture Analysis
        </div>
        <h1 class="hero-title">
            Understand. Simulate.<br>
            <span class="gradient-text">Refactor with Confidence.</span>
        </h1>
        <p class="hero-subtitle">
            Upload a Java project. Get real static analysis, class-level dependency graphs, ML risk scoring,
            and a <strong style="color:#a5b4fc">What-If Simulator</strong> that predicts the blast radius
            of any architectural change — before touching a single file.
        </p>
        <div class="hero-actions">
            <a href="${pageContext.request.contextPath}/projects/upload" class="btn-primary-hero">
                📤 Upload Java Project
            </a>
            <a href="${pageContext.request.contextPath}/simulator" class="btn-secondary-hero">
                🔮 Open What-If Simulator
            </a>
        </div>
        <div class="hero-stats">
            <div class="stat-pill"><span class="val">100%</span><span class="lbl">Pure Java</span></div>
            <div class="stat-sep"></div>
            <div class="stat-pill"><span class="val">0</span><span class="lbl">Files Modified</span></div>
            <div class="stat-sep"></div>
            <div class="stat-pill"><span class="val">ML+</span><span class="lbl">Risk Scoring</span></div>
            <div class="stat-sep"></div>
            <div class="stat-pill"><span class="val">Instant</span><span class="lbl">Impact Maps</span></div>
        </div>
    </div>
</section>

<!-- FEATURES -->
<section class="features-section">
    <div class="features-heading">
        <h2>Everything you need to reason about Java architecture</h2>
        <p>No external services. No language runtimes. Runs entirely from a self-contained WAR file.</p>
    </div>
    <div class="features-grid">

        <a href="${pageContext.request.contextPath}/simulator" class="feat-card"
           style="--card-grad: radial-gradient(ellipse 100% 80% at 0% 0%, rgba(99,102,241,0.08), transparent);">
            <div class="feat-icon" style="background:rgba(99,102,241,0.15)">🔮</div>
            <h3>What-If Architecture Simulator</h3>
            <p>Describe any proposed change in plain English. The engine traverses the dependency graph, computes blast-radius impact, identifies compatibility risks, and projects architecture metrics — all without modifying source files.</p>
            <span class="feat-badge" style="background:rgba(99,102,241,0.15);color:#a5b4fc;">Flagship Feature →</span>
        </a>

        <div class="feat-card" style="--card-grad: radial-gradient(ellipse 100% 80% at 0% 0%, rgba(14,165,233,0.07), transparent);">
            <div class="feat-icon" style="background:rgba(14,165,233,0.12)">🕸</div>
            <h3>Java Dependency Graph</h3>
            <p>Real class-level dependency graphs built directly from Java syntax trees using the Compiler Tree API. Tracks inheritance, interfaces, coupling, fan-in/out, and Tarjan SCC cycles with zero false positives.</p>
            <span class="feat-badge" style="background:rgba(14,165,233,0.12);color:#7dd3fc;">Compiler Tree API</span>
        </div>

        <div class="feat-card" style="--card-grad: radial-gradient(ellipse 100% 80% at 0% 0%, rgba(168,85,247,0.07), transparent);">
            <div class="feat-icon" style="background:rgba(168,85,247,0.12)">🤖</div>
            <h3>ML Risk Prediction</h3>
            <p>A trained Random Forest ensemble scores each Java component LOW, MEDIUM, or HIGH structural risk using complexity, coupling, and centrality metrics. Combined with deterministic heuristics for a transparent, auditable 0–100 score.</p>
            <span class="feat-badge" style="background:rgba(168,85,247,0.12);color:#d8b4fe;">Random Forest</span>
        </div>

        <div class="feat-card" style="--card-grad: radial-gradient(ellipse 100% 80% at 0% 0%, rgba(16,185,129,0.07), transparent);">
            <div class="feat-icon" style="background:rgba(16,185,129,0.12)">♻️</div>
            <h3>Automated Refactoring</h3>
            <p>Extracts interfaces from Java classes, updates import headers, re-analyzes the architecture, and recompiles with javac to guarantee build safety. Isolated on a working copy — your original project is never modified.</p>
            <span class="feat-badge" style="background:rgba(16,185,129,0.12);color:#6ee7b7;">Build-Safe</span>
        </div>

        <div class="feat-card" style="--card-grad: radial-gradient(ellipse 100% 80% at 0% 0%, rgba(245,158,11,0.07), transparent);">
            <div class="feat-icon" style="background:rgba(245,158,11,0.12)">🦨</div>
            <h3>Architecture Smell Detection</h3>
            <p>Detects God Classes, Feature Envy, cyclic dependencies, high-coupling hotspots, and missing abstraction layers. Each smell is annotated with severity, evidence, and a recommended remedy.</p>
            <span class="feat-badge" style="background:rgba(245,158,11,0.12);color:#fcd34d;">8+ Smell Patterns</span>
        </div>

        <div class="feat-card" style="--card-grad: radial-gradient(ellipse 100% 80% at 0% 0%, rgba(239,68,68,0.07), transparent);">
            <div class="feat-icon" style="background:rgba(239,68,68,0.12)">📋</div>
            <h3>Full Architecture Report</h3>
            <p>Auto-generated Markdown report covering metrics, risk predictions, smell findings, dependency paths, refactoring candidates, simulation results, and validation plan — ready to download and share with the team.</p>
            <span class="feat-badge" style="background:rgba(239,68,68,0.12);color:#fca5a5;">Downloadable</span>
        </div>

    </div>
</section>

<!-- HOW IT WORKS -->
<section class="how-section">
    <div class="how-inner">
        <h2>How It Works</h2>
        <div class="steps">
            <div class="step">
                <div class="step-num">1</div>
                <h4>Upload Archive</h4>
                <p>ZIP / TAR / TAR.GZ Java project</p>
            </div>
            <div class="step">
                <div class="step-num">2</div>
                <h4>Parse ASTs</h4>
                <p>Compiler Tree API builds class-level model</p>
            </div>
            <div class="step">
                <div class="step-num">3</div>
                <h4>Graph + Metrics</h4>
                <p>Dependency graph, SCC cycles, coupling scores</p>
            </div>
            <div class="step">
                <div class="step-num">4</div>
                <h4>ML Risk Scoring</h4>
                <p>Random Forest predicts component risk</p>
            </div>
            <div class="step">
                <div class="step-num">5</div>
                <h4>What-If Simulate</h4>
                <p>Predict blast-radius of any change</p>
            </div>
            <div class="step">
                <div class="step-num">6</div>
                <h4>Approve &amp; Refactor</h4>
                <p>Isolated implementation with build validation</p>
            </div>
        </div>
    </div>
</section>

<!-- BOTTOM CTA -->
<section class="cta-section">
    <h2>Ready to analyze your Java project?</h2>
    <p>Upload a ZIP file and get a full architecture assessment in seconds.</p>
    <div style="display:flex;gap:14px;justify-content:center;flex-wrap:wrap">
        <a href="${pageContext.request.contextPath}/projects/upload" class="btn-primary-hero">
            📤 Upload Java Project
        </a>
        <a href="${pageContext.request.contextPath}/simulator" class="btn-secondary-hero">
            🔮 Try the Simulator
        </a>
    </div>
</section>

</body>
</html>
