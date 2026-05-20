<script lang="ts">
  import { onDestroy, onMount } from 'svelte';
  import { HugeiconsIcon } from '@hugeicons/svelte';
  import { ArrowLeft01Icon, ArrowUpRight03Icon } from '@hugeicons/core-free-icons';
  import { api, postForm } from '$lib/api';
  import { Badge } from '$lib/components/ui/badge';
  import { Button, type ButtonVariant } from '$lib/components/ui/button';
  import { Card } from '$lib/components/ui/card';
  import * as Dialog from '$lib/components/ui/dialog';
  import { Label } from '$lib/components/ui/label';
  import { Textarea } from '$lib/components/ui/textarea';
  import InventoryGrid from '$lib/components/ui/InventoryGrid.svelte';
  import type { InventoryPayload, PlayerDetails, PlayerSummary, PlayersPayload } from '$lib/types/api';
  import { navigate } from '$lib/router';
  import { cn, pingClass, titleCase } from '$lib/utils';

  interface Props {
    id: string;
    staff: boolean;
  }

  let { id, staff }: Props = $props();
  let player = $state<PlayerDetails | null>(null);
  let online = $state<PlayerSummary | null>(null);
  let inventory = $state<InventoryPayload | null>(null);
  let selectedSlot: string | null = $state(null);
  let loading = $state(true);
  let error = $state<string | null>(null);
  let actionError = $state<string | null>(null);
  let actionMessage = $state<string | null>(null);
  let dialogAction: string | null = $state(null);
  let actionDialogOpen = $state(false);
  let reason = $state('');
  let duration = $state('24h');
  let playersStream: EventSource | null = null;
  let inventoryStream: EventSource | null = null;

  const actions = [
    { action: 'ban', label: 'Ban', tone: 'destructive', temporary: false, reason: true },
    { action: 'tempban', label: 'Tempban', tone: 'warning', temporary: true, reason: true },
    { action: 'mute', label: 'Mute', tone: 'warning', temporary: false, reason: true },
    { action: 'tempmute', label: 'Tempmute', tone: 'warning', temporary: true, reason: true },
    { action: 'freeze', label: 'Freeze', tone: 'default', temporary: true, reason: true },
    { action: 'clear-inventory', label: 'Clear inventory', tone: 'destructive', temporary: false, reason: false },
    { action: 'clear-selected', label: 'Clear selected', tone: 'destructive', temporary: false, reason: false, selected: true }
  ] as const;

  const activeAction = $derived(actions.find((item) => item.action === dialogAction));
  const selectedItem = $derived(Boolean(selectedSlot && inventory?.online));

  function openAction(action: string) {
    dialogAction = action;
    actionDialogOpen = true;
    actionError = null;
    actionMessage = null;
    reason = '';
  }

  function buttonVariant(tone: string): ButtonVariant {
    return tone === 'destructive' ? 'destructive' : tone === 'warning' ? 'outline' : 'default';
  }

  function actionButtonClass(item: (typeof actions)[number]) {
    return cn(
      item.tone === 'warning' && 'border-warning/30 bg-warning/10 text-warning hover:bg-warning/15 hover:text-warning',
      item.action === 'freeze' && 'col-span-2'
    );
  }

  async function submitAction() {
    if (!player || !activeAction) return;
    const form = new FormData();
    form.set('uuid', player.uuid);
    form.set('action', activeAction.action);
    form.set('reason', activeAction.reason ? reason : '');
    form.set('duration', activeAction.temporary ? duration : '');
    form.set('slot', 'selected' in activeAction && activeAction.selected ? selectedSlot ?? '' : '');
    try {
      const result = await postForm<{ ok: boolean; message?: string }>('/api/admin/player-action', form);
      actionMessage = result.message ?? 'Action completed.';
      dialogAction = null;
      actionDialogOpen = false;
    } catch (cause) {
      actionError = cause instanceof Error ? cause.message : 'Action failed.';
    }
  }

  async function load() {
    loading = true;
    error = null;
    selectedSlot = null;
    inventory = null;
    playersStream?.close();
    inventoryStream?.close();
    try {
      const response = await api.player(id);
      player = response.player;
      playersStream = new EventSource('/api/players/stream/staff');
      playersStream.addEventListener('message', (event) => {
        try {
          const payload = JSON.parse(event.data) as PlayersPayload;
          online = payload.players.find((item) => item.uuid === player?.uuid) ?? null;
        } catch {
          online = null;
        }
      });
      if (staff) {
        inventoryStream = new EventSource(`/api/player/inventory/stream?uuid=${encodeURIComponent(response.player.uuid)}`);
        inventoryStream.addEventListener('message', (event) => {
          try {
            inventory = JSON.parse(event.data) as InventoryPayload;
            if (!inventory.online) selectedSlot = null;
          } catch {
            inventory = null;
          }
        });
      }
    } catch (cause) {
      error = cause instanceof Error ? cause.message : 'Unable to load player.';
    } finally {
      loading = false;
    }
  }

  onMount(load);
  onDestroy(() => {
    playersStream?.close();
    inventoryStream?.close();
  });
</script>

