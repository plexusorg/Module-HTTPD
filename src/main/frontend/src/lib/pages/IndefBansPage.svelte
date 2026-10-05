<script lang="ts">
    import {onMount} from 'svelte';
    import {api} from '$lib/api';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import Button from '$lib/components/ui/Button.svelte';
    import Notice from '$lib/components/ui/Notice.svelte';
    import SearchField from '$lib/components/ui/SearchField.svelte';
    import {lowerSearch} from '$lib/utils';

    interface BanGroup {
        usernames: string[];
        uuids: string[];
        ips: string[];
        reason: string;
    }

    let bans: Array<Record<string, unknown>> = $state([]);
    let loading = $state(true);
    let error = $state<string | null>(null);
    let filter = $state('');

    const groups = $derived(bans.map(toGroup));
    const visible = $derived.by(() => {
        const q = filter.toLowerCase().trim();
        return groups.filter((group) => !q || lowerSearch(group).includes(q));
    });
    const totals = $derived.by(() => {
        return {
            groups: groups.length,
            users: groups.reduce((total, group) => total + group.usernames.length, 0),
            uuids: groups.reduce((total, group) => total + group.uuids.length, 0),
            ips: groups.reduce((total, group) => total + group.ips.length, 0)
        };
    });

    function isRecord(value: unknown): value is Record<string, unknown> {
        return typeof value === 'object' && value !== null && !Array.isArray(value);
    }

    function listValues(value: unknown): string[] {
        if (value == null) return [];
        if (Array.isArray(value)) return value.flatMap(listValues);
        if (typeof value === 'string') {
            const trimmed = value.trim();
            return trimmed ? [trimmed] : [];
        }
        if (typeof value === 'number' || typeof value === 'boolean') return [String(value)];
        if (isRecord(value)) return Object.values(value).flatMap(listValues);
        return [];
    }

    function toGroup(ban: Record<string, unknown>): BanGroup {
        const entries = Object.entries(ban);
        const nested = entries.length === 1 && isRecord(entries[0][1]) ? entries[0][1] : ban;
        const reason = listValues(nested.reason).join(', ');

        return {
            usernames: listValues(nested.usernames ?? nested.users ?? nested.names),
            uuids: listValues(nested.uuids ?? nested.uuid),
            ips: listValues(nested.ips ?? nested.ip),
            reason: reason || listValues(ban.reason).join(', ')
        };
    }

    function entryCount(group: BanGroup): number {
        return group.usernames.length + group.uuids.length + group.ips.length;
    }

    function groupKey(group: BanGroup, index: number): string {
        return `${index}:${group.usernames[0] ?? ''}:${group.uuids[0] ?? ''}:${group.ips[0] ?? ''}`;
    }

    async function load() {
        loading = true;
        error = null;
        try {
            bans = await api.indefiniteBans();
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Unable to load indefinite bans.';
        } finally {
            loading = false;
        }
    }

    onMount(load);
</script>

<PageHeader title="Indefinite bans" trail={[{href: '/', label: 'Overview'}]}>
    {#snippet meta()}
        {#if !loading && !error}
            <span class="tabular-nums">{totals.groups} groups · {totals.users} users · {totals.uuids} UUIDs · {totals.ips} IPs</span>
        {/if}
    {/snippet}
</PageHeader>

{#if loading}
    <Notice kind="loading" title="Loading indefinite bans"/>
{:else if error}
    <Notice kind="error" title="Couldn't load indefinite bans" message={error}>
        <Button variant="primary" onclick={load}>Try again</Button>
    </Notice>
{:else if groups.length === 0}
    <Notice kind="empty" title="No indefinite bans" message="The server config does not list any indefinite bans."/>
{:else}
    <SearchField bind:value={filter} label="Filter bans" placeholder="Name, UUID, IP or reason" hideLabel class="mb-4"/>

    {#if visible.length === 0}
        <Notice kind="empty" title="No match" message={`No indefinite ban matches "${filter.trim()}".`}>
            <Button onclick={() => (filter = '')}>Clear filter</Button>
        </Notice>
    {:else}
        <ul class="panel divide-y divide-line">
            {#each visible as group, index (groupKey(group, index))}
                {@const total = entryCount(group)}
                <li class="min-w-0 px-5 py-4">
                    <div class="flex items-start justify-between gap-4">
                        <p class="min-w-0 break-words font-medium">
                            {#if group.reason}
                                {group.reason}
                            {:else}
                                <span class="font-normal text-faint">No reason provided</span>
                            {/if}
                        </p>
                        <span class="shrink-0 text-muted tabular-nums">{total} {total === 1 ? 'entry' : 'entries'}</span>
                    </div>
                    <dl class="mt-3 grid grid-cols-[4rem_minmax(0,1fr)] gap-x-4 gap-y-2">
                        {#if group.usernames.length}
                            <dt class="text-muted">Users</dt>
                            <dd class="break-words">{group.usernames.join(', ')}</dd>

                        {/if}
                        {#if group.uuids.length}
                            <dt class="text-muted">UUIDs</dt>
                            <dd class="flex flex-col gap-1 font-mono text-[0.8125rem]">
                                {#each group.uuids as uuid, uuidIndex (`${uuid}:${uuidIndex}`)}
                                    <span class="break-all">{uuid}</span>
                                {/each}
                            </dd>
                        {/if}
                        {#if group.ips.length}
                            <dt class="text-muted">IPs</dt>
                            <dd class="flex flex-wrap gap-x-3 gap-y-1 font-mono text-[0.8125rem]">
                                {#each group.ips as ip, ipIndex (`${ip}:${ipIndex}`)}
                                    <span class="break-all">{ip}</span>
                                {/each}
                            </dd>
                        {/if}
                    </dl>
                </li>
            {/each}
        </ul>
    {/if}
{/if}