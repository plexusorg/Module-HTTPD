<script lang="ts">
  import type { Snippet } from 'svelte';
  import { HugeiconsIcon } from '@hugeicons/svelte';
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
  import { Button } from '$lib/components/ui/button';
  import type { AuthState } from '$lib/types/api';
  import plexLogo from '$lib/assets/plexlogo.webp';
  import { navigate } from '$lib/router';
  import { cn } from '$lib/utils';

  interface Props {
    route: string;
    auth: AuthState | null;
    dark: boolean;
    onToggleDark: () => void;
    children?: Snippet;
  }

  let { route, auth, dark, onToggleDark, children }: Props = $props();
  let menuOpen = $state(false);

  const nav = [
    { href: '/', label: 'Overview', icon: DashboardSquare01Icon, match: ['home'] },
    { href: '/players/', label: 'Players', icon: UserGroupIcon, match: ['players', 'player'] },
    { href: '/commands/', label: 'Commands', icon: CodeIcon, match: ['commands'] },
    { href: '/punishments/', label: 'Punishments', icon: JusticeScale01Icon, match: ['punishments', 'punishments-detail'] },
    { href: '/indefbans/', label: 'Indef Bans', icon: LockIcon, match: ['indefbans'] },
    { href: '/schematics/', label: 'Schematics', icon: PackageIcon, match: ['schematics', 'schematics-upload'] }
  ];

  const loginHref = $derived(`/oauth2/login?return_to=${encodeURIComponent(window.location.pathname + window.location.search)}`);

  function navTo(path: string) {
    menuOpen = false;
    navigate(path);
  }
</script>

<div class="layer-content flex min-h-screen flex-col">
  <header class="sticky top-0 z-50 border-b border-border/60 bg-background/75 backdrop-blur-xl supports-[backdrop-filter]:bg-background/60">
    <div class="mx-auto flex h-14 max-w-7xl items-center gap-4 px-4 sm:px-6">
      <button type="button" class="flex items-center gap-2.5 text-foreground transition-opacity hover:opacity-80" onclick={() => navTo('/')}>
        <img src={plexLogo} alt="" class="size-7 rounded-md" width="28" height="28" />
        <span class="text-sm font-semibold tracking-tight">Plex HTTPD</span>
      </button>

      <nav class="hidden flex-1 items-center gap-1 md:flex">
        {#each nav as item (item.href)}
          <button
            type="button"
            class={cn(
              'group inline-flex h-8 items-center gap-1.5 rounded-full px-3 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
              item.match.includes(route) && 'bg-muted text-foreground'
            )}
            onclick={() => navTo(item.href)}
          >
            <HugeiconsIcon icon={item.icon} class={cn('size-3.5 opacity-70 group-hover:opacity-100', item.match.includes(route) && 'text-primary opacity-100')} aria-hidden="true" />
            {item.label}
          </button>
        {/each}
      </nav>

      <div class="ml-auto flex items-center gap-2">
        {#if auth?.authenticated}
          <span class="hidden text-xs text-muted-foreground sm:inline">{auth.username}</span>
          <Button href="/oauth2/logout" variant="outline" size="sm">
            <HugeiconsIcon icon={Logout01Icon} class="size-3.5" />
            <span class="hidden sm:inline">Sign out</span>
          </Button>
        {:else if auth?.reason !== 'disabled'}
          <Button href={loginHref} variant="outline" size="sm">
            <HugeiconsIcon icon={Login01Icon} class="size-3.5" />
            <span class="hidden sm:inline">Sign in</span>
          </Button>
        {/if}
        <Button variant="ghost" size="icon" aria-label="Toggle theme" onclick={onToggleDark}>
          {#if dark}
            <HugeiconsIcon icon={Sun02Icon} class="size-4" />
          {:else}
            <HugeiconsIcon icon={Moon02Icon} class="size-4" />
          {/if}
        </Button>
        <Button variant="outline" size="icon" class="md:hidden" aria-label="Toggle menu" aria-expanded={menuOpen} onclick={() => (menuOpen = !menuOpen)}>
          {#if menuOpen}
            <HugeiconsIcon icon={Cancel01Icon} class="size-4" />
          {:else}
            <HugeiconsIcon icon={Menu01Icon} class="size-4" />
          {/if}
        </Button>
      </div>
    </div>

    {#if menuOpen}
      <nav class="border-t border-border/60 px-4 py-3 md:hidden">
        {#each nav as item (item.href)}
          <button
            type="button"
            class={cn(
              'flex h-10 w-full items-center gap-2.5 rounded-xl px-3 text-sm text-muted-foreground transition-colors hover:bg-muted hover:text-foreground',
              item.match.includes(route) && 'bg-muted text-foreground'
            )}
            onclick={() => navTo(item.href)}
          >
            <HugeiconsIcon icon={item.icon} class={cn('size-4 opacity-70', item.match.includes(route) && 'text-primary opacity-100')} aria-hidden="true" />
            {item.label}
          </button>
        {/each}
      </nav>
    {/if}
  </header>

  <main class="mx-auto w-full max-w-7xl flex-1 px-4 py-8 sm:px-6 md:py-10">
    {@render children?.()}
  </main>
</div>
