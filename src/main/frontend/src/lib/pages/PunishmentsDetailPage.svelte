<script lang="ts">
    import {onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {Search01Icon} from '@hugeicons/core-free-icons';
    import {api} from '$lib/api';
    import {Badge} from '$lib/components/ui/badge';
    import {Button} from '$lib/components/ui/button';
    import {Card} from '$lib/components/ui/card';
    import {Input} from '$lib/components/ui/input';
    import type {PunishmentsPayload} from '$lib/types/api';
    import {lowerSearch, titleCase} from '$lib/utils';

    interface Props {
        id: string;
    }

    let {id}: Props = $props();
    let data = $state<PunishmentsPayload | null>(null);
    let loading = $state(true);
    let error = $state<string | null>(null);
    let filter = $state('');
    let type = $state('all');
    let status = $state('all');

    const punishments = $derived<Array<Record<string, unknown>>>(data?.punishments ?? []);
    const types = $derived<string[]>(Array.from(new Set(punishments.map((item) => String(item.type ?? item.kind ?? '')).filter(Boolean))).sort());
    const visible = $derived.by(() => {
        const q = filter.toLowerCase().trim();
        return punishments.filter((item) => {
            const itemType = String(item.type ?? item.kind ?? '');
            const active = Boolean(item.active ?? item.isActive ?? item.current);
            const itemStatus = active ? 'active' : 'expired';
            return (!q || lowerSearch(item).includes(q)) && (type === 'all' || itemType === type) && (status === 'all' || itemStatus === status);
        });
    });

    function displayValue(value: unknown) {
        if (value == null || value === '') return '-';
        if (typeof value === 'boolean') return value ? 'yes' : 'no';
        if (Array.isArray(value)) return value.join(', ');
        return String(value);
    }

    function entries(item: Record<string, unknown>) {
        return Object.entries(item).filter(([key]) => !['id', 'uuid'].includes(key));
    }

    onMount(async () => {
        try {
            data = await api.punishments(id);
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Unable to load punishments.';
        } finally {
            loading = false;
        }
    });
</script>

{#if loading}
    <p class="rise text-sm text-muted-foreground">Loading punishments...</p>
{:else if error}
    <Card class="rise p-5"><p class="text-sm text-destructive">{error}</p></Card>
{:else if data}
    <section class="rise flex flex-wrap items-end justify-between gap-3">
        <div class="flex min-w-0 items-center gap-3">
            <img class="size-12 rounded-xl bg-muted inventory-pixelated"
                 src={`https://vzge.me/face/512/${encodeURIComponent(data.player.uuid)}.png`} alt="" loading="lazy"
                 width="48" height="48"/>
            <div class="min-w-0">
                <h1 class="truncate text-3xl font-medium tracking-tight md:text-4xl">{data.player.name}</h1>
                <p class="mt-1 break-all font-mono text-xs text-muted-foreground">{data.player.uuid}</p>
            </div>
        </div>
        <span class="tabular text-sm text-muted-foreground"><span class="text-foreground">{punishments.length}</span> punishments</span>
    </section>

    <section class="rise mt-4 flex flex-wrap items-center gap-3">
        <div class="relative w-full sm:max-w-md">
            <HugeiconsIcon icon={Search01Icon}
                           class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"/>
            <Input bind:value={filter}
                   placeholder={data.canViewIps ? 'Filter by reason, punisher, type, IP...' : 'Filter by reason, punisher, type...'}
                   autocomplete="off" class="pl-9"/>
        </div>
        <Button href="/punishments/" variant="secondary">
            <HugeiconsIcon icon={Search01Icon} class="size-3.5"/>
            New search
        </Button>
    </section>

    <section class="rise mt-3 flex flex-wrap items-center gap-1.5">
        <Button size="sm" variant={type === 'all' ? 'default' : 'outline'} onclick={() => (type = 'all')}>All</Button>
        {#each types as item (item)}
            <Button size="sm" variant={type === item ? 'default' : 'outline'}
                    onclick={() => (type = item)}>{titleCase(item)}</Button>
        {/each}
        <span class="mx-1 h-4 w-px bg-border"></span>
        {#each ['all', 'active', 'expired'] as item (item)}
            <Button size="sm" variant={status === item ? 'default' : 'outline'}
                    onclick={() => (status = item)}>{titleCase(item === 'all' ? 'any' : item)}</Button>
        {/each}
    </section>

    {#if visible.length === 0}
        <p class="mt-4 text-sm text-muted-foreground">No punishments match those filters.</p>
    {:else}
        <section class="rise mt-4 grid gap-3 md:grid-cols-2">
            {#each visible as punishment, index (String(punishment.id ?? index))}
                {@const itemType = String(punishment.type ?? punishment.kind ?? 'punishment')}
                {@const active = Boolean(punishment.active ?? punishment.isActive ?? punishment.current)}
                <Card class="p-4">
                    <div class="flex items-start justify-between gap-3">
                        <div>
                            <h2 class="font-medium">{titleCase(itemType)}</h2>
                            <p class="mt-1 text-sm text-muted-foreground">{displayValue(punishment.reason)}</p>
                        </div>
                        <Badge variant={active ? 'destructive' : 'secondary'}>{active ? 'active' : 'expired'}</Badge>
                    </div>
                    <dl class="mt-4 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-1 text-xs">
                        {#each entries(punishment) as [key, value] (key)}
                            <dt class="text-muted-foreground">{titleCase(key)}</dt>
                            <dd class="break-all font-mono text-foreground/80">{displayValue(value)}</dd>
                        {/each}
                    </dl>
                </Card>
            {/each}
        </section>
    {/if}
{/if}
