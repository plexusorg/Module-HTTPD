<script lang="ts">
  import ItemIcon from '$lib/components/ui/ItemIcon.svelte';
  import type { InventoryItem, InventoryPayload } from '$lib/types/api';
  import { cn, titleCase } from '$lib/utils';

  interface Props {
    inventory: InventoryPayload | null;
    selectedKey: string | null;
    onSelect: (slot: string | null) => void;
  }

  let { inventory, selectedKey, onSelect }: Props = $props();

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

{#snippet slot(item: InventoryItem | null | undefined, key: string)}
  {#if item}
    {@const durability = durabilityPercent(item)}
    <button
      type="button"
      title={tooltip(item)}
      class={cn(
        'relative size-12 rounded-md bg-muted/40 transition-colors hover:bg-muted',
        selectedKey === key ? 'ring-2 ring-primary' : 'ring-card'
      )}
      onclick={() => onSelect(key)}
    >
      <ItemIcon type={item.type} />
      {#if item.enchants}
        <span class="pointer-events-none absolute inset-0 rounded-md bg-primary/5 ring-1 ring-inset ring-primary/40"></span>
      {/if}
      {#if item.amount > 1}
        <span class="pointer-events-none absolute bottom-0.5 right-1 font-mono text-xs font-medium [text-shadow:0_1px_2px_rgba(0,0,0,0.7)]">{item.amount}</span>
      {/if}
      {#if durability != null && durability < 99.9}
        <span class="absolute inset-x-1 bottom-0.5 h-0.5 rounded-full bg-foreground/15">
          <span class={cn('block h-full rounded-full', durability > 50 ? 'bg-success' : durability > 25 ? 'bg-warning' : 'bg-destructive')} style:width={`${durability}%`}></span>
        </span>
      {/if}
    </button>
  {:else}
    <div class="ring-card size-12 rounded-md bg-muted/40"></div>
  {/if}
{/snippet}

{#if !inventory}
  <p class="py-6 text-center text-sm text-muted-foreground">Waiting for data...</p>
{:else if !inventory.online}
  <p class="py-6 text-center text-sm text-muted-foreground">Player is offline.</p>
{:else}
  <div class="grid gap-6 lg:grid-cols-[auto_1fr]">
    <div class="-mx-2 overflow-x-auto px-2 pb-2 sm:mx-0 sm:px-0">
      <div class="flex min-w-max flex-wrap gap-4 lg:flex-nowrap">
        <div>
          <p class="mb-1 text-[10px] uppercase tracking-wide text-muted-foreground">Main</p>
          <div class="space-y-2">
            <div class="grid grid-cols-9 gap-1">
              {#each inventory.storage ?? [] as item, index (index)}
                {@render slot(item, `storage-${index}`)}
              {/each}
            </div>
            <div class="grid grid-cols-9 gap-1 border-t border-border/40 pt-2">
              {#each inventory.hotbar ?? [] as item, index (index)}
                {@render slot(item, `hotbar-${index}`)}
              {/each}
            </div>
          </div>
        </div>
        <div class="flex gap-4">
          <div>
            <p class="mb-1 text-[10px] uppercase tracking-wide text-muted-foreground">Armor</p>
            <div class="flex flex-col gap-1">
              {@render slot(inventory.armor?.helmet, 'armor-helmet')}
              {@render slot(inventory.armor?.chest, 'armor-chest')}
              {@render slot(inventory.armor?.legs, 'armor-legs')}
              {@render slot(inventory.armor?.boots, 'armor-boots')}
            </div>
          </div>
          <div>
            <p class="mb-1 text-[10px] uppercase tracking-wide text-muted-foreground">Offhand</p>
            {@render slot(inventory.offhand, 'offhand')}
          </div>
        </div>
      </div>
    </div>

    <div class="min-w-0 rounded-xl border border-border/40 bg-background/40 p-4">
      {#if selectedItem}
        <div class="space-y-4">
          <div class="flex items-start gap-3">
            <div class="ring-card relative size-16 shrink-0 rounded-md bg-muted/40">
              <ItemIcon type={selectedItem.type} />
            </div>
            <div class="min-w-0">
              {#if selectedItem.name}
                <p class="max-w-full overflow-hidden text-ellipsis whitespace-nowrap text-base font-medium italic">{selectedItem.name}</p>
              {/if}
              <p class="break-all font-mono text-xs text-muted-foreground">{selectedItem.type}</p>
              <p class="mt-0.5 text-xs text-muted-foreground">Count: {selectedItem.amount}</p>
            </div>
          </div>

          {#if selectedItem.lore?.length}
            <div>
              <p class="text-[10px] uppercase tracking-wide text-muted-foreground">Lore</p>
              <ul class="mt-1 space-y-0.5 text-xs italic text-foreground/80">
                {#each selectedItem.lore as line, index (index)}
                  <li class="break-all">{line}</li>
                {/each}
              </ul>
            </div>
          {/if}

          {#if selectedItem.enchants}
            <div>
              <p class="text-[10px] uppercase tracking-wide text-muted-foreground">Enchantments</p>
              <ul class="mt-1 space-y-0.5 text-xs">
                {#each Object.entries(selectedItem.enchants) as [key, value] (key)}
                  <li class="flex justify-between gap-3"><span>{titleCase(key)}</span><span class="font-mono text-muted-foreground">{ROMAN[value] || value}</span></li>
                {/each}
              </ul>
            </div>
          {/if}

          {#if selectedItem.nbt}
            <div>
              <p class="text-[10px] uppercase tracking-wide text-muted-foreground">NBT</p>
              <pre class="mt-1 max-h-48 max-w-full overflow-auto rounded-md bg-muted/40 p-2 font-mono text-[10px] leading-snug whitespace-pre-wrap break-all">{selectedItem.nbt}</pre>
            </div>
          {/if}
        </div>
      {:else}
        <div class="flex h-full min-h-56 items-center justify-center text-center text-sm text-muted-foreground">
          Select an occupied slot to inspect the item.
        </div>
      {/if}
    </div>
  </div>
{/if}
