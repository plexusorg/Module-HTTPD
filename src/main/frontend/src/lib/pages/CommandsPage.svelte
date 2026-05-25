<script lang="ts">
    import {onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowDown01Icon, Search01Icon} from '@hugeicons/core-free-icons';
    import {api} from '$lib/api';
    import {Button} from '$lib/components/ui/button';
    import {Card} from '$lib/components/ui/card';
    import {Input} from '$lib/components/ui/input';
    import type {CommandGroup} from '$lib/types/api';
    import {lowerSearch} from '$lib/utils';

    let groups: CommandGroup[] = $state([]);
    let filter = $state('');
    let loading = $state(true);
    let error = $state<string | null>(null);
    let collapsed = $state(false);

    const visibleGroups = $derived.by(() => {
        const q = filter.toLowerCase().trim();
        return groups
            .map((group) => ({
                ...group,
                commands: group.commands.filter((command) => !q || lowerSearch(command).includes(q) || group.plugin.toLowerCase().includes(q))
            }))
            .filter((group) => group.commands.length > 0);
    });

    onMount(async () => {
        try {
            groups = (await api.commands()).groups ?? [];
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Unable to load commands.';
        } finally {
            loading = false;
        }
    });
</script>

<section class="rise">
    <h1 class="text-3xl font-medium tracking-tight md:text-4xl">Commands</h1>
</section>

<section class="rise mt-6 flex flex-wrap items-center gap-3">
    <div class="relative w-full sm:max-w-md">
        <HugeiconsIcon icon={Search01Icon}
                       class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"/>
        <Input bind:value={filter} placeholder="Filter commands, aliases, permissions..." autocomplete="off"
               class="pl-9"/>
    </div>
    <Button variant="outline"
            onclick={() => (collapsed = !collapsed)}>{collapsed ? 'Expand all' : 'Collapse all'}</Button>
</section>

{#if loading}
    <p class="mt-4 text-sm text-muted-foreground">Loading commands...</p>
{:else if error}
    <p class="mt-4 text-sm text-destructive">{error}</p>
{:else if visibleGroups.length === 0}
    <p class="mt-4 text-sm text-muted-foreground">No commands match that filter.</p>
{:else}
    <section class="rise mt-4 space-y-3">
        {#each visibleGroups as group, groupIndex (`${group.plugin}:${groupIndex}`)}
            <Card class="overflow-hidden">
                <details open={!collapsed}>
                    <summary
                            class="flex cursor-pointer list-none items-center justify-between gap-3 border-b border-border/60 bg-muted/30 px-4 py-3">
                        <span class="text-sm font-medium">{group.plugin}</span>
                        <span class="flex items-center gap-2 text-xs text-muted-foreground"><span>{group.commands.length}
                            commands</span><HugeiconsIcon icon={ArrowDown01Icon} class="size-4"/></span>
                    </summary>
                    <div class="divide-y divide-border/60">
                        {#each group.commands as command, commandIndex (`${command.name}:${commandIndex}`)}
                            <article class="grid gap-2 px-4 py-3 md:grid-cols-[14rem_1fr]">
                                <div class="min-w-0">
                                    <p class="break-all font-mono text-sm font-medium text-foreground">
                                        /{command.name}</p>
                                    {#if command.aliases?.length}
                                        <p class="mt-1 break-all font-mono text-xs text-muted-foreground">{command.aliases.map((alias) => `/${alias}`).join(', ')}</p>
                                    {/if}
                                </div>
                                <div class="min-w-0 text-sm">
                                    <p class="text-foreground/90">{command.description || 'No description.'}</p>
                                    {#if command.usage}
                                        <p class="mt-1 break-all font-mono text-xs text-muted-foreground">{command.usage}</p>
                                    {/if}
                                    {#if command.permission}
                                        <p class="mt-1 break-all font-mono text-xs text-primary">{command.permission}</p>
                                    {/if}
                                </div>
                            </article>
                        {/each}
                    </div>
                </details>
            </Card>
        {/each}
    </section>
{/if}
