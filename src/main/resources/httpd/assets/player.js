(async function () {
    const pingEl = document.querySelector('[data-player-ping]');
    const statusEl = document.querySelector('[data-player-status]');
    const worldEl = document.querySelector('[data-player-world]');
    const gamemodeEl = document.querySelector('[data-player-gamemode]');
    if (!pingEl) return;
    const uuid = pingEl.getAttribute('data-uuid');
    if (!uuid) return;

    const { renderItem } = await import('/assets/blockrenderer.js');

    // ---- Helpers ----

    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function toTitle(snake) {
        if (!snake) return '';
        return snake.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, c => c.toUpperCase());
    }

    const ROMAN = ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X'];
    function toRoman(n) {
        return ROMAN[n] || String(n);
    }

    function pingColor(ping) {
        if (ping < 80) return 'text-success';
        if (ping < 200) return 'text-warning';
        return 'text-destructive';
    }

    // ---- Live header (ping/world/gamemode/status) ----

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

    const staffSrc = new EventSource('/api/players/stream/staff');
    staffSrc.addEventListener('message', (evt) => {
        try { handle(JSON.parse(evt.data)); }
        catch (e) {}
    });

    // ---- Action dialog wiring ----

    const dialog = document.getElementById('action-dialog');
    const form = document.getElementById('action-form');
    if (dialog && form) {
        const actionInput = form.querySelector('[data-action-input]');
        const actionLabel = form.querySelector('[data-action-label]');
        const durationField = form.querySelector('[data-duration-field]');
        const reasonField = form.querySelector('[data-reason-field]');
        const reasonInput = form.querySelector('input[name="reason"]');
        const slotInput = form.querySelector('[data-slot-input]');
        const actionDescription = form.querySelector('[data-action-description]');

        document.querySelectorAll('[data-admin-action]').forEach(btn => {
            btn.addEventListener('click', () => {
                const action = btn.getAttribute('data-admin-action');
                const isTemp = btn.getAttribute('data-admin-temp') === 'true';
                const noReason = btn.getAttribute('data-admin-no-reason') === 'true';
                const selectedRequired = btn.getAttribute('data-selected-required') === 'true';
                if (selectedRequired && !selectedKey) return;
                actionInput.value = action;
                actionLabel.textContent = action.replace(/-/g, ' ');
                if (slotInput) slotInput.value = selectedRequired ? selectedKey : '';
                durationField.hidden = !isTemp;
                durationField.querySelector('select').disabled = !isTemp;
                if (reasonField) reasonField.hidden = noReason;
                if (reasonInput) {
                    reasonInput.disabled = noReason;
                    reasonInput.required = !noReason;
                    reasonInput.value = '';
                }
                if (actionDescription) {
                    const target = 'Target: <span class="font-medium text-foreground">' + escapeHtml(document.querySelector('h1')?.textContent || 'player') + '</span>';
                    actionDescription.innerHTML = selectedRequired
                        ? target + '<br>Slot: <span class="font-mono text-foreground">' + escapeHtml(selectedKey) + '</span>'
                        : target;
                }
                if (typeof dialog.showModal === 'function') dialog.showModal();
                else dialog.setAttribute('open', '');
                if (!noReason && reasonInput) setTimeout(() => reasonInput.focus(), 0);
            });
        });

        form.querySelectorAll('[data-dialog-cancel]').forEach(btn => {
            btn.addEventListener('click', () => dialog.close());
        });
    }

    // ---- Live inventory ----

    const invRoot = document.getElementById('inv-root');
    if (!invRoot) return;

    // Latest inventory snapshot, used by the click handler.
    let lastInv = null;
    // Slot currently rendered in the detail panel (key like "storage-5"); kept across re-renders so the highlight survives data refreshes.
    let selectedKey = null;

    function updateInventoryActionButtons() {
        const selectedItem = getItemBySlotKey(lastInv, selectedKey);
        const enabled = !!(lastInv && lastInv.online && selectedKey && selectedItem);
        document.querySelectorAll('[data-selected-required]').forEach(btn => {
            btn.disabled = !enabled;
            if (enabled) btn.removeAttribute('disabled');
            else btn.setAttribute('disabled', '');
            btn.title = enabled ? 'Clear ' + selectedKey : 'Select an occupied inventory slot first';
        });
    }

    function renderDurabilityBar(item) {
        if (!item.maxDamage) return '';
        const damage = item.damage || 0;
        const remaining = (item.maxDamage - damage) / item.maxDamage;
        if (remaining >= 0.999) return '';
        const cls = remaining > 0.5 ? 'bg-success' : remaining > 0.25 ? 'bg-warning' : 'bg-destructive';
        const pct = Math.max(0, Math.min(100, remaining * 100));
        return `<div class="absolute inset-x-1 bottom-0.5 h-0.5 rounded-full bg-foreground/15">
            <div class="${cls} h-full rounded-full" style="width:${pct.toFixed(1)}%"></div>
        </div>`;
    }

    function tooltipFor(item) {
        const parts = [];
        parts.push(item.name || toTitle(item.type));
        if (item.amount > 1) parts[0] += ' ×' + item.amount;
        if (item.enchants) {
            for (const [k, v] of Object.entries(item.enchants)) {
                parts.push(toTitle(k) + ' ' + toRoman(v));
            }
        }
        if (item.maxDamage) {
            const remaining = item.maxDamage - (item.damage || 0);
            parts.push('Durability: ' + remaining + ' / ' + item.maxDamage);
        }
        return escapeHtml(parts.join(' • '));
    }

    function renderItemIcon(item) {
        const name = item.type.toLowerCase();
        const label = escapeHtml(name.replace(/_/g, ' '));
        return `<span class="absolute inset-0 pointer-events-none" data-item-icon="${escapeHtml(name)}">
            <span class="absolute inset-0 grid place-items-center text-[8px] font-mono text-muted-foreground leading-tight px-0.5 text-center break-all">${label}</span>
        </span>`;
    }

    async function hydrateIcons(root) {
        const targets = Array.from(root.querySelectorAll('[data-item-icon]:not([data-item-hydrated])'));
        for (const el of targets) el.setAttribute('data-item-hydrated', '');
        await Promise.all(targets.map(async el => {
            const name = el.getAttribute('data-item-icon');
            const url = await renderItem(name);
            if (!url) return;
            const img = document.createElement('img');
            img.className = 'size-full object-contain pointer-events-none [image-rendering:pixelated]';
            img.alt = name;
            img.src = url;
            el.innerHTML = '';
            el.appendChild(img);
        }));
    }

    function renderSlot(item, key) {
        if (!item) {
            return `<div class="ring-card size-12 rounded-md bg-muted/40" data-slot-key="${key}"></div>`;
        }
        const tooltip = tooltipFor(item);
        const amount = item.amount > 1
            ? `<span class="pointer-events-none absolute bottom-0.5 right-1 text-xs font-mono font-medium [text-shadow:0_1px_2px_rgba(0,0,0,0.7)]">${item.amount}</span>`
            : '';
        const enchanted = item.enchants
            ? '<span class="pointer-events-none absolute inset-0 rounded-md ring-1 ring-inset ring-primary/40 bg-primary/5"></span>'
            : '';
        const selected = key === selectedKey
            ? 'ring-2 ring-primary'
            : 'ring-card';
        return `
            <button type="button" data-slot-key="${key}"
                    class="${selected} relative size-12 rounded-md bg-muted/40 cursor-pointer transition-colors hover:bg-muted"
                    title="${tooltip}">
                ${renderItemIcon(item)}
                ${enchanted}
                ${amount}
                ${renderDurabilityBar(item)}
            </button>
        `;
    }

    function renderInventoryGrid(inv) {
        const armor = inv.armor || {};
        const storage = inv.storage || [];
        const hotbar = inv.hotbar || [];
        return `
            <div class="flex flex-wrap gap-4 lg:flex-nowrap">
                <div>
                    <p class="mb-1 text-[10px] uppercase tracking-wide text-muted-foreground">Main</p>
                    <div class="space-y-2">
                        <div class="grid grid-cols-9 gap-1">
                            ${storage.map((s, i) => renderSlot(s, 'storage-' + i)).join('')}
                        </div>
                        <div class="grid grid-cols-9 gap-1 border-t border-border/40 pt-2">
                            ${hotbar.map((s, i) => renderSlot(s, 'hotbar-' + i)).join('')}
                        </div>
                    </div>
                </div>
                <div class="flex gap-4">
                    <div>
                        <p class="mb-1 text-[10px] uppercase tracking-wide text-muted-foreground">Armor</p>
                        <div class="flex flex-col gap-1">
                            ${renderSlot(armor.helmet, 'armor-helmet')}
                            ${renderSlot(armor.chest, 'armor-chest')}
                            ${renderSlot(armor.legs, 'armor-legs')}
                            ${renderSlot(armor.boots, 'armor-boots')}
                        </div>
                    </div>
                    <div>
                        <p class="mb-1 text-[10px] uppercase tracking-wide text-muted-foreground">Offhand</p>
                        ${renderSlot(inv.offhand, 'offhand')}
                    </div>
                </div>
            </div>
        `;
    }

    function renderDetailPanel(item) {
        if (!item) {
            return `<div class="flex h-full min-h-[14rem] items-center justify-center text-center text-sm text-muted-foreground">
                Click a slot to inspect the item.
            </div>`;
        }
        const safeType = escapeHtml(item.type);
        const safeName = item.name ? escapeHtml(item.name) : null;
        const nameTruncated = item.nameTruncated && Number.isFinite(Number(item.nameTruncatedChars))
            ? Number(item.nameTruncatedChars)
            : null;
        const lines = [];
        lines.push(`<div class="flex items-start gap-3">
            <div class="ring-card relative size-16 shrink-0 rounded-md bg-muted/40">
                ${renderItemIcon(item)}
            </div>
            <div class="min-w-0">
                ${safeName ? `<p class="max-w-full overflow-hidden text-ellipsis whitespace-nowrap text-base font-medium italic">${safeName}</p>` : ''}
                ${nameTruncated != null ? `<p class="mt-0.5 text-[10px] font-semibold uppercase tracking-wide text-destructive">Name truncated by ${nameTruncated.toLocaleString()} characters</p>` : ''}
                <p class="font-mono text-xs text-muted-foreground break-all">${safeType}</p>
                <p class="mt-0.5 text-xs text-muted-foreground">Count: ${item.amount}</p>
            </div>
        </div>`);

        if (item.lore && item.lore.length) {
            lines.push(`<div>
                <p class="text-[10px] uppercase tracking-wide text-muted-foreground">Lore</p>
                <ul class="mt-1 space-y-0.5 text-xs italic text-foreground/80">
                    ${item.lore.map(l => `<li class="break-all">${escapeHtml(l)}</li>`).join('')}
                </ul>
            </div>`);
        }

        if (item.enchants && Object.keys(item.enchants).length) {
            const rows = Object.entries(item.enchants)
                .map(([k, v]) => `<li class="flex justify-between gap-3"><span>${escapeHtml(toTitle(k))}</span><span class="font-mono text-muted-foreground">${toRoman(v)}</span></li>`)
                .join('');
            lines.push(`<div>
                <p class="text-[10px] uppercase tracking-wide text-muted-foreground">Enchantments</p>
                <ul class="mt-1 space-y-0.5 text-xs">${rows}</ul>
            </div>`);
        }

        if (item.maxDamage) {
            const remaining = item.maxDamage - (item.damage || 0);
            const pct = Math.max(0, Math.min(100, (remaining / item.maxDamage) * 100));
            const cls = pct > 50 ? 'bg-success' : pct > 25 ? 'bg-warning' : 'bg-destructive';
            lines.push(`<div>
                <p class="text-[10px] uppercase tracking-wide text-muted-foreground">Durability</p>
                <div class="mt-1 flex items-center gap-2">
                    <div class="h-1.5 flex-1 rounded-full bg-muted">
                        <div class="${cls} h-full rounded-full" style="width:${pct.toFixed(1)}%"></div>
                    </div>
                    <span class="font-mono text-xs tabular text-muted-foreground">${remaining} / ${item.maxDamage}</span>
                </div>
            </div>`);
        }

        const tags = [];
        if (item.unbreakable) tags.push('Unbreakable');
        if (item.flags) item.flags.forEach(f => tags.push(toTitle(f.replace(/^HIDE_/, 'Hide '))));
        if (tags.length) {
            lines.push(`<div>
                <p class="text-[10px] uppercase tracking-wide text-muted-foreground">Tags</p>
                <div class="mt-1 flex flex-wrap gap-1">
                    ${tags.map(t => `<span class="inline-flex h-5 items-center rounded-full bg-muted px-2 text-xs">${escapeHtml(t)}</span>`).join('')}
                </div>
            </div>`);
        }

        if (item.pdcKeys && item.pdcKeys.length) {
            lines.push(`<div>
                <p class="text-[10px] uppercase tracking-wide text-muted-foreground">Plugin NBT keys</p>
                <ul class="mt-1 space-y-0.5 font-mono text-xs text-foreground/80">
                    ${item.pdcKeys.map(k => `<li class="break-all">${escapeHtml(k)}</li>`).join('')}
                </ul>
                ${item.pdcKeysTruncated ? `<p class="mt-1 text-[10px] font-semibold uppercase tracking-wide text-destructive">Plugin NBT keys truncated</p>` : ''}
            </div>`);
        }

        if (item.nbt) {
            lines.push(`<div>
                <div class="flex items-center justify-between gap-2">
                    <p class="text-[10px] uppercase tracking-wide text-muted-foreground">NBT</p>
                    <button type="button" data-copy-nbt
                            class="rounded bg-muted px-2 py-0.5 text-[10px] font-medium uppercase tracking-wide text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground">
                        Copy
                    </button>
                </div>
                <pre data-nbt-text class="mt-1 max-h-48 max-w-full overflow-auto rounded-md bg-muted/40 p-2 font-mono text-[10px] leading-snug whitespace-pre-wrap break-all">${escapeHtml(item.nbt)}</pre>
                ${item.nbtTruncated && Number.isFinite(Number(item.nbtTruncatedChars)) ? `<p class="mt-1 text-[10px] font-semibold uppercase tracking-wide text-destructive">NBT truncated by ${Number(item.nbtTruncatedChars).toLocaleString()} characters</p>` : ''}
            </div>`);
        }

        return `<div class="space-y-4">${lines.join('')}</div>`;
    }

    function getItemBySlotKey(inv, key) {
        if (!inv || !inv.online || !key) return null;
        if (key === 'offhand') return inv.offhand || null;
        if (key.startsWith('storage-')) return (inv.storage || [])[parseInt(key.substring(8), 10)] || null;
        if (key.startsWith('hotbar-')) return (inv.hotbar || [])[parseInt(key.substring(7), 10)] || null;
        if (key.startsWith('armor-')) return (inv.armor || {})[key.substring(6)] || null;
        return null;
    }

    function render(inv) {
        const previousGrid = invRoot.querySelector('[data-inv-grid]');
        const previousScrollLeft = previousGrid ? previousGrid.scrollLeft : 0;
        lastInv = inv;
        if (!inv.online) {
            selectedKey = null;
            invRoot.innerHTML = `<p class="py-6 text-center text-sm text-muted-foreground">Player is offline.</p>`;
            updateInventoryActionButtons();
            return;
        }
        invRoot.innerHTML = `
            <div class="grid gap-6 lg:grid-cols-[auto_1fr]">
                <div data-inv-grid class="-mx-2 overflow-x-auto px-2 pb-2 sm:mx-0 sm:px-0">
                    <div class="min-w-max">${renderInventoryGrid(inv)}</div>
                </div>
                <div data-inv-detail class="min-w-0 rounded-xl border border-border/40 bg-background/40 p-4">
                    ${renderDetailPanel(getItemBySlotKey(inv, selectedKey))}
                </div>
            </div>
        `;
        const grid = invRoot.querySelector('[data-inv-grid]');
        if (grid) grid.scrollLeft = previousScrollLeft;
        hydrateIcons(invRoot);
        updateInventoryActionButtons();
    }

    invRoot.addEventListener('click', (evt) => {
        const copyBtn = evt.target.closest('[data-copy-nbt]');
        if (copyBtn) {
            const pre = copyBtn.closest('div').parentElement.querySelector('[data-nbt-text]');
            if (pre && navigator.clipboard) {
                navigator.clipboard.writeText(pre.textContent).then(() => {
                    const original = copyBtn.textContent;
                    copyBtn.textContent = 'Copied';
                    setTimeout(() => { copyBtn.textContent = original; }, 1500);
                }).catch(() => {});
            }
            evt.stopPropagation();
            return;
        }
        const btn = evt.target.closest('[data-slot-key]');
        if (!btn) return;
        selectedKey = btn.getAttribute('data-slot-key');
        const item = getItemBySlotKey(lastInv, selectedKey);
        const detail = invRoot.querySelector('[data-inv-detail]');
        if (detail) {
            detail.innerHTML = renderDetailPanel(item);
            hydrateIcons(detail);
        }
        invRoot.querySelectorAll('[data-slot-key]').forEach(el => {
            const isSelected = el.getAttribute('data-slot-key') === selectedKey;
            el.classList.toggle('ring-2', isSelected);
            el.classList.toggle('ring-primary', isSelected);
            el.classList.toggle('ring-card', !isSelected);
        });
        updateInventoryActionButtons();
    });

    const invSrc = new EventSource('/api/player/inventory/stream?uuid=' + encodeURIComponent(uuid));
    invSrc.addEventListener('message', (evt) => {
        try { render(JSON.parse(evt.data)); }
        catch (e) {}
    });
})();
