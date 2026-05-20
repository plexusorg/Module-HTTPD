<script lang="ts">
  import { HugeiconsIcon } from '@hugeicons/svelte';
  import { ArrowRight01Icon, Search01Icon } from '@hugeicons/core-free-icons';
  import { Button } from '$lib/components/ui/button';
  import { Input } from '$lib/components/ui/input';
  import { navigate } from '$lib/router';

  let query = $state('');

  function submit() {
    const value = query.trim();
    if (!value) return;
    navigate(`/punishments/${encodeURIComponent(value)}`);
  }
</script>

<section class="rise">
  <h1 class="text-3xl font-medium tracking-tight md:text-4xl">Punishments</h1>
</section>

<form class="rise mt-6 flex flex-col gap-3 sm:flex-row sm:items-center" onsubmit={(event) => { event.preventDefault(); submit(); }}>
  <div class="relative w-full sm:max-w-md">
    <HugeiconsIcon icon={Search01Icon} class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
    <Input bind:value={query} autofocus placeholder="UUID or username" autocomplete="off" class="pl-9" />
  </div>
  <Button type="submit">
    Search
    <HugeiconsIcon icon={ArrowRight01Icon} class="size-3.5" />
  </Button>
</form>
