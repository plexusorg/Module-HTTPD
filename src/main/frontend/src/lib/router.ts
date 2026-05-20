export interface Route {
  path: string;
  params: Record<string, string>;
}

const routes = [
  { id: 'home', pattern: /^\/$/ },
  { id: 'players', pattern: /^\/players\/?$/ },
  { id: 'player', pattern: /^\/player\/([^/]+)\/?$/ },
  { id: 'commands', pattern: /^\/commands\/?$/ },
  { id: 'punishments', pattern: /^\/punishments\/?$/ },
  { id: 'punishments-detail', pattern: /^\/punishments\/([^/]+)\/?$/ },
  { id: 'indefbans', pattern: /^\/indefbans\/?$/ },
  { id: 'schematics', pattern: /^\/schematics\/?$/ },
  { id: 'schematics-upload', pattern: /^\/schematics\/upload\/?$/ }
] as const;

export type RouteId = (typeof routes)[number]['id'] | 'not-found';

export function parseRoute(pathname: string): Route {
  for (const route of routes) {
    const match = pathname.match(route.pattern);
    if (!match) continue;
    const params: Record<string, string> = {};
    if (route.id === 'player' || route.id === 'punishments-detail') {
      params.id = decodeURIComponent(match[1]);
    }
    return { path: route.id, params };
  }
  return { path: 'not-found', params: {} };
}

export function navigate(path: string) {
  history.pushState({}, '', path);
  window.dispatchEvent(new PopStateEvent('popstate'));
}

export function isInternalAppLink(anchor: HTMLAnchorElement) {
  if (anchor.target || anchor.hasAttribute('download')) return false;
  const url = new URL(anchor.href);
  if (url.origin !== window.location.origin) return false;
  return routes.some((route) => route.pattern.test(url.pathname));
}
