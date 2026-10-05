<script lang="ts">
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowRight01Icon} from '@hugeicons/core-free-icons';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import PlayerLookup from '$lib/components/PlayerLookup.svelte';
    import Button from '$lib/components/ui/Button.svelte';
    import PlayerHead from '$lib/components/ui/PlayerHead.svelte';
    import {clearRecentLookups, readRecentLookups} from '$lib/recentLookups';

    let recent = $state(readRecentLookups());

    function clearRecent() {
        clearRecentLookups();
        recent = [];
    }
</script>

<PageHeader title="Punishments" trail={[{href: '/', label: 'Overview'}]}>
    {#snippet meta()}
        <span>Review the bans, mutes, kicks and other records of any player.</span>
    {/snippet}
</PageHeader>

<div>
    <section class="panel p-5 sm:p-6">
        <PlayerLookup label="Find a player" size="large"/>
    </section>

    {#if recent.length}
        <section aria-labelledby="recent-heading" class="mt-10">
            <div class="mb-3 flex items-center justify-between gap-3">
                <h2 id="recent-heading" class="text-base font-semibold">Recent lookups</h2>
                <Button size="sm" variant="ghost" onclick={clearRecent}>Clear</Button>
            </div>
            <ul class="panel divide-y divide-line overflow-hidden">
                {#each recent as item (item.uuid)}
                    <li>
                        <a href={`/punishments/${encodeURIComponent(item.uuid)}`}
                           class="group flex items-center gap-3 px-4 py-2.5 transition-colors hover:bg-sunken focus-visible:-outline-offset-2">
                            <PlayerHead uuid={item.uuid} size={28}/>
                            <span class="min-w-0 flex-1 truncate font-medium">{item.name}</span>
                            <HugeiconsIcon icon={ArrowRight01Icon} class="size-4 shrink-0 text-faint transition-colors group-hover:text-ink"/>
                        </a>
                    </li>
                {/each}
            </ul>
        </section>
    {/if}
</div>
