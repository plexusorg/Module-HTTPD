<script lang="ts">
  import { onMount } from 'svelte';
  import { titleCase } from '$lib/utils';

  interface Props {
    type: string;
    class?: string;
  }

  let { type, class: className = '' }: Props = $props();
  let url: string | null = $state(null);
  const normalized = $derived(type.toLowerCase());

  onMount(() => {
    let alive = true;
    import('$lib/rendering/itemRenderer')
      .then(({ renderItem }) => renderItem(normalized))
      .then((next) => {
        if (alive) url = next;
      });
    return () => {
      alive = false;
    };
  });
</script>

{#if url}
  <img class="size-full object-contain inventory-pixelated {className}" src={url} alt={titleCase(type)} />
{:else}
  <span class="grid size-full place-items-center px-0.5 text-center font-mono text-[8px] leading-tight text-muted-foreground {className}">
    {normalized.replace(/_/g, ' ')}
  </span>
{/if}
