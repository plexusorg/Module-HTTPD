<script lang="ts">
    import type {Snippet} from 'svelte';
    import {HugeiconsIcon} from '@hugeicons/svelte';
    import {
        Cancel01Icon,
        CodeIcon,
        DashboardSquare01Icon,
        JusticeScale01Icon,
        LockIcon,
        Login01Icon,
        Logout01Icon,
        Menu01Icon,
        Moon02Icon,
        PackageIcon,
        Sun02Icon,
        UserGroupIcon
    } from '@hugeicons/core-free-icons';
    import Modal from '$lib/components/ui/Modal.svelte';
    import type {AuthState} from '$lib/types/api';
    import plexLogo from '$lib/assets/plexlogo.webp';
    import {cn} from '$lib/utils';

    interface Props {
        route: string;
        pathname: string;
        auth: AuthState | null;
        dark: boolean;
        onToggleDark: () => void;
        children?: Snippet;
    }

    let {route, pathname, auth, dark, onToggleDark, children}: Props = $props();
    let menuOpen = $state(false);
    const staff = $derived(auth?.is_staff === true);

    const nav = [
        {href: '/', label: 'Overview', icon: DashboardSquare01Icon, match: ['home'], staffOnly: false},
        {href: '/players/', label: 'Players', icon: UserGroupIcon, match: ['players', 'player'], staffOnly: false},
        {href: '/commands/', label: 'Commands', icon: CodeIcon, match: ['commands'], staffOnly: false},
        {href: '/punishments/', label: 'Punishments', icon: JusticeScale01Icon, match: ['punishments', 'punishments-detail'], staffOnly: false},
        {href: '/indefbans/', label: 'Indefinite bans', icon: LockIcon, match: ['indefbans'], staffOnly: true},
        {href: '/schematics/', label: 'Schematics', icon: PackageIcon, match: ['schematics', 'schematics-upload'], staffOnly: false}
    ];

    const current = $derived(nav.find((item) => item.match.includes(route)));
    const loginHref = $derived(`/oauth2/login?return_to=${encodeURIComponent(pathname + window.location.search)}`);

    $effect(() => {
        document.title = route === 'not-found' ? 'Not found · Plex HTTPD' : current ? `${current.label} · Plex HTTPD` : 'Plex HTTPD';
    });
</script>

