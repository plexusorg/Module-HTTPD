<script lang="ts">
    import {onMount} from 'svelte';
    import StaffRequired from '$lib/components/auth/StaffRequired.svelte';
    import AppShell from '$lib/components/layout/AppShell.svelte';
    import PageHeader from '$lib/components/layout/PageHeader.svelte';
    import Button from '$lib/components/ui/Button.svelte';
    import Notice from '$lib/components/ui/Notice.svelte';
    import {getAuth} from '$lib/api';
    import {isInternalAppLink, navigate, parseRoute} from '$lib/router';
    import type {AuthState} from '$lib/types/api';

    let route = $state(parseRoute(window.location.pathname));
    let pathname = $state(window.location.pathname);
    let auth: AuthState | null = $state(null);
    let dark = $state(false);
    const staff = $derived((auth as AuthState | null)?.is_staff === true);

    function syncRoute() {
        route = parseRoute(window.location.pathname);
        pathname = window.location.pathname;
    }

    function toggleDark() {
        dark = !dark;
        document.documentElement.classList.toggle('dark', dark);
        localStorage.setItem('plex-httpd-theme', dark ? 'dark' : 'light');
    }

    onMount(() => {
        const storedTheme = localStorage.getItem('plex-httpd-theme');
        dark = storedTheme ? storedTheme === 'dark' : window.matchMedia('(prefers-color-scheme: dark)').matches;
        document.documentElement.classList.toggle('dark', dark);

        getAuth().then((state) => (auth = state)).catch(() => (auth = {authenticated: false}));

        const onClick = (event: MouseEvent) => {
            if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
            const anchor = (event.target as HTMLElement).closest('a');
            if (!(anchor instanceof HTMLAnchorElement) || anchor.getAttribute('href')?.startsWith('#') || !isInternalAppLink(anchor)) return;
            event.preventDefault();
            navigate(new URL(anchor.href).pathname);
        };

        window.addEventListener('popstate', syncRoute);
        document.addEventListener('click', onClick);
        return () => {
            window.removeEventListener('popstate', syncRoute);
            document.removeEventListener('click', onClick);
        };
    });
</script>

<AppShell route={route.path} {pathname} {auth} {dark} onToggleDark={toggleDark}>
    {#if route.path === 'home'}
        {#await import('$lib/pages/HomePage.svelte') then {default: HomePage}}
            <HomePage/>
        {/await}
    {:else if route.path === 'players'}
        {#if auth === null}
            <PageHeader title="Players" trail={[{href: '/', label: 'Overview'}]}/>
            <Notice kind="loading" title="Checking your session"/>
        {:else}
            {#await import('$lib/pages/PlayersPage.svelte') then {default: PlayersPage}}
                <PlayersPage {staff}/>
            {/await}
        {/if}
    {:else if route.path === 'player'}
        {#if staff}
            {#await import('$lib/pages/PlayerPage.svelte') then {default: PlayerPage}}
                <PlayerPage id={route.params.id} {staff}/>
            {/await}
        {:else}
            <StaffRequired {auth} title="Player admin" trail={[{href: '/', label: 'Overview'}, {href: '/players/', label: 'Players'}]}
                           action="open player admin tools"/>
        {/if}
    {:else if route.path === 'commands'}
        {#await import('$lib/pages/CommandsPage.svelte') then {default: CommandsPage}}
            <CommandsPage/>
        {/await}
    {:else if route.path === 'punishments'}
        {#await import('$lib/pages/PunishmentsSearchPage.svelte') then {default: PunishmentsSearchPage}}
            <PunishmentsSearchPage/>
        {/await}
    {:else if route.path === 'punishments-detail'}
        {#await import('$lib/pages/PunishmentsDetailPage.svelte') then {default: PunishmentsDetailPage}}
            {#key route.params.id}
                <PunishmentsDetailPage id={route.params.id} {staff}/>
            {/key}
        {/await}
    {:else if route.path === 'indefbans'}
        {#if staff}
            {#await import('$lib/pages/IndefBansPage.svelte') then {default: IndefBansPage}}
                <IndefBansPage/>
            {/await}
        {:else}
            <StaffRequired {auth} title="Indefinite bans" trail={[{href: '/', label: 'Overview'}]} action="view indefinite bans"/>
        {/if}
    {:else if route.path === 'schematics'}
        {#await import('$lib/pages/SchematicsPage.svelte') then {default: SchematicsPage}}
            <SchematicsPage {staff}/>
        {/await}
    {:else if route.path === 'schematics-upload'}
        {#if staff}
            {#await import('$lib/pages/SchematicUploadPage.svelte') then {default: SchematicUploadPage}}
                <SchematicUploadPage/>
            {/await}
        {:else}
            <StaffRequired {auth} title="Upload schematic" trail={[{href: '/', label: 'Overview'}, {href: '/schematics/', label: 'Schematics'}]}
                           action="upload schematics"/>
        {/if}
    {:else}
        <PageHeader title="Not found" trail={[{href: '/', label: 'Overview'}]}/>
        <Notice kind="empty" title="Nothing lives at this address" message={`No page matches ${pathname}. The link may be old or mistyped.`}>
            <Button href="/" variant="primary">Go to overview</Button>
            <Button href="/punishments/">Look up a player</Button>
        </Notice>
    {/if}
</AppShell>
