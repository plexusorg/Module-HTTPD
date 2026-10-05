const KEY = 'plex-httpd-recent-lookups';
const LIMIT = 8;

export interface RecentLookup {
    uuid: string;
    name: string;
}

export function readRecentLookups(): RecentLookup[] {
    try {
        const parsed: unknown = JSON.parse(localStorage.getItem(KEY) ?? '[]');
        if (!Array.isArray(parsed)) return [];
        return parsed.filter((item): item is RecentLookup => typeof item?.uuid === 'string' && typeof item?.name === 'string');
    } catch {
        return [];
    }
}

export function rememberLookup(entry: RecentLookup) {
    const next = [entry, ...readRecentLookups().filter((item) => item.uuid !== entry.uuid)].slice(0, LIMIT);
    try {
        localStorage.setItem(KEY, JSON.stringify(next));
    } catch {
    }
}

export function clearRecentLookups() {
    try {
        localStorage.removeItem(KEY);
    } catch {
    }
}
