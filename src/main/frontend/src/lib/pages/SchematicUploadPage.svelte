<script lang="ts">
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {CheckmarkCircle02Icon, Upload01Icon} from '@hugeicons/core-free-icons';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import Button from '$lib/components/ui/Button.svelte';
    import {postForm} from '$lib/api';
    import {cn, formatBytes} from '$lib/utils';

    let file: File | null = $state(null);
    let message: string | null = $state(null);
    let error: string | null = $state(null);
    let submitting = $state(false);
    let dragging = $state(false);
    let input: HTMLInputElement | undefined = $state();

    async function submit() {
        if (!file) return;
        const form = new FormData();
        form.set('file', file);
        submitting = true;
        message = null;
        error = null;
        try {
            const result = await postForm<Record<string, unknown>>('/api/schematics/upload', form);
            message = String(result.message ?? 'Upload complete.');
            file = null;
            if (input) input.value = '';
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Upload failed.';
        } finally {
            submitting = false;
        }
    }

    function onDrop(event: DragEvent) {
        event.preventDefault();
        dragging = false;
        const dropped = event.dataTransfer?.files?.[0];
        if (dropped) file = dropped;
    }
</script>

<PageHeader title="Upload schematic" trail={[{href: '/', label: 'Overview'}, {href: '/schematics/', label: 'Schematics'}]}/>

<form onsubmit={(event) => { event.preventDefault(); submit(); }}>
    <label for="formFile"
           ondragover={(event) => { event.preventDefault(); dragging = true; }}
           ondragleave={() => (dragging = false)}
           ondrop={onDrop}
           class={cn(
               'flex cursor-pointer flex-col items-center gap-1 rounded-lg border border-dashed px-6 py-12 text-center transition-colors',
               'has-[:focus-visible]:border-brand has-[:focus-visible]:bg-brand-soft',
               dragging ? 'border-brand bg-brand-soft' : 'border-line-strong bg-surface hover:bg-sunken/60'
           )}>
        <HugeiconsIcon icon={Upload01Icon} class="mb-2 size-5 text-faint"/>
        {#if file}
            <span class="max-w-full break-all font-medium">{file.name}</span>
            <span class="text-muted tabular-nums">{formatBytes(file.size)} · <span class="link">Choose a different file</span></span>
        {:else}
            <span class="font-medium">Drop a schematic here</span>
            <span class="text-muted">or <span class="link">browse your files</span></span>
        {/if}
        <input
                bind:this={input}
                id="formFile"
                type="file"
                name="file"
                class="sr-only"
                onchange={(event) => {
                    file = (event.currentTarget as HTMLInputElement).files?.[0] ?? null;
                }}
        />
    </label>

    <div class="mt-4 flex flex-wrap items-center gap-2">
        <Button type="submit" variant="primary" disabled={!file || submitting}>{submitting ? 'Uploading…' : 'Upload'}</Button>
        <Button href="/schematics/" variant="ghost">Cancel</Button>
    </div>

    {#if message}
        <div class="mt-5 flex flex-wrap items-center gap-3 rounded-md bg-ok-soft px-4 py-3 text-ok" role="status">
            <HugeiconsIcon icon={CheckmarkCircle02Icon} class="size-4 shrink-0"/>
            <span class="flex-1">{message}</span>
            <a href="/schematics/" class="link">View schematics</a>
        </div>
    {/if}
    {#if error}
        <p class="mt-5 rounded-md bg-danger-soft px-4 py-3 text-danger" role="alert">{error}</p>
    {/if}
</form>