{#if loading}
  <p class="rise text-sm text-muted-foreground">Loading player...</p>
{:else if error}
  <Card class="rise p-5">
    <h1 class="text-xl font-medium">Player lookup failed</h1>
    <p class="mt-2 text-sm text-destructive">{error}</p>
  </Card>
{:else if player}
  <section class="rise flex flex-wrap items-end justify-between gap-3">
    <div class="flex min-w-0 items-center gap-3">
      <img class="size-14 rounded-xl bg-muted inventory-pixelated" src={`https://vzge.me/face/512/${encodeURIComponent(player.uuid)}.png`} alt="" loading="lazy" width="56" height="56" />
      <div class="min-w-0">
        <h1 class="truncate text-3xl font-medium tracking-tight md:text-4xl">{player.name}</h1>
        <p class="mt-1 break-all font-mono text-xs text-muted-foreground">{player.uuid}</p>
      </div>
    </div>
    <Button variant="secondary" onclick={() => navigate('/players/')}>
      <HugeiconsIcon icon={ArrowLeft01Icon} class="size-3.5" />
      Players
    </Button>
  </section>

  <section class="rise mt-6 grid gap-4 md:grid-cols-2">
    <Card class="p-5">
      <h2 class="text-sm font-medium tracking-tight">Info</h2>
      <dl class="mt-4 grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
        <dt class="text-muted-foreground">Status</dt>
        <dd>{#if online}<Badge variant="secondary" class="bg-success/10 text-success">online</Badge>{:else}<Badge variant="secondary">offline</Badge>{/if}</dd>
        <dt class="text-muted-foreground">Ping</dt>
        <dd class="tabular {pingClass(online?.ping)}">{online ? `${online.ping | 0}ms` : '-'}</dd>
        <dt class="text-muted-foreground">World</dt>
        <dd class="text-foreground/80">{online?.world ?? '-'}</dd>
        <dt class="text-muted-foreground">Gamemode</dt>
        <dd class="text-foreground/80">{online?.gamemode ? titleCase(online.gamemode) : '-'}</dd>
        <dt class="text-muted-foreground">IP</dt>
        <dd class="break-all font-mono text-foreground/80">{player.ip ?? '-'}</dd>
        <dt class="text-muted-foreground">First played</dt>
        <dd class="text-foreground/80">{player.firstPlayed ?? '-'}</dd>
        <dt class="text-muted-foreground">Punishments</dt>
        <dd><a href={`/punishments/${encodeURIComponent(player.uuid)}`} class="inline-flex items-center gap-1 text-primary hover:underline">View history</a></dd>
        {#if player.nameMcUrl}
          <dt class="text-muted-foreground">NameMC</dt>
          <dd><a href={player.nameMcUrl} target="_blank" rel="noopener" class="inline-flex items-center gap-1 text-primary hover:underline">View profile <HugeiconsIcon icon={ArrowUpRight03Icon} class="size-3" /></a></dd>
        {/if}
      </dl>
    </Card>

    <Card class="p-5">
      <h2 class="text-sm font-medium tracking-tight">Actions</h2>
      <p class="mt-1 text-xs text-muted-foreground">Issued punishments use the authenticated staff account.</p>
      <div class="mt-4 grid grid-cols-2 gap-2">
        {#each actions as item (item.action)}
          <Button
            variant={buttonVariant(item.tone)}
            class={actionButtonClass(item)}
            disabled={'selected' in item && item.selected && !selectedItem}
            onclick={() => openAction(item.action)}
          >
            {item.label}
          </Button>
        {/each}
      </div>
      {#if actionMessage}
        <p class="mt-3 text-sm text-success">{actionMessage}</p>
      {/if}
    </Card>
  </section>

  {#if staff}
    <section class="rise mt-4">
      <Card class="p-5">
        <h2 class="text-sm font-medium tracking-tight">Live inventory</h2>
        <div class="mt-4">
          <InventoryGrid {inventory} selectedKey={selectedSlot} onSelect={(slot) => (selectedSlot = slot)} />
        </div>
      </Card>
    </section>
  {/if}

  {#if activeAction}
    <Dialog.Root bind:open={actionDialogOpen}>
      <Dialog.Content>
        <Dialog.Header>
          <Dialog.Title>Confirm {activeAction.label.toLowerCase()}</Dialog.Title>
          <Dialog.Description>
            Target: <span class="text-foreground">{player.name}</span>{'selected' in activeAction && activeAction.selected ? ` | Slot: ${selectedSlot}` : ''}
          </Dialog.Description>
        </Dialog.Header>
        {#if activeAction.reason}
          <div class="grid gap-2">
            <Label for="actionReason">Reason</Label>
            <Textarea id="actionReason" bind:value={reason} required maxlength={500} />
          </div>
        {/if}
        {#if activeAction.temporary}
          <div class="grid gap-2">
            <Label for="actionDuration">Duration</Label>
            <select id="actionDuration" bind:value={duration} class="border-input bg-input/30 focus-visible:border-ring focus-visible:ring-ring/50 h-9 rounded-4xl border px-3 py-1 text-sm outline-none focus-visible:ring-[3px]">
              <option value="5m">5 minutes</option>
              <option value="1h">1 hour</option>
              <option value="24h">1 day</option>
              <option value="7d">7 days</option>
              <option value="30d">30 days</option>
            </select>
          </div>
        {/if}
        {#if actionError}
          <p class="mt-3 text-sm text-destructive">{actionError}</p>
        {/if}
        <Dialog.Footer>
          <Button variant="secondary" onclick={() => { actionDialogOpen = false; dialogAction = null; }}>Cancel</Button>
          <Button variant="destructive" disabled={activeAction.reason && !reason.trim()} onclick={submitAction}>Confirm</Button>
        </Dialog.Footer>
      </Dialog.Content>
    </Dialog.Root>
  {/if}
{/if}
