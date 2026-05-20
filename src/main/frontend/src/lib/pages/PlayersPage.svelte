<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import { HugeiconsIcon } from '@hugeicons/svelte';
  import { Search01Icon, Shield01Icon, UserGroupIcon } from '@hugeicons/core-free-icons';
  import { Badge } from '$lib/components/ui/badge';
  import { Card } from '$lib/components/ui/card';
  import { Input } from '$lib/components/ui/input';
  import type { PlayerSummary, PlayersPayload } from '$lib/types/api';
  import { pingClass } from '$lib/utils';

  interface Props {
    staff: boolean;
  }

  let { staff }: Props = $props();
  let players: PlayerSummary[] = $state([]);
  let max = $state(0);
  let filter = $state('');
  let connected = $state(false);
  let es: EventSource | null = null;

  const visiblePlayers = $derived(players.filter((player) => player.name.toLowerCase().includes(filter.toLowerCase().trim())));

  function connect() {
    es?.close();
    es = new EventSource(staff ? '/api/players/stream/staff' : '/api/players/stream');
    es.addEventListener('open', () => (connected = true));
    es.addEventListener('error', () => (connected = false));
    es.addEventListener('message', (event) => {
      try {
        const payload = JSON.parse(event.data) as PlayersPayload;
        players = Array.isArray(payload.players) ? payload.players : [];
        max = payload.max ?? 0;
        connected = true;
      } catch {
        connected = false;
      }
    });
  }

  onMount(connect);
  onDestroy(() => es?.close());
</script>

<section class="rise flex flex-wrap items-end justify-between gap-3">
  <h1 class="text-3xl font-medium tracking-tight md:text-4xl">Players</h1>
  <span class="tabular text-sm text-muted-foreground">
    <span class="text-foreground">{players.length}</span> / {max} online
  </span>
</section>

<section class="rise mt-6 flex flex-col items-stretch gap-3 sm:flex-row sm:items-center">
  <div class="relative w-full sm:max-w-md">
    <HugeiconsIcon icon={Search01Icon} class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
    <Input bind:value={filter} placeholder="Filter by name..." autocomplete="off" class="pl-9" />
  </div>
  <span class="text-xs text-muted-foreground">{connected ? 'live' : 'waiting for stream'}</span>
</section>

<section class="rise mt-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
  {#if visiblePlayers.length === 0}
    <Card class="col-span-full p-10 text-center">
      <HugeiconsIcon icon={UserGroupIcon} class="mx-auto size-8 text-muted-foreground/60" />
      <p class="mt-3 text-sm text-muted-foreground">{players.length ? 'No players match that filter.' : 'No players online right now.'}</p>
    </Card>
  {:else}
    {#each visiblePlayers as player (player.uuid)}
      <svelte:element
        this={staff ? 'a' : 'div'}
        href={staff ? `/player/${encodeURIComponent(player.uuid)}` : undefined}
        class="ring-card flex items-center gap-3 rounded-xl bg-card p-3 transition-colors hover:bg-secondary/50"
      >
        <img class="size-10 rounded-lg bg-muted inventory-pixelated" src={`https://vzge.me/face/512/${encodeURIComponent(player.uuid)}.png`} alt="" loading="lazy" width="40" height="40" />
        <div class="min-w-0 flex-1">
          <div class="flex items-center gap-2">
            <span class="truncate text-sm font-medium">{player.name}</span>
            {#if player.op}
              <Badge variant="default">op</Badge>
            {/if}
            {#if staff && player.gamemode}
              <Badge>{player.gamemode.toLowerCase()}</Badge>
            {/if}
          </div>
          <div class="mt-0.5 flex flex-wrap items-center gap-x-2 text-xs text-muted-foreground">
            {#if player.world}
              <span>In {player.world}</span>
              <span class="text-foreground/30">.</span>
            {/if}
            <span class="tabular {pingClass(player.ping)}">{player.ping | 0}ms</span>
          </div>
        </div>
        {#if staff}
          <HugeiconsIcon icon={Shield01Icon} class="size-4 text-muted-foreground" />
        {/if}
      </svelte:element>
    {/each}
  {/if}
</section>
