<script lang="ts">
    import PageHeader, {type Crumb} from '$lib/components/layout/PageHeader.svelte';
    import Button from '$lib/components/ui/Button.svelte';
    import Notice from '$lib/components/ui/Notice.svelte';
    import type {AuthState} from '$lib/types/api';

    interface Props {
        auth: AuthState | null;
        title: string;
        trail: Crumb[];
        action: string;
    }

    let {auth, title, trail, action}: Props = $props();
    const loginHref = $derived(`/oauth2/login?return_to=${encodeURIComponent(window.location.pathname + window.location.search)}`);
    const parent = $derived(trail.at(-1));
</script>

<PageHeader {title} {trail}/>

{#if auth === null}
    <Notice kind="loading" title="Checking access"/>
{:else}
    <Notice kind="locked" title="Staff access required"
            message={auth.authenticated ? `Your account does not have staff access. Only staff can ${action}.` : `Sign in with a staff account to ${action}.`}>
        {#if auth.reason !== 'disabled'}
            <Button href={loginHref} variant="primary">{auth.authenticated ? 'Sign in with another account' : 'Sign in'}</Button>
        {/if}
        {#if parent}
            <Button href={parent.href}>Back to {parent.label.toLowerCase()}</Button>
        {/if}
    </Notice>
{/if}
