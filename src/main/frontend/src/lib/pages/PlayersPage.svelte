<script lang="ts">
    import {onDestroy, onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowRight01Icon} from '@hugeicons/core-free-icons';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import Button from '$lib/components/ui/Button.svelte';
    import Notice from '$lib/components/ui/Notice.svelte';
    import PlayerHead from '$lib/components/ui/PlayerHead.svelte';
    import SearchField from '$lib/components/ui/SearchField.svelte';
    import Tag from '$lib/components/ui/Tag.svelte';
    import type {PlayerSummary, PlayersPayload} from '$lib/types/api';
    import {pingClass, titleCase} from '$lib/utils';

    interface Props {
        staff: boolean;
    }

    let {staff}: Props = $props();
    let players: PlayerSummary[] = $state([]);
    let max = $state(0);
    let received = $state(false);
    let filter = $state('');
    let es: EventSource | null = null;

    const visiblePlayers = $derived(players.filter((player) => player.name.toLowerCase().includes(filter.toLowerCase().trim())));

    function connect() {
        es?.close();
        es = new EventSource(staff ? '/api/players/stream/staff' : '/api/players/stream');
        es.addEventListener('message', (event) => {
            try {
                const payload = JSON.parse(event.data) as PlayersPayload;
                players = Array.isArray(payload.players) ? payload.players : [];
                max = payload.max ?? 0;
                received = true;
            } catch {
            }
        });
    }

    onMount(connect);
    onDestroy(() => es?.close());
</script>

<PageHeader title="Players" trail={[{href: '/', label: 'Overview'}]}>
    {#snippet meta()}
        {#if received}
            <span class="tabular-nums">{players.length} of {max} online</span>
        {/if}
        {#if staff}
            <span>Select a player to open admin tools.</span>
        {/if}
    {/snippet}
</PageHeader>

{#if !received}
    <Notice kind="loading" title="Connecting to the server"/>
{:else if players.length === 0}
    <Notice kind="empty" title="Nobody is online" message="The list updates live. Players appear here as soon as they join.">
        <Button href="/punishments/">Look up a player's history</Button>
    </Notice>
{:else}
    <SearchField bind:value={filter} label="Filter online players" placeholder="Name" hideLabel class="mb-4 max-w-sm"/>

    {#if visiblePlayers.length === 0}
        <Notice kind="empty" title="No match" message={`No online player name contains "${filter.trim()}".`}>
            <Button onclick={() => (filter = '')}>Clear filter</Button>
        </Notice>
    {:else}
        <ul class="panel divide-y divide-line overflow-hidden">
            {#each visiblePlayers as player (player.uuid)}
                <li>
                    <svelte:element
                            this={staff ? 'a' : 'div'}
                            href={staff ? `/player/${encodeURIComponent(player.uuid)}` : undefined}
                            class="group flex items-center gap-3 px-4 py-2.5 {staff ? 'transition-colors hover:bg-sunken focus-visible:-outline-offset-2' : ''}"
                    >
                        <PlayerHead uuid={player.uuid} size={32}/>
                        <div class="flex min-w-0 flex-1 flex-wrap items-center gap-x-2 gap-y-0.5">
                            <span class="truncate font-medium">{player.name}</span>
                            {#if player.op}
                                <Tag tone="brand">Operator</Tag>
                            {/if}
                            {#if staff && player.gamemode}
                                <Tag>{titleCase(player.gamemode)}</Tag>
                            {/if}
                        </div>
                        {#if player.world}
                            <span class="hidden max-w-[12rem] truncate text-muted sm:block">{player.world}</span>
                        {/if}
                        <span class="w-14 shrink-0 text-right tabular-nums {pingClass(player.ping)}">{player.ping | 0} ms</span>
                        {#if staff}
                            <HugeiconsIcon icon={ArrowRight01Icon} class="size-4 shrink-0 text-faint transition-colors group-hover:text-ink"/>
                        {/if}
                    </svelte:element>
                </li>
            {/each}
        </ul>
    {/if}
{/if}