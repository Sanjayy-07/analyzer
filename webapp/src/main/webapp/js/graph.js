/* Interactive dependency graph (100% static layout, bounded auto-fit, zero drift, instant hover/click inspection). */
(function (global) {
    function shortName(id) {
        if (!id) return '';
        const parts = String(id).split(/[./\\]/);
        return parts[parts.length - 1] || id;
    }

    function createGraph(canvas, options) {
        options = options || {};
        const ctx = canvas.getContext('2d');
        const state = {
            nodes: [],
            edges: [],
            pos: {},
            scale: 1,
            panX: 0,
            panY: 0,
            dragging: null,
            panning: false,
            lastX: 0,
            lastY: 0,
            mouseScreenX: 0,
            mouseScreenY: 0,
            selected: null,
            selectedEdge: null,
            hovered: null,
            filters: { package: '', relType: '', query: '', highlight: 'none' }
        };

        function resize() {
            const rect = canvas.getBoundingClientRect();
            const dpr = window.devicePixelRatio || 1;
            const w = Math.max(320, Math.floor((rect.width || 600) * dpr));
            const h = Math.max(240, Math.floor((rect.height || 340) * dpr));
            if (canvas.width !== w || canvas.height !== h) {
                canvas.width = w;
                canvas.height = h;
                draw();
            }
        }
        resize();
        window.addEventListener('resize', resize);

        function visibleNodes() {
            const q = (state.filters.query || '').toLowerCase().trim();
            const pkg = (state.filters.package || '').toLowerCase().trim();
            return state.nodes.filter(n => {
                if (q && !(n.id.toLowerCase().includes(q) || (n.label || '').toLowerCase().includes(q))) return false;
                if (pkg && !(n.packageName || '').toLowerCase().includes(pkg)) return false;
                return true;
            });
        }

        function load(payload) {
            const data = payload || { nodes: [], edges: [] };
            state.nodes = data.nodes || [];
            state.edges = data.edges || [];
            state.pos = {};
            state.selected = null;
            state.selectedEdge = null;
            state.hovered = null;

            const n = state.nodes.length;
            if (n === 0) {
                draw();
                return;
            }

            resize();
            const dpr = window.devicePixelRatio || 1;
            const cw = canvas.width || (600 * dpr);
            const ch = canvas.height || (340 * dpr);
            const baseR = Math.min(cw, ch) * 0.40;

            // 1. Sunflower spiral initial distribution (deterministic, even, uncrowded)
            state.nodes.forEach((node, i) => {
                const phi = i * 2.399963229728653; // Golden angle in radians
                const r = n > 1 ? baseR * Math.sqrt((i + 1) / n) : 0;
                state.pos[node.id] = { x: Math.cos(phi) * r, y: Math.sin(phi) * r };
            });

            // 2. Synchronous bounded relaxation (runs exactly 30 fast steps, no infinite animation)
            const k = Math.max(65, Math.min(180, 650 / Math.sqrt(n)));
            for (let step = 0; step < 32; step++) {
                const temp = 1.0 - (step / 32.0);
                // Repulsion between node pairs
                for (let i = 0; i < n; i++) {
                    const a = state.nodes[i];
                    const pa = state.pos[a.id];
                    for (let j = i + 1; j < n; j++) {
                        const b = state.nodes[j];
                        const pb = state.pos[b.id];
                        let dx = pa.x - pb.x;
                        let dy = pa.y - pb.y;
                        let dist = Math.sqrt(dx * dx + dy * dy) || 1;
                        if (dist < 320) {
                            const force = ((k * k) / dist) * temp * 0.0022;
                            dx /= dist; dy /= dist;
                            pa.x += dx * force; pa.y += dy * force;
                            pb.x -= dx * force; pb.y -= dy * force;
                        }
                    }
                }
                // Attraction along edges
                state.edges.forEach(e => {
                    const pa = state.pos[e.from], pb = state.pos[e.to];
                    if (!pa || !pb) return;
                    let dx = pb.x - pa.x;
                    let dy = pb.y - pa.y;
                    let dist = Math.sqrt(dx * dx + dy * dy) || 1;
                    const force = (dist - 85) * temp * 0.0045;
                    dx /= dist; dy /= dist;
                    pa.x += dx * force; pa.y += dy * force;
                    pb.x -= dx * force; pb.y -= dy * force;
                });
                // Strict radial boundary clamp: nodes can NEVER drift off into infinity
                const maxR = baseR * 1.15;
                state.nodes.forEach(node => {
                    const p = state.pos[node.id];
                    const d = Math.hypot(p.x, p.y);
                    if (d > maxR) {
                        p.x = (p.x / d) * maxR;
                        p.y = (p.y / d) * maxR;
                    }
                });
            }

            // 3. Auto-fit and center within canvas bounds so 100% of nodes are always visible
            let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
            state.nodes.forEach(node => {
                const p = state.pos[node.id];
                if (p.x < minX) minX = p.x;
                if (p.x > maxX) maxX = p.x;
                if (p.y < minY) minY = p.y;
                if (p.y > maxY) maxY = p.y;
            });

            const pad = 48 * dpr;
            const spanX = Math.max(20, maxX - minX);
            const spanY = Math.max(20, maxY - minY);
            const fitScaleX = (cw - pad * 2) / spanX;
            const fitScaleY = (ch - pad * 2) / spanY;
            state.scale = Math.min(1.05, Math.min(fitScaleX, fitScaleY));
            state.panX = -(minX + maxX) / 2;
            state.panY = -(minY + maxY) / 2;

            draw();
        }

        function toScreen(p) {
            return {
                x: canvas.width / 2 + (p.x + state.panX) * state.scale,
                y: canvas.height / 2 + (p.y + state.panY) * state.scale
            };
        }

        function nodeColor(n) {
            if (n.proposed) return '#a855f7';
            if (n.inCycle) return '#ef4444';
            if (n.risk === 'HIGH') return '#f97316';
            if (n.smell) return '#f59e0b';
            if ((n.kind || '') === 'INTERFACE') return '#0ea5e9';
            return '#6366f1';
        }

        function draw() {
            ctx.clearRect(0, 0, canvas.width, canvas.height);
            ctx.fillStyle = '#0a0d14';
            ctx.fillRect(0, 0, canvas.width, canvas.height);

            const dpr = window.devicePixelRatio || 1;
            const vis = visibleNodes();
            const ids = new Set(vis.map(n => n.id));
            const highlight = state.filters.highlight;
            const activeNode = state.selected || state.hovered;

            // 1. Draw Edges
            state.edges.forEach(e => {
                if (!ids.has(e.from) || !ids.has(e.to)) return;
                if (highlight === 'cycles') {
                    const nf = vis.find(n => n.id === e.from);
                    const nt = vis.find(n => n.id === e.to);
                    if (!nf?.inCycle || !nt?.inCycle) return;
                }
                const posA = state.pos[e.from];
                const posB = state.pos[e.to];
                if (!posA || !posB) return;
                const pa = toScreen(posA);
                const pb = toScreen(posB);

                const isSelectedEdge = (state.selectedEdge && state.selectedEdge.from === e.from && state.selectedEdge.to === e.to);
                const isConnected = activeNode && (e.from === activeNode || e.to === activeNode);

                ctx.beginPath();
                ctx.moveTo(pa.x, pa.y);
                ctx.lineTo(pb.x, pb.y);

                if (e.change === 'added') {
                    ctx.strokeStyle = '#34d399'; // vivid emerald green
                    ctx.lineWidth = 2.4 * dpr;
                    ctx.setLineDash([]);
                } else if (e.change === 'removed') {
                    ctx.strokeStyle = '#f87171'; // crimson red
                    ctx.lineWidth = 2.0 * dpr;
                    ctx.setLineDash([5 * dpr, 4 * dpr]);
                } else if (isSelectedEdge || isConnected) {
                    ctx.strokeStyle = '#fbbf24'; // amber gold
                    ctx.lineWidth = 2.5 * dpr;
                    ctx.setLineDash([]);
                } else {
                    ctx.strokeStyle = 'rgba(148, 163, 184, 0.32)';
                    ctx.lineWidth = 1.2 * dpr;
                    ctx.setLineDash([]);
                }
                ctx.stroke();

                // Directional arrow head
                const angle = Math.atan2(pb.y - pa.y, pb.x - pa.x);
                const arrowR = 14 * dpr;
                const ax = pb.x - arrowR * Math.cos(angle);
                const ay = pb.y - arrowR * Math.sin(angle);
                ctx.beginPath();
                ctx.moveTo(ax, ay);
                ctx.lineTo(ax - 8 * dpr * Math.cos(angle - 0.42), ay - 8 * dpr * Math.sin(angle - 0.42));
                ctx.lineTo(ax - 8 * dpr * Math.cos(angle + 0.42), ay - 8 * dpr * Math.sin(angle + 0.42));
                ctx.fillStyle = ctx.strokeStyle;
                ctx.fill();
            });

            // 2. Draw Nodes and Label Badges
            let hoveredNodeObj = null;
            vis.forEach(n => {
                if (highlight === 'risk' && n.risk !== 'HIGH') return;
                if (highlight === 'smells' && !n.smell) return;
                if (highlight === 'cycles' && !n.inCycle) return;

                const pos = state.pos[n.id];
                if (!pos) return;
                const p = toScreen(pos);
                const r = 13 * dpr;

                const isSel = (state.selected === n.id);
                const isHov = (state.hovered === n.id);
                if (isHov) hoveredNodeObj = n;

                // Glowing halo for selected or hovered node
                if (isSel || isHov) {
                    ctx.beginPath();
                    ctx.arc(p.x, p.y, r + 5 * dpr, 0, Math.PI * 2);
                    ctx.fillStyle = isSel ? 'rgba(251, 191, 36, 0.30)' : 'rgba(56, 189, 248, 0.25)';
                    ctx.fill();
                }

                // Node circle
                ctx.beginPath();
                ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
                ctx.fillStyle = nodeColor(n);
                ctx.fill();

                ctx.strokeStyle = isSel ? '#fbbf24' : (isHov ? '#38bdf8' : 'rgba(255, 255, 255, 0.25)');
                ctx.lineWidth = (isSel || isHov) ? 2.5 * dpr : 1.0 * dpr;
                ctx.stroke();

                // Clean label pill badge
                const rawLabel = n.label || shortName(n.id);
                const displayLabel = rawLabel.length > 20 ? rawLabel.slice(0, 18) + '…' : rawLabel;

                ctx.font = `${10.5 * dpr}px Inter, -apple-system, sans-serif`;
                ctx.textAlign = 'center';
                ctx.textBaseline = 'middle';
                const textWidth = ctx.measureText(displayLabel).width;
                const pillW = textWidth + 10 * dpr;
                const pillH = 16 * dpr;
                const pillX = p.x - pillW / 2;
                const pillY = p.y + r + 4 * dpr;

                ctx.fillStyle = isSel ? 'rgba(30, 41, 59, 0.96)' : 'rgba(15, 23, 42, 0.90)';
                ctx.strokeStyle = isSel ? '#fbbf24' : (isHov ? '#38bdf8' : 'rgba(71, 85, 105, 0.45)');
                ctx.lineWidth = 1;
                if (ctx.roundRect) {
                    ctx.beginPath();
                    ctx.roundRect(pillX, pillY, pillW, pillH, 4 * dpr);
                    ctx.fill();
                    ctx.stroke();
                } else {
                    ctx.fillRect(pillX, pillY, pillW, pillH);
                    ctx.strokeRect(pillX, pillY, pillW, pillH);
                }

                ctx.fillStyle = isSel ? '#fbbf24' : (isHov ? '#38bdf8' : '#f1f5f9');
                ctx.fillText(displayLabel, p.x, pillY + pillH / 2);
            });

            // 3. Draw On-Canvas Floating Tooltip when hovering
            if (hoveredNodeObj) {
                drawTooltip(hoveredNodeObj, dpr);
            }
        }

        function drawTooltip(n, dpr) {
            const pos = state.pos[n.id];
            if (!pos) return;
            const p = toScreen(pos);
            const title = n.label || shortName(n.id);
            const sub = `${n.kind || 'CLASS'} • fan-in: ${n.fanIn} • fan-out: ${n.fanOut}`;

            ctx.font = `bold ${11 * dpr}px Inter, sans-serif`;
            const w1 = ctx.measureText(title).width;
            ctx.font = `${10 * dpr}px Inter, sans-serif`;
            const w2 = ctx.measureText(sub).width;
            const boxW = Math.max(w1, w2) + 16 * dpr;
            const boxH = 36 * dpr;

            let bx = p.x - boxW / 2;
            let by = p.y - 13 * dpr - boxH - 6 * dpr;
            if (by < 10) by = p.y + 24 * dpr;
            bx = Math.max(8, Math.min(canvas.width - boxW - 8, bx));

            ctx.fillStyle = 'rgba(15, 23, 42, 0.96)';
            ctx.strokeStyle = '#38bdf8';
            ctx.lineWidth = 1;
            if (ctx.roundRect) {
                ctx.beginPath();
                ctx.roundRect(bx, by, boxW, boxH, 6 * dpr);
                ctx.fill();
                ctx.stroke();
            } else {
                ctx.fillRect(bx, by, boxW, boxH);
                ctx.strokeRect(bx, by, boxW, boxH);
            }

            ctx.textAlign = 'left';
            ctx.textBaseline = 'top';
            ctx.fillStyle = '#ffffff';
            ctx.font = `bold ${11 * dpr}px Inter, sans-serif`;
            ctx.fillText(title, bx + 8 * dpr, by + 6 * dpr);
            ctx.fillStyle = '#94a3b8';
            ctx.font = `${9.5 * dpr}px Inter, sans-serif`;
            ctx.fillText(sub, bx + 8 * dpr, by + 20 * dpr);
        }

        function hitNode(x, y) {
            const vis = visibleNodes();
            const dpr = window.devicePixelRatio || 1;
            const r = 16 * dpr;
            for (let i = vis.length - 1; i >= 0; i--) {
                const n = vis[i];
                const pos = state.pos[n.id];
                if (!pos) continue;
                const p = toScreen(pos);
                const dx = x - p.x, dy = y - p.y;
                if (dx * dx + dy * dy <= r * r) return n;
            }
            return null;
        }

        function hitEdge(x, y) {
            const vis = new Set(visibleNodes().map(n => n.id));
            const dpr = window.devicePixelRatio || 1;
            for (const e of state.edges) {
                if (!vis.has(e.from) || !vis.has(e.to)) continue;
                const posA = state.pos[e.from], posB = state.pos[e.to];
                if (!posA || !posB) return;
                const a = toScreen(posA);
                const b = toScreen(posB);
                const dist = pointToSegment(x, y, a.x, a.y, b.x, b.y);
                if (dist < 6 * dpr) return e;
            }
            return null;
        }

        function pointToSegment(px, py, x1, y1, x2, y2) {
            const dx = x2 - x1, dy = y2 - y1;
            const l2 = dx * dx + dy * dy || 1;
            let t = ((px - x1) * dx + (py - y1) * dy) / l2;
            t = Math.max(0, Math.min(1, t));
            const sx = x1 + t * dx, sy = y1 + t * dy;
            return Math.hypot(px - sx, py - sy);
        }

        canvas.addEventListener('mousedown', ev => {
            const rect = canvas.getBoundingClientRect();
            const x = (ev.clientX - rect.left) * (canvas.width / rect.width);
            const y = (ev.clientY - rect.top) * (canvas.height / rect.height);
            const n = hitNode(x, y);
            if (n) {
                state.dragging = n.id;
                state.selected = n.id;
                state.selectedEdge = null;
                if (options.onSelectNode) options.onSelectNode(n);
                draw();
            } else {
                const e = hitEdge(x, y);
                if (e) {
                    state.selectedEdge = e;
                    state.selected = null;
                    if (options.onSelectEdge) options.onSelectEdge(e);
                    draw();
                } else {
                    state.panning = true;
                }
            }
            state.lastX = ev.clientX;
            state.lastY = ev.clientY;
        });

        window.addEventListener('mousemove', ev => {
            const rect = canvas.getBoundingClientRect();
            const x = (ev.clientX - rect.left) * (canvas.width / rect.width);
            const y = (ev.clientY - rect.top) * (canvas.height / rect.height);

            const dx = ev.clientX - state.lastX;
            const dy = ev.clientY - state.lastY;
            state.lastX = ev.clientX;
            state.lastY = ev.clientY;

            if (state.dragging && state.pos[state.dragging]) {
                const factor = 1 / state.scale;
                state.pos[state.dragging].x += dx * factor;
                state.pos[state.dragging].y += dy * factor;
                draw();
            } else if (state.panning) {
                state.panX += dx / state.scale;
                state.panY += dy / state.scale;
                draw();
            } else {
                const isInside = (ev.clientX >= rect.left && ev.clientX <= rect.right &&
                                  ev.clientY >= rect.top && ev.clientY <= rect.bottom);
                if (!isInside) {
                    if (state.hovered) {
                        state.hovered = null;
                        canvas.style.cursor = 'default';
                        draw();
                    }
                    return;
                }
                // Hover detection
                const h = hitNode(x, y);
                const prevHover = state.hovered;
                state.hovered = h ? h.id : null;
                canvas.style.cursor = h ? 'pointer' : 'default';

                if (h && state.hovered !== prevHover) {
                    if (options.onHoverNode) options.onHoverNode(h);
                    draw();
                } else if (!h && prevHover) {
                    draw();
                }
            }
        });

        window.addEventListener('mouseup', () => {
            state.dragging = null;
            state.panning = false;
        });

        canvas.addEventListener('wheel', ev => {
            ev.preventDefault();
            const factor = ev.deltaY < 0 ? 1.10 : 0.90;
            state.scale = Math.max(0.25, Math.min(4.0, state.scale * factor));
            draw();
        }, { passive: false });

        return {
            load,
            draw,
            reheat() {
                load({ nodes: state.nodes, edges: state.edges });
            },
            setFilter(key, value) {
                state.filters[key] = value;
                draw();
            },
            resetView() {
                const n = state.nodes.length;
                if (n === 0) return;
                const dpr = window.devicePixelRatio || 1;
                let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
                state.nodes.forEach(node => {
                    const p = state.pos[node.id];
                    if (!p) return;
                    if (p.x < minX) minX = p.x;
                    if (p.x > maxX) maxX = p.x;
                    if (p.y < minY) minY = p.y;
                    if (p.y > maxY) maxY = p.y;
                });
                const pad = 48 * dpr;
                const spanX = Math.max(20, maxX - minX);
                const spanY = Math.max(20, maxY - minY);
                state.scale = Math.min(1.05, Math.min((canvas.width - pad * 2) / spanX, (canvas.height - pad * 2) / spanY));
                state.panX = -(minX + maxX) / 2;
                state.panY = -(minY + maxY) / 2;
                draw();
            },
            searchAndSelect(query) {
                state.filters.query = query || '';
                const found = visibleNodes()[0];
                if (found) {
                    state.selected = found.id;
                    if (options.onSelectNode) options.onSelectNode(found);
                }
                draw();
            }
        };
    }

    global.ArchGraph = { createGraph, shortName };
})(window);
