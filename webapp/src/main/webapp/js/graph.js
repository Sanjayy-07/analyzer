/* Interactive dependency graph (no external visualization library). */
(function (global) {
    function shortName(id) {
        if (!id) return '';
        const parts = String(id).split(/[./]/);
        return parts[parts.length - 1] || id;
    }

    function createGraph(canvas, options) {
        const ctx = canvas.getContext('2d');
        const state = {
            nodes: [],
            edges: [],
            pos: {},
            vel: {},
            scale: 1,
            panX: 0,
            panY: 0,
            dragging: null,
            panning: false,
            lastX: 0,
            lastY: 0,
            selected: null,
            selectedEdge: null,
            filters: { package: '', relType: '', query: '', highlight: 'none' },
            running: true
        };

        function resize() {
            const rect = canvas.getBoundingClientRect();
            canvas.width = Math.max(320, rect.width * (window.devicePixelRatio || 1));
            canvas.height = Math.max(240, rect.height * (window.devicePixelRatio || 1));
        }
        resize();
        window.addEventListener('resize', resize);

        function load(payload) {
            const data = payload || { nodes: [], edges: [] };
            state.nodes = data.nodes || [];
            state.edges = data.edges || [];
            state.pos = {};
            state.vel = {};
            const n = state.nodes.length || 1;
            state.nodes.forEach((node, i) => {
                const angle = (2 * Math.PI * i) / n;
                state.pos[node.id] = { x: Math.cos(angle) * 180, y: Math.sin(angle) * 180 };
                state.vel[node.id] = { x: 0, y: 0 };
            });
            state.selected = null;
            state.selectedEdge = null;
        }

        function visibleNodes() {
            const q = (state.filters.query || '').toLowerCase();
            const pkg = (state.filters.package || '').toLowerCase();
            return state.nodes.filter(n => {
                if (q && !(n.id.toLowerCase().includes(q) || (n.label || '').toLowerCase().includes(q))) return false;
                if (pkg && !(n.packageName || '').toLowerCase().includes(pkg)) return false;
                return true;
            });
        }

        function tick() {
            const vis = visibleNodes();
            const ids = new Set(vis.map(n => n.id));
            const k = 120;
            vis.forEach(a => {
                vis.forEach(b => {
                    if (a.id >= b.id) return;
                    const pa = state.pos[a.id], pb = state.pos[b.id];
                    let dx = pa.x - pb.x, dy = pa.y - pb.y;
                    let dist = Math.sqrt(dx * dx + dy * dy) || 0.1;
                    const force = (k * k) / dist;
                    dx /= dist; dy /= dist;
                    state.vel[a.id].x += dx * force * 0.002;
                    state.vel[a.id].y += dy * force * 0.002;
                    state.vel[b.id].x -= dx * force * 0.002;
                    state.vel[b.id].y -= dy * force * 0.002;
                });
            });
            state.edges.forEach(e => {
                if (!ids.has(e.from) || !ids.has(e.to)) return;
                const pa = state.pos[e.from], pb = state.pos[e.to];
                if (!pa || !pb) return;
                let dx = pb.x - pa.x, dy = pb.y - pa.y;
                let dist = Math.sqrt(dx * dx + dy * dy) || 0.1;
                const force = (dist - 140) * 0.008;
                dx /= dist; dy /= dist;
                state.vel[e.from].x += dx * force;
                state.vel[e.from].y += dy * force;
                state.vel[e.to].x -= dx * force;
                state.vel[e.to].y -= dy * force;
            });
            vis.forEach(n => {
                const v = state.vel[n.id], p = state.pos[n.id];
                v.x *= 0.85; v.y *= 0.85;
                p.x += v.x; p.y += v.y;
            });
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
            const vis = visibleNodes();
            const ids = new Set(vis.map(n => n.id));
            const highlight = state.filters.highlight;
            const neighbors = new Set();
            if (state.selected) {
                state.edges.forEach(e => {
                    if (e.from === state.selected) neighbors.add(e.to);
                    if (e.to === state.selected) neighbors.add(e.from);
                });
            }

            state.edges.forEach(e => {
                if (!ids.has(e.from) || !ids.has(e.to)) return;
                if (highlight === 'cycles') {
                    const nf = vis.find(n => n.id === e.from);
                    const nt = vis.find(n => n.id === e.to);
                    if (!nf?.inCycle || !nt?.inCycle) return;
                }
                const pa = toScreen(state.pos[e.from]);
                const pb = toScreen(state.pos[e.to]);
                ctx.beginPath();
                ctx.moveTo(pa.x, pa.y);
                ctx.lineTo(pb.x, pb.y);
                if (e.change === 'added') ctx.strokeStyle = '#34d399';
                else if (e.change === 'removed') ctx.strokeStyle = '#f87171';
                else if (state.selectedEdge && state.selectedEdge.from === e.from && state.selectedEdge.to === e.to) ctx.strokeStyle = '#fbbf24';
                else ctx.strokeStyle = 'rgba(148,163,184,0.45)';
                ctx.lineWidth = e.change === 'unchanged' || !e.change ? 1.2 : 2.4;
                if (e.change === 'removed') ctx.setLineDash([6, 4]);
                else ctx.setLineDash([]);
                ctx.stroke();
                const angle = Math.atan2(pb.y - pa.y, pb.x - pa.x);
                ctx.beginPath();
                ctx.moveTo(pb.x, pb.y);
                ctx.lineTo(pb.x - 10 * Math.cos(angle - 0.4), pb.y - 10 * Math.sin(angle - 0.4));
                ctx.lineTo(pb.x - 10 * Math.cos(angle + 0.4), pb.y - 10 * Math.sin(angle + 0.4));
                ctx.fillStyle = ctx.strokeStyle;
                ctx.fill();
            });

            vis.forEach(n => {
                if (highlight === 'risk' && n.risk !== 'HIGH') return;
                if (highlight === 'smells' && !n.smell) return;
                if (highlight === 'cycles' && !n.inCycle) return;
                const p = toScreen(state.pos[n.id]);
                const r = 14 * (window.devicePixelRatio || 1);
                ctx.beginPath();
                ctx.arc(p.x, p.y, r, 0, Math.PI * 2);
                ctx.fillStyle = nodeColor(n);
                ctx.fill();
                if (state.selected === n.id) {
                    ctx.strokeStyle = '#e2e8f0';
                    ctx.lineWidth = 2;
                    ctx.stroke();
                }
                ctx.fillStyle = '#e2e8f0';
                ctx.font = `${11 * (window.devicePixelRatio || 1)}px Inter, sans-serif`;
                ctx.textAlign = 'center';
                ctx.fillText(n.label || shortName(n.id), p.x, p.y + r + 12);
            });
        }

        function loop() {
            if (state.running) tick();
            draw();
            requestAnimationFrame(loop);
        }
        requestAnimationFrame(loop);

        function hitNode(x, y) {
            const vis = visibleNodes();
            const r = 16 * (window.devicePixelRatio || 1);
            for (let i = vis.length - 1; i >= 0; i--) {
                const n = vis[i];
                const p = toScreen(state.pos[n.id]);
                const dx = x - p.x, dy = y - p.y;
                if (dx * dx + dy * dy <= r * r) return n;
            }
            return null;
        }

        function hitEdge(x, y) {
            const vis = new Set(visibleNodes().map(n => n.id));
            for (const e of state.edges) {
                if (!vis.has(e.from) || !vis.has(e.to)) continue;
                const a = toScreen(state.pos[e.from]);
                const b = toScreen(state.pos[e.to]);
                const dist = pointToSegment(x, y, a.x, a.y, b.x, b.y);
                if (dist < 6 * (window.devicePixelRatio || 1)) return e;
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
            } else {
                const e = hitEdge(x, y);
                if (e) {
                    state.selectedEdge = e;
                    state.selected = null;
                    if (options.onSelectEdge) options.onSelectEdge(e);
                } else {
                    state.panning = true;
                }
            }
            state.lastX = ev.clientX;
            state.lastY = ev.clientY;
        });
        window.addEventListener('mousemove', ev => {
            const dx = ev.clientX - state.lastX;
            const dy = ev.clientY - state.lastY;
            state.lastX = ev.clientX;
            state.lastY = ev.clientY;
            if (state.dragging) {
                const factor = 1 / state.scale / (window.devicePixelRatio || 1);
                state.pos[state.dragging].x += dx * factor;
                state.pos[state.dragging].y += dy * factor;
                state.vel[state.dragging].x = 0;
                state.vel[state.dragging].y = 0;
            } else if (state.panning) {
                state.panX += dx / state.scale;
                state.panY += dy / state.scale;
            }
        });
        window.addEventListener('mouseup', () => {
            state.dragging = null;
            state.panning = false;
        });
        canvas.addEventListener('wheel', ev => {
            ev.preventDefault();
            const factor = ev.deltaY < 0 ? 1.08 : 0.92;
            state.scale = Math.max(0.4, Math.min(3, state.scale * factor));
        }, { passive: false });

        return {
            load,
            setFilter(key, value) { state.filters[key] = value; },
            resetView() { state.scale = 1; state.panX = 0; state.panY = 0; },
            searchAndSelect(query) {
                state.filters.query = query || '';
                const found = visibleNodes()[0];
                if (found) {
                    state.selected = found.id;
                    if (options.onSelectNode) options.onSelectNode(found);
                }
            }
        };
    }

    global.ArchGraph = { createGraph, shortName };
})(window);
