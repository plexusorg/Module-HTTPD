<script lang="ts">
    import type {Snippet} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowDown01Icon} from '@hugeicons/core-free-icons';

    interface Props {
        value: string;
        id: string;
        label: string;
        children: Snippet;
    }

    let {value = $bindable(), id, label, children}: Props = $props();
</script>

<div>
    <label for={id} class="label">{label}</label>
    <div class="relative">
        <select {id} bind:value class="control cursor-pointer appearance-none pr-9">
            {@render children()}
        </select>
        <HugeiconsIcon icon={ArrowDown01Icon}
                       class="pointer-events-none absolute right-3 top-1/2 size-4 -translate-y-1/2 text-faint"/>
    </div>
</div>
