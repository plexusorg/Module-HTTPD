<script lang="ts">
    import {untrack} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {Search01Icon} from '@hugeicons/core-free-icons';
    import Button from '$lib/components/ui/Button.svelte';
    import {navigate} from '$lib/router';
    import {cn} from '$lib/utils';

    interface Props {
        label: string;
        size?: 'large' | 'compact';
        initial?: string;
        class?: string;
    }

    let {label, size = 'compact', initial = '', class: className = ''}: Props = $props();
    const uid = $props.id();
    let query = $state(untrack(() => initial));
    const large = $derived(size === 'large');

    function submit(event: SubmitEvent) {
        event.preventDefault();
        const value = query.trim();
        if (!value) return;
        navigate(`/punishments/${encodeURIComponent(value)}`);
    }
</script>

<form role="search" aria-label={label} onsubmit={submit} class={cn('w-full', className)}>
    <label for="{uid}-player" class="label">{label}</label>
    <div class="flex gap-2">
        <div class="relative min-w-0 flex-1">
            <HugeiconsIcon icon={Search01Icon}
                           class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-faint"/>
            <input id="{uid}-player" bind:value={query} placeholder="Username or UUID" autocomplete="off"
                   autocapitalize="none" spellcheck={false} required aria-describedby={large ? `${uid}-help` : undefined}
                   class={cn('control pl-9', large && 'h-11 text-[0.9375rem]')}/>
        </div>
        <Button type="submit" variant="primary" size={large ? 'lg' : 'md'} disabled={!query.trim()}>Look up</Button>
    </div>
    {#if large}
        <p id="{uid}-help" class="mt-2 text-muted">Enter a full username, or a UUID to match one exact player.</p>
    {/if}
</form>