{#snippet brand()}
    <a href="/" class="flex items-center gap-2.5 rounded-md" aria-label="Plex HTTPD overview">
        <span class="img-outline grid size-7 shrink-0 place-items-center overflow-hidden rounded-md bg-white">
            <img src={plexLogo} alt="" class="size-6" width="24" height="24"/>
        </span>
        <span class="text-sm font-semibold">Plex HTTPD</span>
    </a>
{/snippet}

{#snippet navList()}
    <ul class="flex flex-col gap-px">
        {#each nav as item (item.href)}
            {@const active = item.match.includes(route)}
            <li>
                <a href={item.href} aria-current={active ? 'page' : undefined} onclick={() => (menuOpen = false)}
                   class={cn(
                       'flex h-9 items-center gap-2.5 rounded-md px-2.5 transition-colors',
                       active ? 'bg-sunken font-medium text-ink' : 'text-muted hover:bg-sunken hover:text-ink'
                   )}>
                    <HugeiconsIcon icon={item.icon} strokeWidth={active ? 2 : 1.5} class={cn('size-4', active && 'text-link')}/>
                    <span class="flex-1">{item.label}</span>
                    {#if item.staffOnly && !staff}
                        <HugeiconsIcon icon={LockIcon} class="size-3.5 text-faint"/>
                        <span class="sr-only">(staff only)</span>
                    {/if}
                </a>
            </li>
        {/each}
    </ul>
{/snippet}

{#snippet account()}
    <div class="flex flex-col gap-1">
        {#if auth === null}
            <p class="px-2.5 py-2 text-muted">Checking session…</p>
        {:else if auth.authenticated}
            <div class="flex min-w-0 items-center gap-2.5 py-1 pl-1.5">
                <span class="grid size-7 shrink-0 place-items-center rounded-full bg-brand-soft text-xs font-semibold text-link">{(auth.username ?? '?').slice(0, 1).toUpperCase()}</span>
                <span class="min-w-0 flex-1 leading-tight">
                    <span class="block truncate font-medium">{auth.username}</span>
                    <span class="block text-xs text-muted">{staff ? 'Staff' : 'Signed in'}</span>
                </span>
                <a href="/oauth2/logout" aria-label="Sign out" title="Sign out"
                   class="grid size-9 place-items-center rounded-md text-muted transition-colors hover:bg-sunken hover:text-ink">
                    <HugeiconsIcon icon={Logout01Icon} class="size-4"/>
                </a>
            </div>
        {:else if auth.reason !== 'disabled'}
            <a href={loginHref}
               class="flex h-9 items-center gap-2.5 rounded-md px-2.5 font-medium text-link transition-colors hover:bg-sunken">
                <HugeiconsIcon icon={Login01Icon} class="size-4"/>
                Sign in
            </a>
        {/if}
        <button type="button" onclick={onToggleDark}
                class="flex h-9 items-center gap-2.5 rounded-md px-2.5 text-left text-muted transition-colors hover:bg-sunken hover:text-ink">
            <HugeiconsIcon icon={dark ? Sun02Icon : Moon02Icon} class="size-4"/>
            <span class="flex-1">{dark ? 'Light theme' : 'Dark theme'}</span>
        </button>
    </div>
{/snippet}

<a href="#main"
   class="sr-only z-[60] rounded-md bg-brand px-3 py-2 font-medium text-brand-ink focus:not-sr-only focus:fixed focus:left-3 focus:top-3">
    Skip to content
</a>

<aside class="fixed inset-y-0 left-0 z-40 hidden w-60 flex-col border-r border-line bg-sidebar lg:flex">
    <div class="flex h-14 items-center px-4">{@render brand()}</div>
    <nav aria-label="Main" class="flex-1 overflow-y-auto px-2 py-2">{@render navList()}</nav>
    <div class="px-2 py-3">{@render account()}</div>
</aside>

<header class="sticky top-0 z-40 flex h-14 items-center gap-1 border-b border-line bg-canvas/90 px-4 backdrop-blur-md lg:hidden">
    {@render brand()}
    <button type="button" onclick={onToggleDark} aria-label={dark ? 'Switch to light theme' : 'Switch to dark theme'}
            class="ml-auto grid size-10 place-items-center rounded-md text-muted transition-colors hover:bg-sunken hover:text-ink">
        <HugeiconsIcon icon={dark ? Sun02Icon : Moon02Icon} class="size-[1.125rem]"/>
    </button>
    <button type="button" onclick={() => (menuOpen = true)} aria-label="Open menu" aria-haspopup="dialog"
            aria-expanded={menuOpen}
            class="-mr-2 grid size-10 place-items-center rounded-md text-muted transition-colors hover:bg-sunken hover:text-ink">
        <HugeiconsIcon icon={Menu01Icon} class="size-[1.125rem]"/>
    </button>
</header>

<Modal bind:open={menuOpen} title="Menu" variant="drawer" hideTitle>
    <div class="flex h-14 items-center justify-between pl-4 pr-2">
        {@render brand()}
        <button type="button" onclick={() => (menuOpen = false)} aria-label="Close menu"
                class="grid size-10 place-items-center rounded-md text-muted transition-colors hover:bg-sunken hover:text-ink">
            <HugeiconsIcon icon={Cancel01Icon} class="size-[1.125rem]"/>
        </button>
    </div>
    <nav aria-label="Main" class="flex-1 overflow-y-auto px-2 py-2">{@render navList()}</nav>
    <div class="px-2 py-3">{@render account()}</div>
</Modal>

<main id="main" tabindex="-1" class="min-h-screen outline-none lg:pl-60">
    <div class="mx-auto w-full max-w-[80rem] px-4 pb-16 pt-6 sm:px-8 lg:px-10 lg:pt-10">
        {@render children?.()}
    </div>
</main>
