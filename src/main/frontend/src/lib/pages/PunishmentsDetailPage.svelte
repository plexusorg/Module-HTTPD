<script lang="ts">
    import {onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowLeft01Icon, ArrowDown01Icon, Search01Icon} from '@hugeicons/core-free-icons';
    import {api} from '$lib/api';
    import {Badge} from '$lib/components/ui/badge';
    import {Button} from '$lib/components/ui/button';
    import PlayerHead from '$lib/components/ui/PlayerHead.svelte';
    import {Card} from '$lib/components/ui/card';
    import {Input} from '$lib/components/ui/input';
    import type {PunishmentSummary, PunishmentsPayload} from '$lib/types/api';
    import {lowerSearch, titleCase} from '$lib/utils';

    interface Props {
        id: string;
    }

    let {id}: Props = $props();
    let data = $state<PunishmentsPayload | null>(null);
    let loading = $state(true);
    let loadingMore = $state(false);
    let error = $state<string | null>(null);
    let loadMoreError = $state<string | null>(null);
    let filter = $state('');
    let type = $state('all');
    let status = $state('all');

    const filtered = $derived(filter.trim() !== '' || type !== 'all' || status !== 'all');
    const punishments = $derived<PunishmentSummary[]>(data?.punishments ?? []);
    const types = $derived<string[]>(Array.from(new Set(punishments.map((item) => item.type).filter(Boolean))).sort());
    const visible = $derived.by(() => {
        const q = filter.toLowerCase().trim();
        return punishments.filter((item) => {
            const itemType = item.type;
            const itemStatus = punishmentStatus(item);
            return (!q || lowerSearch(item).includes(q)) && (type === 'all' || itemType === type) && (status === 'all' || itemStatus === status);
        });
    });

    function punishmentStatus(item: PunishmentSummary) {
        if (item.type === 'KICK' || item.type === 'SMITE') return 'completed';
        if (item.active) return 'active';
        if (item.endDate !== null && item.endDate <= Date.now()) return 'expired';
        return 'revoked';
    }

    function formatDate(value: number) {
        const date = new Date(value);
        return Number.isNaN(date.getTime()) ? 'Outside supported date range' : date.toLocaleString();
    }

    function expiry(item: PunishmentSummary) {
        if (item.type === 'KICK' || item.type === 'SMITE') return 'Not applicable';
        if (item.endDate !== null) return formatDate(item.endDate);
        return item.type === 'BAN' || item.type === 'TEMPBAN' ? 'Missing expiry (invalid ban)' : 'Never';
    }

    function typeLabel(value: string) {
        return value === 'TEMPBAN' ? 'Temporary ban' : titleCase(value);
    }

    async function loadMore() {
        if (!data?.pagination.hasMore || loadingMore) return;
        loadingMore = true;
        loadMoreError = null;
        try {
            const next = await api.punishments(id, data.punishments.length);
            data = {...next, punishments: [...data.punishments, ...next.punishments]};
        } catch (cause) {
            loadMoreError = cause instanceof Error ? cause.message : 'Unable to load punishments.';
        } finally {
            loadingMore = false;
        }
    }

    async function load() {
        loading = true;
        error = null;
        try {
            data = await api.punishments(id);
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Unable to load punishments.';
        } finally {
            loading = false;
        }
    }

    onMount(() => { void load(); });
</script>

<nav aria-label="Punishment navigation" class="mb-6">
    <Button href="/punishments/" variant="outline" size="lg" class="transition-colors">
        <HugeiconsIcon icon={ArrowLeft01Icon} class="size-4"/>
        Back to player search
    </Button>
</nav>

