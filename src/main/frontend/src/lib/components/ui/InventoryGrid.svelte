<script lang="ts">
    import ItemIcon from '$lib/components/ui/ItemIcon.svelte';
    import type {InventoryItem, InventoryPayload} from '$lib/types/api';
    import {cn, titleCase} from '$lib/utils';

    interface Props {
        inventory: InventoryPayload | null;
        selectedKey: string | null;
        onSelect: (slot: string | null) => void;
    }

    let {inventory, selectedKey, onSelect}: Props = $props();

    const ROMAN = ['', 'I', 'II', 'III', 'IV', 'V', 'VI', 'VII', 'VIII', 'IX', 'X'];

    function itemAt(slot: string | null) {
        if (!inventory?.online || !slot) return null;
        if (slot === 'offhand') return inventory.offhand ?? null;
        if (slot.startsWith('storage-')) return inventory.storage?.[Number(slot.substring(8))] ?? null;
        if (slot.startsWith('hotbar-')) return inventory.hotbar?.[Number(slot.substring(7))] ?? null;
        if (slot.startsWith('armor-')) return inventory.armor?.[slot.substring(6)] ?? null;
        return null;
    }

    const selectedItem = $derived(itemAt(selectedKey));

    function tooltip(item: InventoryItem) {
        const parts = [item.name || titleCase(item.type)];
        if (item.amount > 1) parts[0] += ` x${item.amount}`;
        if (item.enchants) {
            for (const [key, value] of Object.entries(item.enchants)) parts.push(`${titleCase(key)} ${ROMAN[value] || value}`);
        }
        if (item.maxDamage) parts.push(`Durability: ${item.maxDamage - (item.damage || 0)} / ${item.maxDamage}`);
        return parts.join(' | ');
    }

    function durabilityPercent(item: InventoryItem) {
        if (!item.maxDamage) return null;
        return Math.max(0, Math.min(100, ((item.maxDamage - (item.damage || 0)) / item.maxDamage) * 100));
    }
</script>

