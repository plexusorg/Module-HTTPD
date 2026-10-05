<script lang="ts">
    import type {Snippet} from 'svelte';
    import {cn} from '$lib/utils';

    interface Props {
        open: boolean;
        title: string;
        variant?: 'dialog' | 'drawer';
        hideTitle?: boolean;
        description?: Snippet;
        children?: Snippet;
        footer?: Snippet;
        onclose?: () => void;
    }

    let {open = $bindable(false), title, variant = 'dialog', hideTitle = false, description, children, footer, onclose}: Props = $props();
    const uid = $props.id();
    let dialog: HTMLDialogElement | undefined = $state();
    let returnFocus: HTMLElement | null = null;

    $effect(() => {
        if (!dialog) return;
        if (open && !dialog.open) {
            returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null;
            dialog.showModal();
        } else if (!open && dialog.open) {
            dialog.close();
        }
    });

    $effect(() => {
        const element = dialog;
        if (!element) return;
        let downOnBackdrop = false;
        const onPointerDown = (event: PointerEvent) => (downOnBackdrop = event.target === element);
        const onClick = (event: MouseEvent) => {
            if (downOnBackdrop && event.target === element) element.close();
            downOnBackdrop = false;
        };
        const onClose = () => {
            open = false;
            onclose?.();
            returnFocus?.focus();
            returnFocus = null;
        };
        element.addEventListener('pointerdown', onPointerDown);
        element.addEventListener('click', onClick);
        element.addEventListener('close', onClose);
        return () => {
            element.removeEventListener('pointerdown', onPointerDown);
            element.removeEventListener('click', onClick);
            element.removeEventListener('close', onClose);
            if (element.open) element.close();
        };
    });
</script>

<dialog bind:this={dialog} aria-modal="true" aria-labelledby="{uid}-title"
        aria-describedby={description ? `${uid}-description` : undefined}
        class={cn('modal m-0 max-h-none max-w-none bg-transparent p-0 text-ink backdrop:bg-transparent', variant === 'drawer' ? 'modal-drawer' : 'modal-dialog')}>
    <div class={cn(
        'modal-body flex flex-col shadow-[0_0_0_1px_oklch(0_0_0/0.06),0_16px_48px_-12px_oklch(0_0_0/0.3)] dark:shadow-[0_0_0_1px_oklch(1_0_0/0.08),0_16px_48px_-12px_oklch(0_0_0/0.6)]',
        variant === 'drawer' ? 'h-dvh w-[min(18rem,86vw)] bg-sidebar' : 'max-h-[calc(100dvh-2rem)] w-[min(28rem,calc(100vw-2rem))] overflow-y-auto rounded-lg bg-surface'
    )}>
        <div class={variant === 'drawer' ? 'contents' : 'px-5 pt-5'}>
            <h2 id="{uid}-title" class={hideTitle ? 'sr-only' : 'text-base font-semibold'}>{title}</h2>
            {#if description}
                <div id="{uid}-description" class="mt-1 text-muted">{@render description()}</div>
            {/if}
        </div>
        {#if children}
            <div class={variant === 'drawer' ? 'flex min-h-0 flex-1 flex-col' : 'grid gap-4 px-5 pb-1 pt-4'}>{@render children()}</div>
        {/if}
        {#if footer}
            <div class="flex flex-wrap justify-end gap-2 px-5 pb-5 pt-4">{@render footer()}</div>
        {/if}
    </div>
</dialog>

<style>
    .modal::backdrop {
        background: oklch(0.15 0.005 264 / 0.45);
        animation: backdrop-in 150ms ease-out;
    }

    .modal-dialog[open] {
        inset: 0;
        margin: auto;
        width: fit-content;
        height: fit-content;
    }

    .modal-dialog[open] .modal-body {
        animation: dialog-in 180ms cubic-bezier(0.2, 0, 0, 1);
    }

    .modal-drawer[open] {
        inset: 0 auto 0 0;
        height: 100dvh;
    }

    .modal-drawer[open] .modal-body {
        animation: drawer-in 220ms cubic-bezier(0.2, 0, 0, 1);
    }

    @keyframes backdrop-in {
        from {
            opacity: 0;
        }
    }

    @keyframes dialog-in {
        from {
            opacity: 0;
            transform: translateY(6px) scale(0.98);
        }
    }

    @keyframes drawer-in {
        from {
            transform: translateX(-100%);
        }
    }
</style>
