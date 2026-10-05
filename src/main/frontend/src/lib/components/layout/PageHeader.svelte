<script lang="ts" module>
    export interface Crumb {
        href: string;
        label: string;
    }
</script>

<script lang="ts">
    import type {Snippet} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowLeft01Icon} from '@hugeicons/core-free-icons';

    interface Props {
        title: string;
        trail?: Crumb[];
        lead?: Snippet;
        meta?: Snippet;
        actions?: Snippet;
        children?: Snippet;
    }

    let {title, trail = [], lead, meta, actions, children}: Props = $props();
    const parent = $derived(trail.at(-1));
</script>

<header class="mb-8">
    {#if parent}
        <nav aria-label="Breadcrumb" class="mb-3">
            <a href={parent.href}
               class="-ml-1 inline-flex items-center gap-1 rounded-sm px-1 text-muted transition-colors hover:text-ink sm:hidden">
                <HugeiconsIcon icon={ArrowLeft01Icon} class="size-3.5"/>
                {parent.label}
            </a>
            <ol class="hidden flex-wrap items-center gap-1.5 text-muted sm:flex">
                {#each trail as crumb (crumb.href)}
                    <li class="flex items-center gap-1.5">
                        <a href={crumb.href} class="rounded-sm transition-colors hover:text-ink">{crumb.label}</a>
                        <span class="text-faint" aria-hidden="true">/</span>
                    </li>
                {/each}
                <li aria-current="page" class="max-w-[32ch] truncate text-ink">{title}</li>
            </ol>
        </nav>
    {/if}

    <div class="flex flex-wrap items-end justify-between gap-x-6 gap-y-4">
        <div class="flex min-w-0 items-center gap-4">
            {@render lead?.()}
            <div class="min-w-0">
                <h1 class="break-words text-2xl font-semibold tracking-tight">{title}</h1>
                {#if meta}
                    <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-muted">{@render meta()}</div>
                {/if}
            </div>
        </div>
        {#if actions}
            <div class="flex w-full flex-wrap items-center gap-2 sm:w-auto">{@render actions()}</div>
        {/if}
    </div>

    {#if children}
        <div class="mt-6">{@render children()}</div>
    {/if}
</header>
