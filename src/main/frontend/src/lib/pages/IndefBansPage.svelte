<script lang="ts">
    import {onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {Search01Icon} from '@hugeicons/core-free-icons';
    import {api} from '$lib/api';
    import {Card} from '$lib/components/ui/card';
    import {Input} from '$lib/components/ui/input';
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

    onMount(async () => {
        try {
            bans = await api.indefiniteBans();
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Unable to load indefinite bans.';
        } finally {
            loading = false;
        }
    });
</script>

<section class="rise flex flex-wrap items-end justify-between gap-3">
    <h1 class="text-3xl font-medium tracking-tight md:text-4xl">Indefinite bans</h1>
    <div class="flex flex-wrap items-center gap-4 text-sm text-muted-foreground tabular">
        <span><span class="text-foreground">{totals.groups}</span> groups</span>
        <span><span class="text-foreground">{totals.users}</span> users</span>
        <span><span class="text-foreground">{totals.uuids}</span> uuids</span>
        <span><span class="text-foreground">{totals.ips}</span> ips</span>
    </div>
</section>

<section class="rise mt-6">
    <div class="relative w-full sm:max-w-md">
        <HugeiconsIcon icon={Search01Icon}
                       class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"/>
        <Input bind:value={filter} placeholder="Filter by name, UUID, or IP..." autocomplete="off" class="pl-9"/>
    </div>
</section>

{#if loading}
    <p class="mt-4 text-sm text-muted-foreground">Loading bans...</p>
{:else if error}
    <p class="mt-4 text-sm text-destructive">{error}</p>
{:else if groups.length === 0}
    <Card class="mt-4 p-10 text-center">
        <p class="text-sm text-muted-foreground">No indefinite bans configured.</p>
    </Card>
{:else if visible.length === 0}
    <p class="mt-4 text-sm text-muted-foreground">No indefinite bans match that filter.</p>
{:else}
    <section class="rise mt-4 grid gap-3 md:grid-cols-2">
        {#each visible as group, index (groupKey(group, index))}
            {@const total = entryCount(group)}
            <Card class="p-5">
                <header class="flex flex-wrap items-baseline justify-between gap-3">
                    <p class="text-sm">
                        {#if group.reason}
                            {group.reason}
                        {:else}
                            <span class="italic text-muted-foreground/70">No reason provided</span>
                        {/if}
                    </p>
                    <span class="text-xs text-muted-foreground">{total} {total === 1 ? 'entry' : 'entries'}</span>
                </header>
                <dl class="mt-4 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 border-t border-border/60 pt-3 text-xs">
                    {#if group.usernames.length}
                        <dt class="text-muted-foreground">Users</dt>
                        <dd class="flex flex-wrap gap-x-3 gap-y-1 break-all text-foreground/90">
                            {#each group.usernames as username, usernameIndex (`${username}:${usernameIndex}`)}
                                <span>{username}</span>
                            {/each}
                        </dd>
                    {/if}
                    {#if group.uuids.length}
                        <dt class="text-muted-foreground">UUIDs</dt>
                        <dd class="flex flex-wrap gap-x-3 gap-y-1 break-all font-mono text-foreground/55">
                            {#each group.uuids as uuid, uuidIndex (`${uuid}:${uuidIndex}`)}
                                <span>{uuid}</span>
                            {/each}
                        </dd>
                    {/if}
                    {#if group.ips.length}
                        <dt class="text-muted-foreground">IPs</dt>
                        <dd class="flex flex-wrap gap-x-3 gap-y-1 break-all font-mono text-warning">
                            {#each group.ips as ip, ipIndex (`${ip}:${ipIndex}`)}
                                <span>{ip}</span>
                            {/each}
                        </dd>
                    {/if}
                </dl>
            </Card>
        {/each}
    </section>
{/if}