{#snippet slot(item: InventoryItem | null | undefined, key: string, fixed = false)}
    {#if item}
        {@const durability = durabilityPercent(item)}
        <button
                type="button"
                title={tooltip(item)}
                aria-label={tooltip(item)}
                aria-pressed={selectedKey === key}
                class={cn('slot relative aspect-square p-[8%] transition-[background-color]', fixed ? 'size-10 sm:size-12' : 'w-full', selectedKey === key && 'slot-selected')}
                onclick={() => onSelect(key)}
        >
            <ItemIcon type={item.type}/>
            {#if item.enchants}
                <span class="enchant pointer-events-none absolute inset-0" aria-hidden="true"></span>
            {/if}
            {#if item.amount > 1}
                <span class="pointer-events-none absolute bottom-0 right-0.5 text-xs font-bold leading-tight text-white tabular-nums [text-shadow:1px_1px_0_rgb(0_0_0/0.8)]">{item.amount}</span>
            {/if}
            {#if durability != null && durability < 99.9}
                <span class="absolute inset-x-[12%] bottom-[8%] h-[3px] bg-black/60">
                    <span class={cn('block h-full', durability > 50 ? 'bg-ok' : durability > 25 ? 'bg-warn' : 'bg-danger')}
                          style:width={`${durability}%`}></span>
                </span>
            {/if}
        </button>
    {:else}
        <div class={cn('slot aspect-square', fixed ? 'size-10 sm:size-12' : 'w-full')}></div>
    {/if}
{/snippet}

{#if !inventory}
    <p class="py-8 text-center text-muted" role="status">Waiting for inventory data…</p>
{:else if !inventory.online}
    <p class="py-8 text-center text-muted">The player is offline. The live inventory shows while they are online.</p>
{:else}
    <div class="grid gap-6 xl:grid-cols-[auto_minmax(0,1fr)]">
        <div class="flex flex-col gap-5 sm:flex-row sm:items-start">
            <div class="w-full max-w-[29rem] min-w-0">
                <p class="mb-2 text-[0.8125rem] text-muted">Inventory</p>
                <div class="slot-frame">
                    <div class="grid grid-cols-9 gap-[3px]">
                        {#each inventory.storage ?? [] as item, index (index)}
                            {@render slot(item, `storage-${index}`)}
                        {/each}
                    </div>
                    <div class="mt-2.5 grid grid-cols-9 gap-[3px]">
                        {#each inventory.hotbar ?? [] as item, index (index)}
                            {@render slot(item, `hotbar-${index}`)}
                        {/each}
                    </div>
                </div>
            </div>
            <div class="flex gap-5">
                <div>
                    <p class="mb-2 text-[0.8125rem] text-muted">Armor</p>
                    <div class="slot-frame flex gap-[3px] sm:flex-col">
                        {@render slot(inventory.armor?.helmet, 'armor-helmet', true)}
                        {@render slot(inventory.armor?.chest, 'armor-chest', true)}
                        {@render slot(inventory.armor?.legs, 'armor-legs', true)}
                        {@render slot(inventory.armor?.boots, 'armor-boots', true)}
                    </div>
                </div>
                <div>
                    <p class="mb-2 text-[0.8125rem] text-muted">Offhand</p>
                    <div class="slot-frame">
                        {@render slot(inventory.offhand, 'offhand', true)}
                    </div>
                </div>
            </div>
        </div>

        <div class="min-w-0 rounded-lg bg-sunken/70 p-4">
            {#if selectedItem}
                <div class="space-y-4">
                    <div class="flex items-start gap-3">
                        <div class="slot relative size-16 shrink-0 p-2">
                            <ItemIcon type={selectedItem.type}/>
                        </div>
                        <div class="min-w-0">
                            {#if selectedItem.name}
                                <p class="max-w-full truncate font-medium">{selectedItem.name}</p>
                            {/if}
                            <p class="break-all font-mono text-xs text-muted">{selectedItem.type.toLowerCase()}</p>
                            <p class="mt-0.5 text-muted tabular-nums">Count {selectedItem.amount}</p>
                        </div>
                    </div>

                    {#if selectedItem.lore?.length}
                        <div>
                            <p class="text-[0.8125rem] text-muted">Lore</p>
                            <ul class="mt-1 space-y-0.5 italic">
                                {#each selectedItem.lore as line, index (index)}
                                    <li class="break-all">{line}</li>
                                {/each}
                            </ul>
                        </div>
                    {/if}

                    {#if selectedItem.enchants}
                        <div>
                            <p class="text-[0.8125rem] text-muted">Enchantments</p>
                            <ul class="mt-1 space-y-0.5">
                                {#each Object.entries(selectedItem.enchants) as [key, value] (key)}
                                    <li class="flex justify-between gap-3"><span>{titleCase(key)}</span><span
                                            class="text-muted">{ROMAN[value] || value}</span></li>
                                {/each}
                            </ul>
                        </div>
                    {/if}

                    {#if selectedItem.nbt}
                        <div>
                            <p class="text-[0.8125rem] text-muted">NBT</p>
                            <pre class="mt-1 max-h-48 max-w-full overflow-auto whitespace-pre-wrap break-all rounded-md bg-surface p-2.5 font-mono text-xs leading-snug">{selectedItem.nbt}</pre>
                        </div>
                    {/if}
                </div>
            {:else}
                <div class="flex h-full min-h-40 items-center justify-center text-center text-muted">
                    Select an occupied slot to inspect the item.
                </div>
            {/if}
        </div>
    </div>
{/if}

<style>
    .slot-frame {
        padding: 4px;
        border-radius: 4px;
        background: oklch(0.82 0.004 264);
    }

    .slot {
        display: block;
        border-radius: 1px;
        background: oklch(0.62 0.004 264);
        box-shadow: inset 2px 2px 0 oklch(0.44 0.004 264), inset -2px -2px 0 oklch(0.93 0.003 264);
    }

    button.slot:hover {
        background: oklch(0.7 0.004 264);
    }

    .slot-selected {
        outline: 2px solid var(--color-brand);
        outline-offset: 1px;
        position: relative;
        z-index: 1;
    }

    .enchant {
        background: linear-gradient(115deg, transparent 30%, oklch(0.75 0.2 300 / 0.35) 50%, transparent 70%);
        background-size: 250% 100%;
        mix-blend-mode: screen;
        animation: glint 3.2s linear infinite;
    }

    :global(.dark) .slot-frame {
        background: oklch(0.27 0.005 264);
    }

    :global(.dark) .slot {
        background: oklch(0.21 0.005 264);
        box-shadow: inset 2px 2px 0 oklch(0.14 0.004 264), inset -2px -2px 0 oklch(0.33 0.006 264);
    }

    :global(.dark) button.slot:hover {
        background: oklch(0.26 0.006 264);
    }

    @keyframes glint {
        from {
            background-position: 120% 0;
        }
        to {
            background-position: -120% 0;
        }
    }
</style>
