<script lang="ts">
    import {onDestroy, onMount} from 'svelte';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import {cn, formatBytes, formatDuration} from '$lib/utils';
    import type {StatsPayload} from '$lib/types/api';

    const SPARK_MAX = 60;
    let stats = $state<StatsPayload | null>(null);
    let connected = $state(false);
    let now = $state(Date.now());
    let tpsHistory: number[] = $state([]);
    let es: EventSource | null = null;
    let timer: number | null = null;

    const uptime = $derived(stats?.server.startTime ? formatDuration(now - stats.server.startTime) : '-');
    const memoryPercent = $derived(stats ? Math.max(0, Math.min(100, (stats.memory.used / stats.memory.max) * 100)) : 0);
    const cpuPercent = $derived(stats ? Math.max(0, Math.min(100, stats.cpu.process * 100)) : 0);
    const playersPercent = $derived(stats && stats.players.max > 0 ? Math.max(0, Math.min(100, (stats.players.online / stats.players.max) * 100)) : 0);
    const tps = $derived(stats?.server.tps ?? []);
    const tpsTone = $derived((tps[0] ?? 20) >= 19.5 ? '' : (tps[0] ?? 20) >= 18 ? 'text-warn' : 'text-danger');
    const memory = $derived(formatBytes(stats?.memory.used).split(' '));
    const sparkPoints = $derived.by(() => {
        if (tpsHistory.length < 2) return '';
        const width = 600;
        const height = 40;
        const pad = 2;
        const values = tpsHistory.slice(-SPARK_MAX);
        const step = (width - pad * 2) / (SPARK_MAX - 1);
        const offset = SPARK_MAX - values.length;
        return values
            .map((value, index) => {
                const x = pad + (index + offset) * step;
                const clamped = Math.max(15, Math.min(20, value));
                const y = pad + (height - pad * 2) * (1 - (clamped - 15) / 5);
                return `${x.toFixed(1)},${y.toFixed(1)}`;
            })
            .join(' ');
    });

    function pct(value: number | null | undefined) {
        if (!Number.isFinite(value ?? NaN)) return '-';
        return `${((value as number) * 100).toFixed(1)}%`;
    }

    function tpsText(value: number | undefined) {
        if (!Number.isFinite(value ?? NaN)) return '-';
        return Math.min(value as number, 20).toFixed(2);
    }

    function loadTone(percent: number) {
        return percent < 70 ? 'bg-muted' : percent < 90 ? 'bg-warn' : 'bg-danger';
    }

    onMount(() => {
        timer = window.setInterval(() => (now = Date.now()), 1000);
        es = new EventSource('/api/stats/stream');
        es.addEventListener('open', () => (connected = true));
        es.addEventListener('error', () => (connected = false));
        es.addEventListener('message', (event) => {
            try {
                stats = JSON.parse(event.data) as StatsPayload;
                connected = true;
                const currentTps = stats.server.tps[0];
                if (Number.isFinite(currentTps)) tpsHistory = [...tpsHistory.slice(-(SPARK_MAX - 1)), currentTps];
            } catch {
            }
        });
    });

    onDestroy(() => {
        es?.close();
        if (timer) window.clearInterval(timer);
    });
</script>

{#snippet meter(percent: number, tone: string, label: string)}
    <div class="mt-4 h-1.5 overflow-hidden rounded-full bg-sunken" role="meter" aria-label={label}
         aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(percent)}>
        <div class={cn('h-full rounded-full transition-[width] duration-700 ease-out', tone)} style:width={`${percent}%`}></div>
    </div>
{/snippet}

