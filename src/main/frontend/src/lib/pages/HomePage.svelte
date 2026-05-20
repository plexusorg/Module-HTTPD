<script lang="ts">
    import {onDestroy, onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {
        ChartNoAxesColumnIncreasingIcon,
        Clock03Icon,
        CpuIcon,
        CubeIcon,
        DatabaseIcon,
        ServerStack01Icon,
        UserGroupIcon
    } from '@hugeicons/core-free-icons';
    import {Card} from '$lib/components/ui/card';
    import {formatBytes, formatDuration} from '$lib/utils';
    import type {StatsPayload} from '$lib/types/api';

    const SPARK_MAX = 60;
    let stats = $state<StatsPayload | null>(null);
    let now = $state(Date.now());
    let tpsHistory: number[] = $state([]);
    let es: EventSource | null = null;
    let timer: number | null = null;

    const uptime = $derived(stats?.server.startTime ? formatDuration(now - stats.server.startTime) : '-');
    const memoryPercent = $derived(stats ? Math.max(0, Math.min(100, (stats.memory.used / stats.memory.max) * 100)) : 0);
    const cpuPercent = $derived(stats ? Math.max(0, Math.min(100, stats.cpu.process * 100)) : 0);
    const playersPercent = $derived(stats && stats.players.max > 0 ? Math.max(0, Math.min(100, (stats.players.online / stats.players.max) * 100)) : 0);
    const tps = $derived(stats?.server.tps ?? []);
    const tpsColor = $derived((tps[0] ?? 20) >= 19.5 ? 'text-success' : (tps[0] ?? 20) >= 18 ? 'text-warning' : 'text-destructive');
    const sparkPoints = $derived.by(() => {
        if (tpsHistory.length < 2) return '';
        const width = 600;
        const height = 60;
        const pad = 4;
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

    onMount(() => {
        timer = window.setInterval(() => (now = Date.now()), 1000);
        es = new EventSource('/api/stats/stream');
        es.addEventListener('message', (event) => {
            try {
                stats = JSON.parse(event.data) as StatsPayload;
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

<section class="rise flex flex-wrap items-end justify-between gap-3">
    <div>
        <h1 class="text-3xl font-medium tracking-tight md:text-4xl">Overview</h1>
        <p class="mt-1 text-sm text-muted-foreground">Minecraft version <span
                class="text-foreground">{stats?.server.version ?? '-'}</span></p>
    </div>
</section>

<section class="mt-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
    <Card class="rise flex min-h-32 flex-col p-4">
        <div class="flex items-center justify-between">
            <span class="text-sm text-muted-foreground">Players</span>
            <HugeiconsIcon icon={UserGroupIcon} class="size-4 text-muted-foreground"/>
        </div>
        <div class="mt-3 flex items-baseline gap-2">
            <span class="tabular text-3xl font-medium tracking-tight">{stats?.players.online ?? '-'}</span>
            <span class="text-sm text-muted-foreground">/ {stats?.players.max ?? '-'}</span>
        </div>
        <div class="mt-3 h-1 overflow-hidden rounded-full bg-muted">
            <div class="h-full rounded-full bg-primary transition-[width] duration-500"
                 style:width={`${playersPercent}%`}></div>
        </div>
        <a href="/players/" class="mt-auto pt-2 text-xs text-primary hover:underline">view list</a>
    </Card>

    <Card class="rise flex min-h-32 flex-col p-4">
        <div class="flex items-center justify-between">
            <span class="text-sm text-muted-foreground">CPU</span>
            <HugeiconsIcon icon={CpuIcon} class="size-4 text-muted-foreground"/>
        </div>
        <div class="mt-3 tabular text-3xl font-medium tracking-tight">{pct(stats?.cpu.process)}</div>
        <div class="mt-3 h-1 overflow-hidden rounded-full bg-muted">
            <div class="h-full rounded-full transition-[width] duration-500 {cpuPercent < 70 ? 'bg-primary' : cpuPercent < 90 ? 'bg-warning' : 'bg-destructive'}"
                 style:width={`${cpuPercent}%`}></div>
        </div>
        <div class="mt-auto flex justify-between pt-2 text-xs text-muted-foreground">
            <span>{stats?.cpu.cores ?? '-'} cores</span>
            <span>system {pct(stats?.cpu.system)}</span>
        </div>
    </Card>

    <Card class="rise flex min-h-32 flex-col p-4">
        <div class="flex items-center justify-between">
            <span class="text-sm text-muted-foreground">Memory</span>
            <HugeiconsIcon icon={DatabaseIcon} class="size-4 text-muted-foreground"/>
        </div>
        <div class="mt-3 flex items-baseline gap-2">
            <span class="tabular text-3xl font-medium tracking-tight">{formatBytes(stats?.memory.used).split(' ')[0]}</span>
            <span class="text-sm text-muted-foreground">{formatBytes(stats?.memory.used).split(' ')[1] ?? ''}</span>
        </div>
        <div class="mt-3 h-1 overflow-hidden rounded-full bg-muted">
            <div class="h-full rounded-full transition-[width] duration-500 {memoryPercent < 70 ? 'bg-primary' : memoryPercent < 90 ? 'bg-warning' : 'bg-destructive'}"
                 style:width={`${memoryPercent}%`}></div>
        </div>
        <div class="mt-auto flex justify-between pt-2 text-xs text-muted-foreground">
            <span>{memoryPercent ? memoryPercent.toFixed(1) : '-'}%</span>
            <span>max {formatBytes(stats?.memory.max)}</span>
        </div>
    </Card>

    <Card class="rise flex min-h-32 flex-col p-4">
        <div class="flex items-center justify-between">
            <span class="text-sm text-muted-foreground">Ticks per second</span>
            <HugeiconsIcon icon={ChartNoAxesColumnIncreasingIcon} class="size-4 text-muted-foreground"/>
        </div>
        <div class="mt-3 flex items-baseline gap-2">
            <span class="tabular text-3xl font-medium tracking-tight {tpsColor}">{tpsText(tps[0])}</span>
            <span class="text-sm text-muted-foreground">/ 20.00</span>
        </div>
        <svg viewBox="0 0 600 60" preserveAspectRatio="none" class="mt-2 h-9 w-full overflow-visible text-primary">
            <polyline fill="none" stroke="currentColor" stroke-width="1.5" stroke-linejoin="round"
                      stroke-linecap="round" points={sparkPoints}/>
        </svg>
        <div class="mt-auto flex justify-between text-xs text-muted-foreground">
            <span>5m {tpsText(tps[1])}</span>
            <span>15m {tpsText(tps[2])}</span>
        </div>
    </Card>
</section>

<section class="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
    <Card class="rise p-4">
        <div class="flex items-center justify-between">
            <span class="text-sm text-muted-foreground">Uptime</span>
            <HugeiconsIcon icon={Clock03Icon} class="size-4 text-muted-foreground"/>
        </div>
        <div class="mt-2 font-mono text-3xl font-medium tracking-tight md:text-4xl">{uptime}</div>
    </Card>

    <Card class="rise p-4">
        <div class="flex items-center justify-between">
            <span class="text-sm text-muted-foreground">World</span>
            <HugeiconsIcon icon={CubeIcon} class="size-4 text-muted-foreground"/>
        </div>
        <dl class="mt-2 grid grid-cols-3 gap-2 text-center">
            <div>
                <dt class="text-xs text-muted-foreground">Worlds</dt>
                <dd class="tabular text-3xl font-medium">{stats?.world.worlds ?? '-'}</dd>
            </div>
            <div>
                <dt class="text-xs text-muted-foreground">Chunks</dt>
                <dd class="tabular text-3xl font-medium">{stats?.world.loadedChunks ?? '-'}</dd>
            </div>
            <div>
                <dt class="text-xs text-muted-foreground">Entities</dt>
                <dd class="tabular text-3xl font-medium">{stats?.world.entities ?? '-'}</dd>
            </div>
        </dl>
    </Card>

    <Card class="rise p-4">
        <div class="flex items-center justify-between">
            <span class="text-sm text-muted-foreground">Plugins</span>
            <HugeiconsIcon icon={ServerStack01Icon} class="size-4 text-muted-foreground"/>
        </div>
        <div class="mt-2 flex items-baseline gap-2">
            <span class="tabular text-3xl font-medium">{stats?.plugins.active ?? '-'}</span>
            <span class="text-sm text-muted-foreground">active</span>
        </div>
        <div class="mt-3 flex gap-2">
            <a href="/commands/"
               class="rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground hover:text-foreground">commands</a>
            <a href="/schematics/"
               class="rounded-full bg-muted px-3 py-1 text-xs text-muted-foreground hover:text-foreground">schematics</a>
        </div>
    </Card>
</section>
