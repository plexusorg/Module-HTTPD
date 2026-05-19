(function () {
    const SPARK_MAX = 60;
    const tpsHistory = [];
    let serverStartTime = null;

    const fmt = {
        pct(n) {
            if (!isFinite(n) || n === null) return '—';
            return (n * 100).toFixed(1) + '%';
        },
        bytes(b) {
            if (b == null || !isFinite(b)) return '—';
            const units = ['B', 'KB', 'MB', 'GB', 'TB'];
            let n = b, i = 0;
            while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
            return (i === 0 ? n.toFixed(0) : n.toFixed(1)) + ' ' + units[i];
        },
        bytesValue(b) {
            if (b == null || !isFinite(b)) return '—';
            const units = ['B', 'KB', 'MB', 'GB', 'TB'];
            let n = b, i = 0;
            while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
            return i === 0 ? n.toFixed(0) : n.toFixed(1);
        },
        bytesUnit(b) {
            if (b == null || !isFinite(b)) return '';
            const units = ['B', 'KB', 'MB', 'GB', 'TB'];
            let n = b, i = 0;
            while (n >= 1024 && i < units.length - 1) { n /= 1024; i++; }
            return units[i];
        },
        tps(n) {
            if (!isFinite(n)) return '—';
            return Math.min(n, 20).toFixed(2);
        },
        int(n) {
            if (n == null || !isFinite(n)) return '—';
            return Math.round(n).toLocaleString();
        },
        duration(ms) {
            if (!ms || !isFinite(ms)) return '—';
            const s = Math.floor(ms / 1000);
            const d = Math.floor(s / 86400);
            const h = Math.floor((s % 86400) / 3600);
            const m = Math.floor((s % 3600) / 60);
            const sec = s % 60;
            if (d > 0) return `${d}d ${h}h ${m}m`;
            if (h > 0) return `${h}h ${m}m ${sec}s`;
            if (m > 0) return `${m}m ${sec}s`;
            return `${sec}s`;
        }
    };

    function setText(selector, value) {
        document.querySelectorAll(selector).forEach(el => {
            if (el.textContent !== value) {
                el.textContent = value;
            }
        });
    }

    function setWidth(selector, percent) {
        document.querySelectorAll(selector).forEach(el => {
            el.style.width = Math.max(0, Math.min(100, percent)).toFixed(1) + '%';
        });
    }

    function setBarColor(selector, percent) {
        document.querySelectorAll(selector).forEach(el => {
            el.classList.remove('bg-primary', 'bg-warning', 'bg-destructive');
            const cls = percent < 70 ? 'bg-primary' : (percent < 90 ? 'bg-warning' : 'bg-destructive');
            el.classList.add(cls);
        });
    }

    function setTpsBadge(tps) {
        document.querySelectorAll('[data-tps-state]').forEach(el => {
            el.classList.remove('text-success', 'text-warning', 'text-destructive');
            el.classList.add(tps >= 19.5 ? 'text-success' : tps >= 18 ? 'text-warning' : 'text-destructive');
        });
    }

    function renderSparkline(history) {
        const svg = document.querySelector('[data-spark="tps"]');
        if (!svg) return;
        const w = svg.viewBox.baseVal.width || 600;
        const h = svg.viewBox.baseVal.height || 80;
        const pad = 4;
        const max = 20;
        const min = 15;
        if (history.length < 2) return;
        const stepX = (w - pad * 2) / (SPARK_MAX - 1);
        const xs = history.slice(-SPARK_MAX);
        const offset = SPARK_MAX - xs.length;
        const points = xs.map((v, i) => {
            const x = pad + (i + offset) * stepX;
            const cv = Math.max(min, Math.min(max, v));
            const y = pad + (h - pad * 2) * (1 - (cv - min) / (max - min));
            return `${x.toFixed(1)},${y.toFixed(1)}`;
        });
        const line = svg.querySelector('[data-spark-line]');
        if (line) line.setAttribute('points', points.join(' '));
        const area = svg.querySelector('[data-spark-area]');
        if (area) {
            const first = points[0].split(',');
            const last = points[points.length - 1].split(',');
            area.setAttribute('points',
                `${first[0]},${h - pad} ${points.join(' ')} ${last[0]},${h - pad}`);
        }
    }

    function setStatus(ok) {
        document.querySelectorAll('[data-status="text"]').forEach(el => {
            el.textContent = ok ? 'online' : 'offline';
        });
        document.querySelectorAll('.status-dot').forEach(el => {
            el.classList.remove('bg-success', 'bg-destructive');
            el.classList.add(ok ? 'bg-success' : 'bg-destructive');
        });
    }

    function tickUptime() {
        if (serverStartTime == null) return;
        setText('[data-stat="uptime"]', fmt.duration(Date.now() - serverStartTime));
    }

    function paint(s) {
        setText('[data-stat="players-online"]', String(s.players.online));
        setText('[data-stat="players-max"]', String(s.players.max));
        const pPercent = s.players.max > 0 ? (s.players.online / s.players.max) * 100 : 0;
        setWidth('[data-stat="players-bar"]', pPercent);

        setText('[data-stat="cpu-process-value"]', fmt.pct(s.cpu.process));
        setText('[data-stat="cpu-system-value"]', fmt.pct(s.cpu.system));
        setText('[data-stat="cpu-cores"]', String(s.cpu.cores));
        const cpuPercent = (s.cpu.process || 0) * 100;
        setWidth('[data-stat="cpu-bar"]', cpuPercent);
        setBarColor('[data-stat="cpu-bar"]', cpuPercent);

        setText('[data-stat="mem-value"]', fmt.bytesValue(s.memory.used));
        setText('[data-stat="mem-unit"]', fmt.bytesUnit(s.memory.used));
        setText('[data-stat="mem-max"]', fmt.bytes(s.memory.max));
        const memPercent = (s.memory.used / s.memory.max) * 100;
        setText('[data-stat="mem-percent"]', memPercent.toFixed(1) + '%');
        setWidth('[data-stat="mem-bar"]', memPercent);
        setBarColor('[data-stat="mem-bar"]', memPercent);

        const tps1 = s.server.tps[0];
        setText('[data-stat="tps-1m"]', fmt.tps(tps1));
        setText('[data-stat="tps-5m"]', fmt.tps(s.server.tps[1]));
        setText('[data-stat="tps-15m"]', fmt.tps(s.server.tps[2]));
        setTpsBadge(tps1);
        tpsHistory.push(tps1);
        if (tpsHistory.length > SPARK_MAX) tpsHistory.shift();
        renderSparkline(tpsHistory);

        if (typeof s.server.startTime === 'number' && serverStartTime !== s.server.startTime) {
            serverStartTime = s.server.startTime;
            tickUptime();
        }
        setText('[data-stat="version"]', s.server.version);

        setText('[data-stat="chunks"]', fmt.int(s.world.loadedChunks));
        setText('[data-stat="entities"]', fmt.int(s.world.entities));
        setText('[data-stat="worlds"]', fmt.int(s.world.worlds));
        setText('[data-stat="plugins"]', fmt.int(s.plugins.active));
    }

    function connect() {
        const es = new EventSource('/api/stats/stream');
        es.addEventListener('open', () => setStatus(true));
        es.addEventListener('message', (evt) => {
            try {
                paint(JSON.parse(evt.data));
                setStatus(true);
            } catch (e) {
                // ignore malformed frame; next tick will overwrite
            }
        });
        es.addEventListener('error', () => setStatus(false));
        return es;
    }

    setInterval(tickUptime, 1000);
    connect();
})();
