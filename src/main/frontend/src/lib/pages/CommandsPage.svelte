<script lang="ts">
    import {onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowDown01Icon} from '@hugeicons/core-free-icons';
    import {api} from '$lib/api';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import Button from '$lib/components/ui/Button.svelte';
    import Notice from '$lib/components/ui/Notice.svelte';
    import SearchField from '$lib/components/ui/SearchField.svelte';
    import type {CommandGroup} from '$lib/types/api';
    import {lowerSearch} from '$lib/utils';

    let groups: CommandGroup[] = $state([]);
    let filter = $state('');
    let loading = $state(true);
    let error = $state<string | null>(null);
    let collapsed = $state(false);

    const commandCount = $derived(groups.reduce((total, group) => total + group.commands.length, 0));
    const visibleGroups = $derived.by(() => {
        const q = filter.toLowerCase().trim();
        return groups
            .map((group) => ({
                ...group,
                commands: group.commands.filter((command) => !q || lowerSearch(command).includes(q) || group.plugin.toLowerCase().includes(q))
            }))
            .filter((group) => group.commands.length > 0);
    });

    async function load() {
        loading = true;
        error = null;
        try {
            groups = (await api.commands()).groups ?? [];
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Unable to load commands.';
        } finally {
            loading = false;
        }
    }

    onMount(load);
</script>

<PageHeader title="Commands" trail={[{href: '/', label: 'Overview'}]}>
    {#snippet meta()}
        {#if !loading && !error}
            <span class="tabular-nums">{commandCount} commands from {groups.length} plugins</span>
        {/if}
    {/snippet}
</PageHeader>

{#if loading}
    <Notice kind="loading" title="Loading commands"/>
{:else if error}
    <Notice kind="error" title="Couldn't load commands" message={error}>
        <Button variant="primary" onclick={load}>Try again</Button>
    </Notice>
{:else if groups.length === 0}
    <Notice kind="empty" title="No commands registered" message="The server did not report any plugin commands."/>
{:else}
    <div class="mb-4 flex flex-wrap items-center gap-2">
        <SearchField bind:value={filter} label="Filter commands" placeholder="Name, alias, permission or plugin" hideLabel class="max-w-md flex-1"/>
        <Button variant="ghost" onclick={() => (collapsed = !collapsed)}>{collapsed ? 'Expand all' : 'Collapse all'}</Button>
    </div>

    {#if visibleGroups.length === 0}
        <Notice kind="empty" title="No match" message={`No command matches "${filter.trim()}".`}>
            <Button onclick={() => (filter = '')}>Clear filter</Button>
        </Notice>
    {:else}
        <div class="space-y-3">
            {#each visibleGroups as group, groupIndex (`${group.plugin}:${groupIndex}`)}
                <details open={!collapsed} class="group panel overflow-hidden">
                    <summary class="flex cursor-pointer items-center justify-between gap-3 px-4 py-3 transition-colors hover:bg-sunken/60">
                        <span class="min-w-0 truncate font-semibold">{group.plugin}</span>
                        <span class="flex shrink-0 items-center gap-2 text-muted tabular-nums">
                            {group.commands.length} {group.commands.length === 1 ? 'command' : 'commands'}
                            <HugeiconsIcon icon={ArrowDown01Icon} class="size-4 text-faint transition-transform group-open:rotate-180"/>
                        </span>
                    </summary>
                    <ul class="divide-y divide-line border-t border-line">
                        {#each group.commands as command, commandIndex (`${command.name}:${commandIndex}`)}
                            <li class="grid gap-x-6 gap-y-1 px-4 py-3 md:grid-cols-[14rem_minmax(0,1fr)]">
                                <div class="min-w-0">
                                    <p class="break-all font-mono text-[0.8125rem] font-medium">/{command.name}</p>
                                    {#if command.aliases?.length}
                                        <p class="mt-0.5 break-all font-mono text-xs text-muted">{command.aliases.map((alias) => `/${alias}`).join(', ')}</p>
                                    {/if}
                                </div>
                                <div class="min-w-0">
                                    <p class={command.description ? '' : 'text-faint'}>{command.description || 'No description'}</p>
                                    {#if command.usage}
                                        <p class="mt-1 text-xs text-muted">Usage <span class="break-all font-mono text-ink">{command.usage}</span></p>
                                    {/if}
                                    {#if command.permission}
                                        <p class="mt-1 text-xs text-muted">Permission <span class="break-all font-mono text-ink">{command.permission}</span></p>
                                    {/if}
                                </div>
                            </li>
                        {/each}
                    </ul>
                </details>
            {/each}
        </div>
    {/if}
{/if}