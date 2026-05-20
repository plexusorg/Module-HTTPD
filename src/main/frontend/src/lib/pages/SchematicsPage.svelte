<script lang="ts">
    import {onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {Download01Icon, Search01Icon, Upload01Icon} from '@hugeicons/core-free-icons';
    import {api} from '$lib/api';
    import {Button} from '$lib/components/ui/button';
    import {Card} from '$lib/components/ui/card';
    import {Input} from '$lib/components/ui/input';
    import type {Schematic} from '$lib/types/api';

    interface Props {
        staff: boolean;
    }

    let {staff}: Props = $props();
    let schematics: Schematic[] = $state([]);
    let loading = $state(true);
    let error = $state<string | null>(null);
    let filter = $state('');

    const visible = $derived(schematics.filter((schematic) => schematic.name.toLowerCase().includes(filter.toLowerCase().trim())));

    onMount(async () => {
        try {
            schematics = (await api.schematics()).schematics ?? [];
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Unable to load schematics.';
        } finally {
            loading = false;
        }
    });
</script>

<section class="rise flex flex-wrap items-end justify-between gap-3">
    <h1 class="text-3xl font-medium tracking-tight md:text-4xl">Schematics</h1>
    {#if staff}
        <Button href="/schematics/upload/">
            <HugeiconsIcon icon={Upload01Icon} class="size-3.5"/>
            Upload
        </Button>
    {/if}
</section>

<section class="rise mt-6">
    <div class="relative w-full sm:max-w-md">
        <HugeiconsIcon icon={Search01Icon}
                       class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"/>
        <Input bind:value={filter} placeholder="Filter schematics..." autocomplete="off" class="pl-9"/>
    </div>
</section>

{#if loading}
    <p class="mt-4 text-sm text-muted-foreground">Loading schematics...</p>
{:else if error}
    <p class="mt-4 text-sm text-destructive">{error}</p>
{:else}
    <Card class="rise mt-4 overflow-hidden py-0">
        <table class="w-full text-sm">
            <thead class="border-b border-border/60 bg-muted/40">
            <tr>
                <th scope="col" class="px-3 py-2 text-left text-xs font-medium text-muted-foreground">Name</th>
                <th scope="col" class="px-3 py-2 text-right text-xs font-medium text-muted-foreground">Size</th>
                <th scope="col" class="w-12"></th>
            </tr>
            </thead>
            <tbody class="divide-y divide-border/60">
            {#each visible as schematic (schematic.name)}
                <tr>
                    <td class="break-all px-3 py-2.5 font-mono text-xs">{schematic.name}</td>
                    <td class="px-3 py-2.5 text-right tabular text-muted-foreground">{schematic.formattedSize || schematic.size}</td>
                    <td class="px-3 py-2.5 text-right">
                        <a href={schematic.downloadUrl} download aria-label={`Download ${schematic.name}`}
                           class="inline-flex size-8 items-center justify-center rounded-full text-muted-foreground hover:bg-muted hover:text-foreground">
                            <HugeiconsIcon icon={Download01Icon} class="size-4"/>
                        </a>
                    </td>
                </tr>
            {:else}
                <tr>
                    <td colspan="3" class="px-3 py-6 text-center text-muted-foreground">No schematics match that
                        filter.
                    </td>
                </tr>
            {/each}
            </tbody>
        </table>
    </Card>
{/if}
