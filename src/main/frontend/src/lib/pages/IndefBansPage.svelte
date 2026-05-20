<script lang="ts">
    import {onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {Search01Icon} from '@hugeicons/core-free-icons';
    import {api} from '$lib/api';
    import {Card} from '$lib/components/ui/card';
    import {Input} from '$lib/components/ui/input';
    import {lowerSearch, titleCase} from '$lib/utils';

    let bans: Array<Record<string, unknown>> = $state([]);
    let loading = $state(true);
    let error = $state<string | null>(null);
    let filter = $state('');

    const visible = $derived(bans.filter((ban) => !filter.trim() || lowerSearch(ban).includes(filter.toLowerCase().trim())));
    const totals = $derived.by(() => {
        const text = JSON.stringify(bans);
        return {
            groups: bans.length,
            users: (text.match(/user(name)?/gi) ?? []).length,
            uuids: (text.match(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi) ?? []).length,
            ips: (text.match(/\b(?:\d{1,3}\.){3}\d{1,3}\b/g) ?? []).length
        };
    });

    function display(value: unknown): string {
        if (value == null || value === '') return '-';
        if (Array.isArray(value)) return value.map(display).join(', ');
        if (typeof value === 'object') return JSON.stringify(value);
        return String(value);
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
        <span><span class="text-foreground">{totals.users}</span> user keys</span>
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
{:else if visible.length === 0}
    <p class="mt-4 text-sm text-muted-foreground">No indefinite bans match that filter.</p>
{:else}
    <section class="rise mt-4 grid gap-3 md:grid-cols-2">
        {#each visible as ban, index (index)}
            <Card class="p-4">
                <h2 class="text-sm font-medium">Group {index + 1}</h2>
                <dl class="mt-3 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-xs">
                    {#each Object.entries(ban) as [key, value] (key)}
                        <dt class="text-muted-foreground">{titleCase(key)}</dt>
                        <dd class="break-all font-mono text-foreground/80">{display(value)}</dd>
                    {/each}
                </dl>
            </Card>
        {/each}
    </section>
{/if}
