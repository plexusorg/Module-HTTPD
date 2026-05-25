<script lang="ts">
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {Upload01Icon} from '@hugeicons/core-free-icons';
    import {Button} from '$lib/components/ui/button';
    import {Card} from '$lib/components/ui/card';
    import {postForm} from '$lib/api';

    let file: File | null = $state(null);
    let message: string | null = $state(null);
    let error: string | null = $state(null);
    let submitting = $state(false);

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
        } catch (cause) {
            error = cause instanceof Error ? cause.message : 'Upload failed.';
        } finally {
            submitting = false;
        }
    }
</script>

<section class="rise">
    <h1 class="text-3xl font-medium tracking-tight md:text-4xl">Upload schematic</h1>
</section>

<section class="rise mt-6 max-w-2xl">
    <Card class="p-5">
        <form class="flex flex-col gap-4 sm:flex-row sm:items-center"
              onsubmit={(event) => { event.preventDefault(); submit(); }}>
            <label for="formFile"
                   class="flex flex-1 cursor-pointer items-center gap-3 rounded-xl border border-dashed border-border bg-muted/30 px-4 py-3 text-sm transition-colors hover:border-foreground/30 hover:bg-muted/50">
                <HugeiconsIcon icon={Upload01Icon} class="size-5 text-muted-foreground"/>
                <span class="min-w-0 text-muted-foreground">
          <span class="text-foreground">{file ? file.name : 'Choose a file'}</span>
        </span>
                <input
                        id="formFile"
                        type="file"
                        name="file"
                        class="sr-only"
                        onchange={(event) => {
            file = (event.currentTarget as HTMLInputElement).files?.[0] ?? null;
          }}
                />
            </label>
            <Button type="submit" disabled={!file || submitting}>{submitting ? 'Uploading...' : 'Upload'}</Button>
        </form>
        {#if message}
            <p class="mt-3 text-sm text-success">{message}</p>
        {/if}
        {#if error}
            <p class="mt-3 text-sm text-destructive">{error}</p>
        {/if}
    </Card>
</section>
