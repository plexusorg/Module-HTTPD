<script lang="ts">
    import {onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowDown01Icon, Shield01Icon} from '@hugeicons/core-free-icons';
    import {api} from '$lib/api';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import PlayerLookup from '$lib/components/PlayerLookup.svelte';
    import Button from '$lib/components/ui/Button.svelte';
    import Notice from '$lib/components/ui/Notice.svelte';
    import PlayerHead from '$lib/components/ui/PlayerHead.svelte';
    import SearchField from '$lib/components/ui/SearchField.svelte';
    import Select from '$lib/components/ui/Select.svelte';
    import Tag from '$lib/components/ui/Tag.svelte';
    import {rememberLookup} from '$lib/recentLookups';
    import type {PunishmentSummary, PunishmentsPayload} from '$lib/types/api';
    import {lowerSearch, titleCase} from '$lib/utils';

    interface Props {
        id: string;
        staff: boolean;
    }

    let {id, staff}: Props = $props();
    let data = $state<PunishmentsPayload | null>(null);
    let loading = $state(true);
    let loadingMore = $state(false);
    let error = $state<string | null>(null);
    let loadMoreError = $state<string | null>(null);
    let filter = $state('');
    let type = $state('all');
    let status = $state('all');

    const trail = [{href: '/', label: 'Overview'}, {href: '/punishments/', label: 'Punishments'}];
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
    const activeCount = $derived(punishments.filter((item) => punishmentStatus(item) === 'active').length);

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

    function clearFilters() {
        filter = '';
        type = 'all';
        status = 'all';
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
            rememberLookup({uuid: data.player.uuid, name: data.player.name});
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Unable to load punishments.';
        } finally {
            loading = false;
        }
    }

    onMount(() => { void load(); });
</script>

