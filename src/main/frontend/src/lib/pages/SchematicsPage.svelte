<script lang="ts">
    import {onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {Download01Icon, Upload01Icon} from '@hugeicons/core-free-icons';
    import {api} from '$lib/api';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import Button from '$lib/components/ui/Button.svelte';
    import Notice from '$lib/components/ui/Notice.svelte';
    import SearchField from '$lib/components/ui/SearchField.svelte';
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

    async function load() {
        loading = true;
        error = null;
        try {
            schematics = (await api.schematics()).schematics ?? [];
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Unable to load schematics.';
        } finally {
            loading = false;
        }
    }

    onMount(load);
</script>

<PageHeader title="Schematics" trail={[{href: '/', label: 'Overview'}]}>
    {#snippet meta()}
        {#if !loading && !error}
            <span class="tabular-nums">{schematics.length} {schematics.length === 1 ? 'file' : 'files'}</span>
        {/if}
    {/snippet}
    {#snippet actions()}
        {#if staff}
            <Button href="/schematics/upload/" variant="primary">
                <HugeiconsIcon icon={Upload01Icon}/>
                Upload
            </Button>
        {/if}
    {/snippet}
</PageHeader>

{#if loading}
    <Notice kind="loading" title="Loading schematics"/>
{:else if error}
    <Notice kind="error" title="Couldn't load schematics" message={error}>
        <Button variant="primary" onclick={load}>Try again</Button>
    </Notice>
{:else if schematics.length === 0}
    <Notice kind="empty" title="No schematics yet" message={staff ? 'Upload a schematic to share it here.' : 'Staff have not uploaded any schematics.'}>
        {#if staff}
            <Button href="/schematics/upload/" variant="primary">Upload a schematic</Button>
        {/if}
    </Notice>
{:else}
    <SearchField bind:value={filter} label="Filter schematics" placeholder="File name" hideLabel class="mb-4"/>

    {#if visible.length === 0}
        <Notice kind="empty" title="No match" message={`No schematic name contains "${filter.trim()}".`}>
            <Button onclick={() => (filter = '')}>Clear filter</Button>
        </Notice>
    {:else}
        <div class="panel overflow-hidden">
            <table class="w-full">
                <thead class="border-b border-line text-[0.8125rem] text-muted">
                <tr>
                    <th scope="col" class="px-4 py-2.5 text-left font-medium">Name</th>
                    <th scope="col" class="px-4 py-2.5 text-right font-medium">Size</th>
                    <th scope="col" class="w-14"><span class="sr-only">Download</span></th>
                </tr>
                </thead>
                <tbody class="divide-y divide-line">
                {#each visible as schematic (schematic.name)}
                    <tr class="transition-colors hover:bg-sunken/60">
                        <td class="break-all px-4 py-2">{schematic.name}</td>
                        <td class="whitespace-nowrap px-4 py-2 text-right text-muted tabular-nums">{schematic.formattedSize || schematic.size}</td>
                        <td class="py-1 pr-2 text-right">
                            <a href={schematic.downloadUrl} download aria-label={`Download ${schematic.name}`} title="Download"
                               class="inline-grid size-9 place-items-center rounded-md text-muted transition-colors hover:bg-sunken hover:text-ink">
                                <HugeiconsIcon icon={Download01Icon} class="size-4"/>
                            </a>
                        </td>
                    </tr>
                {/each}
                </tbody>
            </table>
        </div>
    {/if}
{/if}