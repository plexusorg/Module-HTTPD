(function () {
    const grid = document.getElementById('players-grid');
    const filterInput = document.getElementById('player-filter');
    if (!grid) return;

    const isStaff = grid.dataset.staff === 'true';
    let filter = '';

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function pingColor(ping) {
        if (ping < 80) return 'text-success';
        if (ping < 200) return 'text-warning';
        return 'text-destructive';
    }

    function renderCard(p) {
        const safeName = escapeHtml(p.name);
        const safeUuid = encodeURIComponent(p.uuid);
        const opChip = p.op
            ? '<span class="inline-flex h-5 items-center rounded-full bg-primary/12 px-2 text-xs text-primary">op</span>'
            : '';
        const worldLabel = p.world ? 'In ' + escapeHtml(p.world) : '';
        const separator = worldLabel ? '<span class="text-foreground/30">·</span>' : '';
        const body = `
                <img class="size-10 rounded-lg bg-muted [image-rendering:pixelated]"
                     src="https://vzge.me/face/512/${safeUuid}.png"
                     alt="" loading="lazy" width="40" height="40">
                <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2">
                        <span class="truncate text-sm font-medium">${safeName}</span>
                        ${opChip}
                    </div>
                    <div class="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
                        <span>${worldLabel}</span>
                        ${separator}
                        <span class="tabular ${pingColor(p.ping)}">${p.ping | 0}ms</span>
                    </div>
                </div>
        `;
        if (isStaff) {
            return `
                <a href="/player/${safeUuid}"
                   class="ring-card group flex items-center gap-3 rounded-2xl bg-card p-3 transition-colors hover:bg-secondary/50"
                   data-name="${safeName.toLowerCase()}"
                   title="Open admin panel for ${safeName}">${body}</a>
            `;
        }
        return `
            <div class="ring-card flex items-center gap-3 rounded-2xl bg-card p-3"
                 data-name="${safeName.toLowerCase()}">${body}</div>
        `;
    }

    function renderEmpty() {
        return `
            <div class="ring-card col-span-full rounded-2xl bg-card p-10 text-center">
                <svg class="mx-auto size-8 text-muted-foreground/60" aria-hidden="true"><use href="#i-users"/></svg>
                <p class="mt-3 text-sm text-muted-foreground">No players online right now.</p>
            </div>
        `;
    }

    function applyFilter() {
        const q = filter;
        const cards = grid.querySelectorAll('[data-name]');
        cards.forEach(c => {
            const n = c.getAttribute('data-name') || '';
            c.style.display = (!q || n.includes(q)) ? '' : 'none';
        });
    }

    function paint(state) {
        const players = Array.isArray(state.players) ? state.players : [];
        document.querySelectorAll('[data-stat="players-online"]').forEach(el => {
            el.textContent = String(players.length);
        });
        document.querySelectorAll('[data-stat="players-max"]').forEach(el => {
            el.textContent = String(state.max ?? 0);
        });
        grid.innerHTML = players.length === 0
            ? renderEmpty()
            : players.map(renderCard).join('');
        applyFilter();
    }

    if (filterInput) {
        filterInput.addEventListener('input', () => {
            filter = filterInput.value.toLowerCase().trim();
            applyFilter();
        });
    }

    function connect() {
        const endpoint = isStaff ? '/api/players/stream/staff' : '/api/players/stream';
        const es = new EventSource(endpoint);
        es.addEventListener('message', (evt) => {
            try {
                paint(JSON.parse(evt.data));
            } catch (e) {
                // ignore malformed frame
            }
        });
        return es;
    }

    connect();
})();