{#snippet lookup(initial: string)}
    <PlayerLookup label="Look up another player" {initial} class="sm:w-80"/>
{/snippet}

{#if loading}
    <PageHeader title={id} {trail}>
        {#snippet actions()}{@render lookup('')}{/snippet}
    </PageHeader>
    <Notice kind="loading" title="Loading punishment history" message={`Fetching records for ${id}.`}/>
{:else if error}
    <PageHeader title={id} {trail}>
        {#snippet actions()}{@render lookup(id)}{/snippet}
    </PageHeader>
    <Notice kind="error" title="Couldn't load this history" message={error}>
        <Button variant="primary" onclick={load}>Try again</Button>
        <Button href="/punishments/">Back to search</Button>
    </Notice>
{:else if data}
    <PageHeader title={data.player.name} {trail}>
        {#snippet lead()}
            <PlayerHead uuid={data!.player.uuid} size={48}/>
        {/snippet}
        {#snippet meta()}
            <span class="w-full break-all font-mono text-[0.8125rem]">{data!.player.uuid}</span>
            <span class="tabular-nums">{data!.pagination.total} {data!.pagination.total === 1 ? 'record' : 'records'}</span>
            {#if activeCount}
                <Tag tone="danger" dot>{activeCount} active</Tag>
            {/if}
            {#if staff}
                <a href={`/player/${encodeURIComponent(data!.player.uuid)}`} class="link">Open player admin</a>
            {/if}
        {/snippet}
        {#snippet actions()}{@render lookup('')}{/snippet}
    </PageHeader>

    {#if data.pagination.total === 0}
        <Notice kind="empty" title="Clean record" message={`There are no punishment records for ${data.player.name}.`}/>
    {:else}
        <section aria-labelledby="history-heading">
            <div class="mb-4 flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
                <h2 id="history-heading" class="text-base font-semibold">History</h2>
                <p class="text-muted tabular-nums" aria-live="polite">
                    {visible.length} shown · {punishments.length} of {data.pagination.total} loaded
                </p>
            </div>

            <div class="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_11rem_11rem_auto] lg:items-end">
                <SearchField bind:value={filter} id="history-search" class="sm:col-span-2 lg:col-span-1"
                             label={`Filter ${data.player.name}'s records`}
                             placeholder={data.canViewIps ? 'Reason, punisher, type or IP' : 'Reason, punisher or type'}/>
                <Select id="history-type" label="Type" bind:value={type}>
                    <option value="all">All types</option>
                    {#each types as item (item)}<option value={item}>{typeLabel(item)}</option>{/each}
                </Select>
                <Select id="history-status" label="Status" bind:value={status}>
                    <option value="all">All statuses</option>
                    {#each ['active', 'expired', 'revoked', 'completed'] as item (item)}
                        <option value={item}>{titleCase(item)}</option>
                    {/each}
                </Select>
                <Button variant="ghost" onclick={clearFilters} disabled={!filtered} class="sm:col-span-2 lg:col-span-1">Clear</Button>
            </div>
            {#if data.pagination.hasMore}
                <p class="-mt-1 mb-4 text-[0.8125rem] text-muted">Filters only search loaded records. Load more below to include older history.</p>
            {/if}

            {#if visible.length === 0}
                <Notice kind="empty" title={`No matching records${data.pagination.hasMore ? ' loaded' : ''}`}
                        message={data.pagination.hasMore ? 'Clear the filters, or load more records below.' : 'Try a different filter, or clear the filters.'}>
                    <Button onclick={clearFilters}>Clear filters</Button>
                </Notice>
            {:else}
                <div class="panel overflow-hidden">
                    <div aria-hidden="true" class="history-row hidden border-b border-line px-4 py-2.5 text-[0.8125rem] font-medium text-muted md:grid">
                        <span>Punishment</span><span>Issued by</span><span>Issued</span><span>Status</span><span></span>
                    </div>
                    {#each visible as punishment, index (`${punishment.type}:${punishment.issueDate}:${index}`)}
                        {@const itemStatus = punishmentStatus(punishment)}
                        <details class="group border-t border-line first-of-type:border-t-0">
                            <summary class="history-row grid cursor-pointer items-center gap-y-1 px-4 py-3 transition-colors hover:bg-sunken/60">
                                <span class="min-w-0">
                                    <span class="block font-medium">{typeLabel(punishment.type)}</span>
                                    <span class="mt-0.5 line-clamp-1 break-words text-muted">{punishment.reason || 'No reason provided'}</span>
                                    <span class="mt-0.5 block text-[0.8125rem] text-muted tabular-nums md:hidden">{formatDate(punishment.issueDate)} · {punishment.punisherDisplayName || 'Unknown'}</span>
                                </span>
                                <span class="hidden truncate md:block">{punishment.punisherDisplayName || 'Unknown'}</span>
                                <span class="hidden text-muted tabular-nums md:block">{formatDate(punishment.issueDate)}</span>
                                <span><Tag tone={itemStatus === 'active' ? 'danger' : 'neutral'}>{titleCase(itemStatus)}</Tag></span>
                                <HugeiconsIcon icon={ArrowDown01Icon} class="size-4 text-faint transition-transform group-open:rotate-180"/>
                            </summary>
                            <dl class="grid gap-x-8 gap-y-4 border-t border-line bg-sunken/40 px-4 py-4 sm:grid-cols-2 lg:grid-cols-3">
                                <div class="sm:col-span-2 lg:col-span-3">
                                    <dt class="text-[0.8125rem] text-muted">Reason</dt>
                                    <dd class="mt-0.5 whitespace-pre-wrap break-words leading-relaxed">{punishment.reason || 'No reason provided'}</dd>
                                </div>
                                <div><dt class="text-[0.8125rem] text-muted">Issued by</dt><dd class="mt-0.5 break-words">{punishment.punisherDisplayName || 'Unknown'}</dd></div>
                                <div><dt class="text-[0.8125rem] text-muted">Issued</dt><dd class="mt-0.5 tabular-nums" title={String(punishment.issueDate)}>{formatDate(punishment.issueDate)}</dd></div>
                                <div><dt class="text-[0.8125rem] text-muted">Expires</dt><dd class="mt-0.5 tabular-nums" title={punishment.endDate === null ? undefined : String(punishment.endDate)}>{expiry(punishment)}</dd></div>
                                <div><dt class="text-[0.8125rem] text-muted">Source</dt><dd class="mt-0.5">{titleCase(punishment.source)}</dd></div>
                                {#if punishment.punisher}
                                    <div><dt class="text-[0.8125rem] text-muted">Punisher UUID</dt><dd class="mt-0.5 break-all font-mono text-[0.8125rem]">{punishment.punisher}</dd></div>
                                {/if}
                                {#if punishment.punisherReference}
                                    <div><dt class="text-[0.8125rem] text-muted">Actor reference</dt><dd class="mt-0.5 break-all">{punishment.punisherReference}</dd></div>
                                {/if}
                                {#if data.canViewIps && punishment.ip}
                                    <div><dt class="text-[0.8125rem] text-muted">IP address</dt><dd class="mt-0.5 break-all font-mono text-[0.8125rem]">{punishment.ip}</dd></div>
                                {/if}
                            </dl>
                        </details>
                    {/each}
                </div>
            {/if}

            {#if data.pagination.hasMore}
                <div class="mt-4 flex justify-center">
                    <Button disabled={loadingMore} onclick={loadMore}>
                        {loadingMore ? 'Loading records…' : 'Load more records'}
                    </Button>
                </div>
            {/if}
            {#if loadMoreError}
                <p class="mt-3 rounded-md bg-danger-soft px-3 py-2 text-danger" role="alert">{loadMoreError}</p>
            {/if}
        </section>
    {/if}
{/if}

<style>
    .history-row {
        grid-template-columns: minmax(0, 1fr) auto 1rem;
        column-gap: 1rem;
    }

    @media (min-width: 768px) {
        .history-row {
            grid-template-columns: minmax(0, 1fr) 10rem 11rem 6rem 1rem;
        }
    }
</style>