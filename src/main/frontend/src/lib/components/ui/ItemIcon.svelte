<script lang="ts">
    import {titleCase} from '$lib/utils';

    interface Props {
        type: string;
        class?: string;
    }

    let {type, class: className = ''}: Props = $props();
    let url: string | null = $state(null);
    const normalized = $derived(type.toLowerCase());

    $effect(() => {
        const itemType = normalized;
        let alive = true;
        url = null;
        import('$lib/rendering/itemRenderer')
            .then(({renderItem}) => renderItem(itemType))
            .then((next) => {
                if (alive) url = next;
            })
            .catch(() => {
                if (alive) url = null;
            });
        return () => {
            alive = false;
        };
    });
</script>

{#if url}
    <img class="size-full object-contain inventory-pixelated {className}" src={url} alt={titleCase(type)}/>
{:else}
    <span class="grid size-full place-items-center {className}" title={titleCase(type)}>
        <span class="size-1/2 rounded-sm bg-black/15 dark:bg-white/15"></span>
    </span>
{/if}
