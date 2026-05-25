<script lang="ts">
    import {Button} from '$lib/components/ui/button';
    import {Card} from '$lib/components/ui/card';
    import type {AuthState} from '$lib/types/api';

    interface Props {
        auth: AuthState | null;
        action: string;
    }

    let {auth, action}: Props = $props();
    const loginHref = $derived(`/oauth2/login?return_to=${encodeURIComponent(window.location.pathname + window.location.search)}`);
</script>

{#if auth === null}
    <p class="rise text-sm text-muted-foreground">Checking access...</p>
{:else}
    <Card class="rise max-w-xl p-5">
        <h1 class="text-xl font-medium">Staff access required</h1>
        <p class="mt-2 text-sm text-muted-foreground">You must sign in as staff to {action}.</p>
        {#if auth.reason !== 'disabled'}
            <Button href={loginHref} class="mt-4">Sign in</Button>
        {/if}
    </Card>
{/if}
