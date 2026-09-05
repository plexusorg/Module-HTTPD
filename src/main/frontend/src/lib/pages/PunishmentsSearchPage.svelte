<script lang="ts">
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {ArrowLeft01Icon, ArrowRight01Icon, Search01Icon} from '@hugeicons/core-free-icons';
    import {Button} from '$lib/components/ui/button';
    import {Input} from '$lib/components/ui/input';
    import {navigate} from '$lib/router';

    let query = $state('');

    function submit() {
        const value = query.trim();
        if (!value) return;
        navigate(`/punishments/${encodeURIComponent(value)}`);
    }
</script>

<section class="rise">
    <a href="/"
       class="mb-4 inline-flex min-h-10 items-center gap-2 rounded-md text-sm text-muted-foreground transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ring">
        <HugeiconsIcon icon={ArrowLeft01Icon} class="size-4"/>
        Back to overview
    </a>
    <h1 class="text-balance text-3xl font-medium tracking-tight md:text-4xl">Punishments</h1>
    <p class="mt-2 text-pretty text-sm text-muted-foreground">Look up a player to review their punishment history.</p>
</section>

<section class="rise ring-card mt-6 max-w-3xl rounded-xl bg-card p-5 sm:p-8" aria-labelledby="lookup-heading">
    <h2 id="lookup-heading" class="text-lg font-medium tracking-tight">Find a player</h2>
    <p id="lookup-help" class="mt-1 text-pretty text-sm text-muted-foreground">
        Enter their full username, or use a UUID to identify a specific player.
    </p>
    <form class="mt-6" onsubmit={(event) => { event.preventDefault(); submit(); }}>
        <label for="punishment-player" class="mb-2 block text-sm font-medium">Username or UUID</label>
        <div class="flex flex-col gap-3 sm:flex-row">
            <div class="relative min-w-0 flex-1">
                <HugeiconsIcon icon={Search01Icon}
                               class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"/>
                <Input id="punishment-player" bind:value={query} placeholder="Enter username or UUID"
                       aria-describedby="lookup-help" autocomplete="off" autocapitalize="none" spellcheck={false}
                       required class="h-11 pl-9"/>
            </div>
            <Button type="submit" disabled={!query.trim()} class="h-11 transition-[color,background-color,box-shadow]">
                View history
                <HugeiconsIcon icon={ArrowRight01Icon} class="size-4"/>
            </Button>
        </div>
    </form>
</section>
