<script lang="ts" module>
    export type TagTone = 'neutral' | 'brand' | 'danger' | 'warn' | 'ok';
</script>

<script lang="ts">
    import type {Snippet} from 'svelte';
    import {cn} from '$lib/utils';

    interface Props {
        tone?: TagTone;
        dot?: boolean;
        class?: string;
        children?: Snippet;
    }

    let {tone = 'neutral', dot = false, class: className = '', children}: Props = $props();

    const tones: Record<TagTone, string> = {
        neutral: 'bg-sunken text-muted',
        brand: 'bg-brand-soft text-link',
        danger: 'bg-danger-soft text-danger',
        warn: 'bg-warn-soft text-warn',
        ok: 'bg-ok-soft text-ok'
    };
</script>

<span class={cn('inline-flex h-5 shrink-0 items-center gap-1.5 rounded-sm px-1.5 text-xs font-medium leading-none', tones[tone], className)}>
    {#if dot}<span class="size-1.5 rounded-full bg-current" aria-hidden="true"></span>{/if}
    {@render children?.()}
</span>
