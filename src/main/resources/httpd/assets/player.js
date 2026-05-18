(function () {
    const pingEl = document.querySelector('[data-player-ping]');
    const statusEl = document.querySelector('[data-player-status]');
    const worldEl = document.querySelector('[data-player-world]');
    const gamemodeEl = document.querySelector('[data-player-gamemode]');
    if (!pingEl) return;
    const uuid = pingEl.getAttribute('data-uuid');
    if (!uuid) return;

    function pingColor(ping) {
        if (ping < 80) return 'text-success';
        if (ping < 200) return 'text-warning';
        return 'text-destructive';
    }

    function setOffline() {
        pingEl.textContent = '—';
        pingEl.classList.remove('text-success', 'text-warning', 'text-destructive');
        if (statusEl) {
            statusEl.textContent = 'offline';
            statusEl.classList.remove('text-success');
            statusEl.classList.add('text-muted-foreground');
        }
        if (worldEl) worldEl.textContent = '—';
        if (gamemodeEl) gamemodeEl.textContent = '—';
    }

    function setOnline(p) {
        pingEl.textContent = (p.ping | 0) + 'ms';
        pingEl.classList.remove('text-success', 'text-warning', 'text-destructive');
        pingEl.classList.add(pingColor(p.ping));
        if (statusEl) {
            statusEl.textContent = 'online';
            statusEl.classList.remove('text-muted-foreground');
            statusEl.classList.add('text-success');
        }
        if (worldEl) worldEl.textContent = p.world || '—';
        if (gamemodeEl) gamemodeEl.textContent = p.gamemode ? p.gamemode.toLowerCase() : '—';
    }

    function handle(state) {
        const players = Array.isArray(state.players) ? state.players : [];
        const match = players.find(p => p.uuid === uuid);
        if (match) setOnline(match);
        else setOffline();
    }

    const es = new EventSource('/api/players/stream/staff');
    es.addEventListener('message', (evt) => {
        try { handle(JSON.parse(evt.data)); }
        catch (e) {}
    });

    // Action dialog wiring.
    const dialog = document.getElementById('action-dialog');
    const form = document.getElementById('action-form');
    if (!dialog || !form) return;
    const actionInput = form.querySelector('[data-action-input]');
    const actionLabel = form.querySelector('[data-action-label]');
    const durationField = form.querySelector('[data-duration-field]');
    const reasonInput = form.querySelector('input[name="reason"]');

    document.querySelectorAll('[data-admin-action]').forEach(btn => {
        btn.addEventListener('click', () => {
            const action = btn.getAttribute('data-admin-action');
            const isTemp = btn.getAttribute('data-admin-temp') === 'true';
            actionInput.value = action;
            actionLabel.textContent = action;
            durationField.hidden = !isTemp;
            durationField.querySelector('select').disabled = !isTemp;
            if (reasonInput) reasonInput.value = '';
            if (typeof dialog.showModal === 'function') {
                dialog.showModal();
            } else {
                dialog.setAttribute('open', '');
            }
            if (reasonInput) setTimeout(() => reasonInput.focus(), 0);
        });
    });

    form.querySelectorAll('[data-dialog-cancel]').forEach(btn => {
        btn.addEventListener('click', () => dialog.close());
    });
})();