<PageHeader title="Overview">
    {#snippet meta()}
        {#if !stats}
            <span role="status">Connecting…</span>
        {:else}
            <span>Minecraft {stats.server.version}</span>
        {/if}
        {#if stats && !connected}
            <span role="status" class="text-warn">Reconnecting…</span>
        {/if}
    {/snippet}
</PageHeader>

<div class="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
    <section aria-labelledby="players-heading" class="panel flex flex-col p-5">
        <h2 id="players-heading" class="font-medium text-muted">Players</h2>
        <p class="mt-2 text-3xl font-semibold tracking-tight tabular-nums">
            {stats?.players.online ?? '-'}<span class="ml-1.5 text-base font-normal text-muted">/ {stats?.players.max ?? '-'}</span>
        </p>
        {@render meter(playersPercent, 'bg-brand', 'Player slots used')}
        <a href="/players/" class="link mt-auto self-start pt-3">View players</a>
    </section>

    <section aria-labelledby="cpu-heading" class="panel flex flex-col p-5">
        <h2 id="cpu-heading" class="font-medium text-muted">CPU</h2>
        <p class="mt-2 text-3xl font-semibold tracking-tight tabular-nums">{pct(stats?.cpu.process)}</p>
        {@render meter(cpuPercent, loadTone(cpuPercent), 'Process CPU usage')}
        <p class="mt-auto flex justify-between gap-3 pt-3 text-muted tabular-nums">
            <span>{stats?.cpu.cores ?? '-'} cores</span>
            <span>System {pct(stats?.cpu.system)}</span>
        </p>
    </section>

    <section aria-labelledby="memory-heading" class="panel flex flex-col p-5">
        <h2 id="memory-heading" class="font-medium text-muted">Memory</h2>
        <p class="mt-2 text-3xl font-semibold tracking-tight tabular-nums">
            {memory[0]}<span class="ml-1.5 text-base font-normal text-muted">{memory[1] ?? ''}</span>
        </p>
        {@render meter(memoryPercent, loadTone(memoryPercent), 'Memory used')}
        <p class="mt-auto flex justify-between gap-3 pt-3 text-muted tabular-nums">
            <span>{memoryPercent ? memoryPercent.toFixed(1) : '-'}% used</span>
            <span>Max {formatBytes(stats?.memory.max)}</span>
        </p>
    </section>

    <section aria-labelledby="tps-heading" class="panel flex flex-col p-5">
        <h2 id="tps-heading" class="font-medium text-muted">Ticks per second</h2>
        <p class={cn('mt-2 text-3xl font-semibold tracking-tight tabular-nums', tpsTone)}>
            {tpsText(tps[0])}<span class="ml-1.5 text-base font-normal text-muted">/ 20</span>
        </p>
        <svg viewBox="0 0 600 40" preserveAspectRatio="none" class="mt-3 h-8 w-full overflow-visible text-brand" aria-hidden="true">
            <polyline fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round" stroke-linecap="round"
                      vector-effect="non-scaling-stroke" points={sparkPoints}/>
        </svg>
        <p class="mt-auto flex justify-between gap-3 pt-3 text-muted tabular-nums">
            <span>5 min {tpsText(tps[1])}</span>
            <span>15 min {tpsText(tps[2])}</span>
        </p>
    </section>
</div>

<section aria-labelledby="server-heading" class="panel mt-4 p-5">
    <h2 id="server-heading" class="sr-only">Server</h2>
    <dl class="grid grid-cols-2 gap-x-6 gap-y-5 sm:grid-cols-3 lg:grid-cols-5">
        <div>
            <dt class="font-medium text-muted">Uptime</dt>
            <dd class="mt-1 text-xl font-semibold tracking-tight tabular-nums">{uptime}</dd>
        </div>
        <div>
            <dt class="font-medium text-muted">Worlds</dt>
            <dd class="mt-1 text-xl font-semibold tracking-tight tabular-nums">{stats?.world.worlds ?? '-'}</dd>
        </div>
        <div>
            <dt class="font-medium text-muted">Loaded chunks</dt>
            <dd class="mt-1 text-xl font-semibold tracking-tight tabular-nums">{stats?.world.loadedChunks ?? '-'}</dd>
        </div>
        <div>
            <dt class="font-medium text-muted">Entities</dt>
            <dd class="mt-1 text-xl font-semibold tracking-tight tabular-nums">{stats?.world.entities ?? '-'}</dd>
        </div>
        <div>
            <dt class="font-medium text-muted">Active plugins</dt>
            <dd class="mt-1 text-xl font-semibold tracking-tight tabular-nums">{stats?.plugins.active ?? '-'}</dd>
        </div>
    </dl>
</section>
