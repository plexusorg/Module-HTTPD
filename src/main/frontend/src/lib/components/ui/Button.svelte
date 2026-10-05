<script lang="ts" module>
    export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'warn';
    export type ButtonSize = 'sm' | 'md' | 'lg';
</script>

<script lang="ts">
    import type {Snippet} from 'svelte';
    import type {HTMLAnchorAttributes, HTMLButtonAttributes} from 'svelte/elements';
    import {cn} from '$lib/utils';

    type Props = {
        variant?: ButtonVariant;
        size?: ButtonSize;
        href?: string;
        class?: string;
        children?: Snippet;
    } & Omit<HTMLButtonAttributes, 'class'> & Omit<HTMLAnchorAttributes, 'class' | 'href'>;

    let {variant = 'secondary', size = 'md', href, class: className = '', type = 'button', children, ...rest}: Props = $props();

    const variants: Record<ButtonVariant, string> = {
        primary: 'bg-brand text-brand-ink border-transparent hover:bg-brand/90',
        secondary: 'bg-surface text-ink border-line-strong hover:bg-sunken',
        ghost: 'border-transparent text-muted hover:bg-sunken hover:text-ink',
        danger: 'bg-danger text-canvas border-transparent hover:bg-danger/90',
        warn: 'bg-warn-soft text-warn border-transparent hover:bg-warn/20'
    };

    const sizes: Record<ButtonSize, string> = {
        sm: 'min-h-8 gap-1.5 px-2.5',
        md: 'min-h-9 gap-2 px-3.5',
        lg: 'min-h-11 gap-2 px-5'
    };

    const classes = $derived(cn(
        'inline-flex shrink-0 select-none items-center justify-center whitespace-nowrap rounded-md border text-sm font-medium',
        'transition-[background-color,border-color,color,scale] duration-150 ease-out active:scale-[0.96]',
        'disabled:pointer-events-none disabled:opacity-50 aria-disabled:pointer-events-none aria-disabled:opacity-50',
        '[&_svg]:size-4 [&_svg]:shrink-0',
        variants[variant],
        sizes[size],
        className
    ));
</script>

{#if href}
    <a {href} class={classes} {...rest as HTMLAnchorAttributes}>{@render children?.()}</a>
{:else}
    <button {type} class={classes} {...rest as HTMLButtonAttributes}>{@render children?.()}</button>
{/if}
