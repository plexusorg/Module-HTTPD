<script lang="ts">
    import {onDestroy, onMount} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowUpRight03Icon} from '@hugeicons/core-free-icons';
    import {api, postUrlEncoded} from '$lib/api';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import Button, {type ButtonVariant} from '$lib/components/ui/Button.svelte';
    import InventoryGrid from '$lib/components/ui/InventoryGrid.svelte';
    import Modal from '$lib/components/ui/Modal.svelte';
    import Notice from '$lib/components/ui/Notice.svelte';
    import PlayerHead from '$lib/components/ui/PlayerHead.svelte';
    import Select from '$lib/components/ui/Select.svelte';
    import Tag from '$lib/components/ui/Tag.svelte';
    import type {InventoryPayload, PlayerDetails, PlayerSummary, PlayersPayload} from '$lib/types/api';
    import {cn, pingClass, titleCase} from '$lib/utils';

    interface Props {
        id: string;
        staff: boolean;
    }

    let {id, staff}: Props = $props();
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
    let submitting = $state(false);
    let playersStream: EventSource | null = null;
    let inventoryStream: EventSource | null = null;

    const trail = [{href: '/', label: 'Overview'}, {href: '/players/', label: 'Players'}];

    const actions = [
        {action: 'ban', label: 'Ban', tone: 'destructive', temporary: false, reason: true},
        {action: 'tempban', label: 'Tempban', tone: 'warning', temporary: true, reason: true},
        {action: 'mute', label: 'Mute', tone: 'warning', temporary: false, reason: true},
        {action: 'tempmute', label: 'Tempmute', tone: 'warning', temporary: true, reason: true},
        {action: 'freeze', label: 'Freeze', tone: 'default', temporary: true, reason: true},
        {action: 'clear-inventory', label: 'Clear inventory', tone: 'destructive', temporary: false, reason: false, live: true},
        {
            action: 'clear-selected',
            label: 'Clear selected',
            tone: 'destructive',
            temporary: false,
            reason: false,
            live: true,
            selected: true
        }
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
        return tone === 'destructive' ? 'danger' : tone === 'warning' ? 'warn' : 'secondary';
    }

    function actionHint(item: (typeof actions)[number]) {
        if ('selected' in item && item.selected && !selectedItem) return 'Select a slot first';
        if ('live' in item && item.live && !inventory?.online) return 'Player must be online';
        return null;
    }

    async function submitAction() {
        if (!player || !activeAction || submitting) return;
        submitting = true;
        const form = new URLSearchParams();
        form.set('uuid', player.uuid);
        form.set('action', activeAction.action);
        form.set('reason', activeAction.reason ? reason : '');
        form.set('duration', activeAction.temporary ? duration : '');
        form.set('slot', 'selected' in activeAction && activeAction.selected ? selectedSlot ?? '' : '');
        try {
            const result = await postUrlEncoded<{ ok: boolean; message?: string }>('/api/admin/player-action', form);
            actionMessage = result.message ?? 'Action completed.';
            actionDialogOpen = false;
        } catch (cause) {
            actionError = cause instanceof Error ? cause.message : 'Action failed.';
        } finally {
            submitting = false;
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
    <PageHeader title={player?.name ?? 'Player'} {trail}/>
    <Notice kind="loading" title="Loading player"/>
{:else if error}
    <PageHeader title="Player" {trail}/>
    <Notice kind="error" title="Player lookup failed" message={error}>
        <Button variant="primary" onclick={load}>Try again</Button>
        <Button href="/players/">Back to players</Button>
        <Button href={`/punishments/${encodeURIComponent(id)}`}>Punishment history</Button>
    </Notice>
{:else if player}
    <PageHeader title={player.name} {trail}>
        {#snippet lead()}
            <PlayerHead uuid={player!.uuid} size={48}/>
        {/snippet}
        {#snippet meta()}
            <span class="w-full break-all font-mono text-[0.8125rem]">{player!.uuid}</span>
            {#if online}
                <Tag tone="ok" dot>Online</Tag>
            {:else}
                <Tag dot>Offline</Tag>
            {/if}
        {/snippet}
        {#snippet actions()}
            <Button href={`/punishments/${encodeURIComponent(player!.uuid)}`}>Punishment history</Button>
            {#if player!.nameMcUrl}
                <Button href={player!.nameMcUrl} target="_blank" rel="noopener" variant="ghost">
                    NameMC
                    <HugeiconsIcon icon={ArrowUpRight03Icon}/>
                </Button>
            {/if}
        {/snippet}
    </PageHeader>

    <div class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.15fr)]">
        <section aria-labelledby="record-heading" class="panel p-5">
            <h2 id="record-heading" class="text-base font-semibold">Details</h2>
            <dl class="mt-3 divide-y divide-line">
                {#snippet row(label: string, value: string, mono = false, tone = '')}
                    <div class="grid grid-cols-[7.5rem_minmax(0,1fr)] gap-4 py-2.5">
                        <dt class="text-muted">{label}</dt>
                        <dd class={cn('break-words', mono && 'break-all font-mono text-[0.8125rem]', tone)}>{value}</dd>
                    </div>
                {/snippet}
                {@render row('Ping', online ? `${online.ping | 0} ms` : '-', false, cn('tabular-nums', pingClass(online?.ping)))}
                {@render row('World', online?.world ?? '-')}
                {@render row('Gamemode', online?.gamemode ? titleCase(online.gamemode) : '-')}
                {@render row('IP', player.ip ?? '-', true)}
                {@render row('First played', player.firstPlayed ?? '-')}
            </dl>
        </section>

        <section aria-labelledby="actions-heading" class="panel p-5">
            <h2 id="actions-heading" class="text-base font-semibold">Actions</h2>
            <p class="mt-0.5 text-muted">Actions are issued under your staff account.</p>
            <div class="mt-4 grid grid-cols-2 gap-2">
                {#each actions as item (item.action)}
                    {@const hint = actionHint(item)}
                    <Button
                            variant={buttonVariant(item.tone)}
                            class={cn('py-1.5', item.action === 'freeze' && 'col-span-2')}
                            disabled={submitting
                                || ('live' in item && item.live && !inventory?.online)
                                || ('selected' in item && item.selected && !selectedItem)}
                            onclick={() => openAction(item.action)}
                    >
                        <span class="flex flex-col items-center leading-tight">
                            {item.label}
                            {#if hint}
                                <span class="mt-0.5 text-xs font-normal">{hint}</span>
                            {/if}
                        </span>
                    </Button>
                {/each}
            </div>
            {#if actionMessage}
                <p class="mt-4 rounded-md bg-ok-soft px-3 py-2 text-ok" role="status">{actionMessage}</p>
            {/if}
        </section>
    </div>

    {#if staff}
        <section aria-labelledby="inventory-heading" class="panel mt-4 p-5">
            <h2 id="inventory-heading" class="text-base font-semibold">Inventory</h2>
            <p class="mt-0.5 text-muted">Updates live. Select a slot to inspect or clear it.</p>
            <div class="mt-4">
                <InventoryGrid {inventory} selectedKey={selectedSlot} onSelect={(slot) => (selectedSlot = slot)}/>
            </div>
        </section>
    {/if}

    <Modal bind:open={actionDialogOpen} title={activeAction ? `Confirm ${activeAction.label.toLowerCase()}` : 'Confirm action'}
           onclose={() => (dialogAction = null)}>
        {#snippet description()}
            Target: <span class="font-medium text-ink">{player!.name}</span>{activeAction && 'selected' in activeAction && activeAction.selected ? `, slot ${selectedSlot}` : ''}
        {/snippet}
        {#if activeAction?.reason}
            <div>
                <label for="actionReason" class="label">Reason</label>
                <textarea id="actionReason" bind:value={reason} required maxlength={500} rows="3"
                          class="control min-h-24 resize-y"></textarea>
                <p class="mt-1 text-right text-xs text-muted tabular-nums">{reason.length} / 500</p>
            </div>
        {/if}
        {#if activeAction?.temporary}
            <Select id="actionDuration" label="Duration" bind:value={duration}>
                <option value="5m">5 minutes</option>
                <option value="1h">1 hour</option>
                <option value="24h">1 day</option>
                <option value="7d">7 days</option>
                <option value="30d">30 days</option>
            </Select>
        {/if}
        {#if activeAction && !activeAction.reason && !activeAction.temporary}
            <p class="text-muted">You cannot undo this.</p>
        {/if}
        {#if actionError}
            <p class="rounded-md bg-danger-soft px-3 py-2 text-danger" role="alert">{actionError}</p>
        {/if}
        {#snippet footer()}
            <Button disabled={submitting} onclick={() => (actionDialogOpen = false)}>Cancel</Button>
            <Button variant="danger" disabled={submitting || Boolean(activeAction?.reason && !reason.trim())}
                    onclick={submitAction}>{submitting ? 'Working…' : 'Confirm'}</Button>
        {/snippet}
    </Modal>
{/if}