{#if loading}
    <section class="rise py-10" role="status">
        <h1 class="text-2xl font-medium tracking-tight">Punishment history</h1>
        <p class="mt-2 text-sm text-muted-foreground">Loading records for {id}…</p>
    </section>
{:else if error}
    <Card class="rise gap-3 p-6">
        <h1 class="text-xl font-medium">Couldn't load this history</h1>
        <p class="break-words text-sm text-destructive" role="alert">{error}</p>
        <div><Button variant="secondary" size="lg" class="transition-colors" onclick={load}>Try again</Button></div>
    </Card>
{:else if data}
    <header class="rise flex flex-wrap items-end justify-between gap-5">
        <div class="flex min-w-0 items-center gap-4">
            <PlayerHead uuid={data.player.uuid} size={56}/>
            <div class="min-w-0">
                <p class="mb-1 text-xs font-medium uppercase tracking-widest text-muted-foreground">Punishment history</p>
                <h1 class="break-words text-3xl font-medium tracking-tight md:text-4xl">{data.player.name}</h1>
                <p class="mt-2 break-all font-mono text-xs text-muted-foreground">{data.player.uuid}</p>
            </div>
        </div>
        <p class="text-sm text-muted-foreground"><strong class="font-medium tabular-nums text-foreground">{data.pagination.total}</strong> total records</p>
    </header>

    {#if data.pagination.total === 0}
        <Card class="mt-8 gap-2 p-8">
            <h2 class="text-lg font-medium">No punishments recorded</h2>
            <p class="text-sm text-muted-foreground">There are no punishment records for this player UUID.</p>
            <p class="text-sm text-muted-foreground">Looking for someone else? Use the player search above.</p>
        </Card>
    {:else}
        <section aria-label="Filter punishment history" class="mt-8 rounded-xl bg-muted/40 p-4">
            <div class="grid gap-4 sm:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_11rem_11rem]">
                <div class="sm:col-span-2 lg:col-span-1">
                    <label for="history-search" class="mb-2 block text-xs font-medium">Search loaded records</label>
                    <div class="relative">
                        <HugeiconsIcon icon={Search01Icon} class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"/>
                        <Input id="history-search" bind:value={filter} type="search"
                               placeholder={data.canViewIps ? 'Reason, punisher, type or IP…' : 'Reason, punisher or type…'}
                               autocomplete="off" class="h-10 pl-9"/>
                    </div>
                </div>
                <div>
                    <label for="history-type" class="mb-2 block text-xs font-medium">Punishment type</label>
                    <select id="history-type" bind:value={type} class="history-select">
                        <option value="all">All types</option>
                        {#each types as item (item)}<option value={item}>{typeLabel(item)}</option>{/each}
                    </select>
                </div>
                <div>
                    <label for="history-status" class="mb-2 block text-xs font-medium">Status</label>
                    <select id="history-status" bind:value={status} class="history-select">
                        <option value="all">All statuses</option>
                        {#each ['active', 'expired', 'revoked', 'completed'] as item (item)}
                            <option value={item}>{titleCase(item)}</option>
                        {/each}
                    </select>
                </div>
            </div>
            <div class="mt-3 flex min-h-10 flex-wrap items-center justify-between gap-x-4 text-xs text-muted-foreground">
                <p aria-live="polite"><span class="tabular-nums">{visible.length}</span> matching · <span class="tabular-nums">{punishments.length}</span> of <span class="tabular-nums">{data.pagination.total}</span> records loaded</p>
                {#if filtered}
                    <Button variant="ghost" size="lg" class="text-xs transition-colors" onclick={() => { filter = ''; type = 'all'; status = 'all'; }}>Clear filters</Button>
                {/if}
            </div>
            {#if data.pagination.hasMore}
                <p class="text-xs text-muted-foreground">Filters apply to loaded records. Load more below to search older history.</p>
            {/if}
        </section>

        <section aria-labelledby="history-heading" class="mt-7">
            <div class="mb-3 flex flex-wrap items-baseline justify-between gap-2">
                <h2 id="history-heading" class="text-lg font-medium">History</h2>
                <p class="text-xs text-muted-foreground">Newest first · Select a record for details</p>
            </div>
            {#if visible.length === 0}
                <div class="rounded-xl border border-dashed border-border px-5 py-10 text-center">
                    <h3 class="font-medium">No matching records{data.pagination.hasMore ? ' loaded' : ''}</h3>
                    <p class="mt-2 text-sm text-muted-foreground">{data.pagination.hasMore ? 'Clear your filters or load more records below.' : 'Try a different search or clear your filters.'}</p>
                </div>
            {:else}
                <div class="overflow-hidden rounded-xl bg-card shadow-sm ring-1 ring-border/60">
                    <div aria-hidden="true" class="history-row bg-muted/40 px-4 py-3 text-xs font-medium text-muted-foreground">
                        <span>Punishment / reason</span><span>Issued by</span><span>Issued</span><span>Status</span><span></span>
                    </div>
                    {#each visible as punishment, index (`${punishment.type}:${punishment.issueDate}:${index}`)}
                        {@const itemStatus = punishmentStatus(punishment)}
                        <details class="history-record border-t border-border/60 first:border-t-0">
                            <summary class="history-row cursor-pointer list-none items-center gap-y-2 px-4 py-4 transition-colors hover:bg-muted/40 focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-ring">
                                <span class="min-w-0">
                                    <span class="block text-sm font-medium">{typeLabel(punishment.type)}</span>
                                    <span class="mt-1 line-clamp-2 break-words text-sm text-muted-foreground">{punishment.reason || 'No reason provided'}</span>
                                    <span class="mt-2 block text-xs tabular-nums text-muted-foreground md:hidden">{formatDate(punishment.issueDate)}</span>
                                </span>
                                <span class="hidden break-words text-sm md:block">{punishment.punisherDisplayName || 'Unknown'}</span>
                                <span class="hidden text-xs tabular-nums text-muted-foreground md:block">{formatDate(punishment.issueDate)}</span>
                                <span><Badge variant={itemStatus === 'active' ? 'destructive' : 'secondary'}>{titleCase(itemStatus)}</Badge></span>
                                <HugeiconsIcon icon={ArrowDown01Icon} class="history-chevron size-4 text-muted-foreground"/>
                            </summary>
                            <div class="border-t border-border/60 bg-muted/20 p-5">
                                <p class="text-xs font-medium text-muted-foreground">Reason</p>
                                <p class="mt-2 whitespace-pre-wrap break-words text-sm leading-relaxed">{punishment.reason || 'No reason provided'}</p>
                                <dl class="mt-5 grid gap-x-8 gap-y-5 text-sm sm:grid-cols-2 lg:grid-cols-3">
                                    <div><dt class="text-xs text-muted-foreground">Issued by</dt><dd class="mt-1 break-words">{punishment.punisherDisplayName || 'Unknown'}</dd></div>
                                    <div><dt class="text-xs text-muted-foreground">Issued</dt><dd class="mt-1 tabular-nums" title={String(punishment.issueDate)}>{formatDate(punishment.issueDate)}</dd></div>
                                    <div><dt class="text-xs text-muted-foreground">Expires</dt><dd class="mt-1 tabular-nums" title={punishment.endDate === null ? undefined : String(punishment.endDate)}>{expiry(punishment)}</dd></div>
                                    <div><dt class="text-xs text-muted-foreground">Source</dt><dd class="mt-1">{titleCase(punishment.source)}</dd></div>
                                    {#if punishment.punisher}
                                        <div><dt class="text-xs text-muted-foreground">Punisher UUID</dt><dd class="mt-1 break-all font-mono text-xs leading-5">{punishment.punisher}</dd></div>
                                    {/if}
                                    {#if punishment.punisherReference}
                                        <div><dt class="text-xs text-muted-foreground">Actor reference</dt><dd class="mt-1 break-all">{punishment.punisherReference}</dd></div>
                                    {/if}
                                    {#if data.canViewIps && punishment.ip}
                                        <div><dt class="text-xs text-muted-foreground">IP address</dt><dd class="mt-1 break-all font-mono text-xs leading-5">{punishment.ip}</dd></div>
                                    {/if}
                                </dl>
                            </div>
                        </details>
                    {/each}
                </div>
            {/if}
        </section>

        <footer class="mt-5 flex flex-wrap items-center justify-between gap-3">
            <p class="text-xs tabular-nums text-muted-foreground">{punishments.length} of {data.pagination.total} records loaded</p>
            {#if data.pagination.hasMore}
                <Button variant="outline" size="lg" class="transition-colors" disabled={loadingMore} onclick={loadMore}>
                    {loadingMore ? 'Loading records…' : 'Load more records'}
                </Button>
            {:else}
                <span class="text-xs text-muted-foreground">End of history</span>
            {/if}
        </footer>
        {#if loadMoreError}<p class="mt-3 text-sm text-destructive" role="alert">{loadMoreError}</p>{/if}
    {/if}
{/if}

<style>
    .history-select {
        width: 100%;
        height: 2.5rem;
        border: 1px solid var(--input);
        border-radius: 0.5rem;
        padding: 0 0.75rem;
        background: var(--background);
        color: var(--foreground);
        font-size: 0.875rem;
    }
    .history-select:focus-visible {
        outline: 2px solid var(--ring);
        outline-offset: 2px;
    }
    .history-row {
        display: grid;
        grid-template-columns: minmax(0, 1fr) auto 1rem;
        column-gap: 1rem;
    }
    .history-row[aria-hidden] { display: none; }
    summary::-webkit-details-marker { display: none; }
    .history-record[open] :global(.history-chevron) { transform: rotate(180deg); }
    @media (min-width: 768px) {
        .history-row { grid-template-columns: minmax(0, 1fr) 9rem 10rem 6rem 1rem; }
        .history-row[aria-hidden] { display: grid; }
    }
</style>
