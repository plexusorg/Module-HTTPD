<script lang="ts">
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {Cancel01Icon, Search01Icon} from '@hugeicons/core-free-icons';
    import {cn} from '$lib/utils';

    interface Props {
        value: string;
        label: string;
        placeholder?: string;
        id?: string;
        hideLabel?: boolean;
        class?: string;
    }

    let {value = $bindable(''), label, placeholder, id, hideLabel = false, class: className = ''}: Props = $props();
    const fieldId = $derived(id ?? `filter-${label.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`);
    let input: HTMLInputElement | undefined = $state();

    function clear() {
        value = '';
        input?.focus();
    }
</script>

<div class={cn('w-full', className)}>
    <label for={fieldId} class={hideLabel ? 'sr-only' : 'label'}>{label}</label>
    <div class="relative">
        <HugeiconsIcon icon={Search01Icon}
                       class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-faint"/>
        <input bind:this={input} id={fieldId} type="text" bind:value {placeholder} autocomplete="off"
               autocapitalize="none" spellcheck={false} class="control pl-9 pr-9"
               onkeydown={(event) => { if (event.key === 'Escape' && value) { event.preventDefault(); value = ''; } }}/>
        {#if value}
            <button type="button" onclick={clear} aria-label="Clear filter"
                    class="absolute right-1 top-1/2 grid size-7 -translate-y-1/2 place-items-center rounded-sm text-faint transition-colors hover:bg-sunken hover:text-ink">
                <HugeiconsIcon icon={Cancel01Icon} class="size-3.5"/>
            </button>
        {/if}
    </div>
</div>
