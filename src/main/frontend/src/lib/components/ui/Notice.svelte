<script lang="ts">
    import type {Snippet} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {Alert02Icon, InboxIcon, LockIcon, Loading03Icon} from '@hugeicons/core-free-icons';
    import {cn} from '$lib/utils';

    interface Props {
        kind: 'loading' | 'empty' | 'error' | 'locked';
        title: string;
        message?: string;
        class?: string;
        children?: Snippet;
    }

    let {kind, title, message, class: className = '', children}: Props = $props();

    const icons = {loading: Loading03Icon, empty: InboxIcon, error: Alert02Icon, locked: LockIcon} as const;
</script>

<section role={kind === 'error' ? 'alert' : kind === 'loading' ? 'status' : undefined}
         class={cn('panel flex flex-col items-center px-6 py-12 text-center', kind === 'loading' && 'notice-loading', className)}>
    <HugeiconsIcon icon={icons[kind]} class={cn('size-5', kind === 'error' ? 'text-danger' : 'text-faint', kind === 'loading' && 'notice-spin')}/>
    <h2 class="mt-3 text-base font-semibold">{title}</h2>
    {#if message}
        <p class="mt-1 max-w-md break-words text-muted">{message}</p>
    {/if}
    {#if children}
        <div class="mt-5 flex flex-wrap items-center justify-center gap-2">{@render children()}</div>
    {/if}
</section>

<style>
    /* Fade in late so fast loads do not flash. */
    .notice-loading {
        animation: notice-in 200ms ease-out 150ms backwards;
    }

    .notice-loading :global(.notice-spin) {
        animation: spin 1s linear infinite;
    }

    @keyframes notice-in {
        from {
            opacity: 0;
        }
    }

    @keyframes spin {
        to {
            transform: rotate(360deg);
        }
    }
</style>
