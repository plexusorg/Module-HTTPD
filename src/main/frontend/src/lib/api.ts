import type {
    AuthState,
    CommandGroup,
    PlayerDetails,
    PunishmentSummary,
    PunishmentsPayload,
    Schematic
} from '$lib/types/api';

export async function getJson<T>(url: string, timeoutMs = 15_000): Promise<T> {
    const controller = new AbortController();
    const timeout = window.setTimeout(() => controller.abort(), timeoutMs);
    try {
        const response = await fetch(url, {
            credentials: 'same-origin',
            headers: {Accept: 'application/json'},
            signal: controller.signal
        });
        const body = await response.json().catch(() => null);
        if (!response.ok || (body && typeof body === 'object' && 'error' in body)) {
            const message = body && typeof body === 'object' && 'error' in body ? String(body.error) : `${response.status} ${response.statusText}`;
            throw new Error(message);
        }
        return body as T;
    } catch (cause) {
        if (cause instanceof DOMException && cause.name === 'AbortError') {
            throw new Error('Request timed out.');
        }
        throw cause;
    } finally {
        window.clearTimeout(timeout);
    }
}

export async function getAuth(): Promise<AuthState> {
    const response = await fetch('/oauth2/me', {
        credentials: 'same-origin',
        headers: {Accept: 'application/json'}
    });
    const body = await response.json().catch(() => null);
    if (body && typeof body === 'object' && 'authenticated' in body) return body as AuthState;
    return {authenticated: false};
}

export function postForm<T>(url: string, form: FormData): Promise<T> {
    return fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        body: form,
        headers: {Accept: 'application/json'}
    }).then(async (response) => {
        const body = await response.json().catch(() => null);
        if (!response.ok || (body && typeof body === 'object' && body.ok === false)) {
            const message = body?.error ?? body?.message ?? `${response.status} ${response.statusText}`;
            throw new Error(String(message));
        }
        return body as T;
    });
}

export function postUrlEncoded<T>(url: string, form: URLSearchParams): Promise<T> {
    return fetch(url, {
        method: 'POST',
        credentials: 'same-origin',
        body: form,
        headers: {
            Accept: 'application/json',
            'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
        }
    }).then(async (response) => {
        const body = await response.json().catch(() => null);
        if (!response.ok || (body && typeof body === 'object' && body.ok === false)) {
            const message = body?.error ?? body?.message ?? `${response.status} ${response.statusText}`;
            throw new Error(String(message));
        }
        return body as T;
    });
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null;
}

function isPunishment(value: unknown): value is PunishmentSummary {
    if (!isRecord(value)) return false;
    return typeof value.punished === 'string'
        && (value.punisher == null || typeof value.punisher === 'string')
        && (value.source === 'PLAYER' || value.source === 'CONSOLE' || value.source === 'WEB')
        && typeof value.punisherDisplayName === 'string'
        && typeof value.ip === 'string'
        && typeof value.type === 'string'
        && typeof value.reason === 'string'
        && typeof value.active === 'boolean'
        && typeof value.issueDate === 'number'
        && Number.isFinite(value.issueDate)
        && 'endDate' in value
        && (value.endDate === null || (typeof value.endDate === 'number' && Number.isFinite(value.endDate)));
}

function decodePunishments(value: unknown): PunishmentsPayload {
    if (!isRecord(value) || !isRecord(value.player) || !Array.isArray(value.punishments)
        || typeof value.player.uuid !== 'string' || typeof value.player.name !== 'string'
        || typeof value.canViewIps !== 'boolean' || !isRecord(value.pagination)
        || typeof value.pagination.offset !== 'number' || typeof value.pagination.limit !== 'number'
        || typeof value.pagination.total !== 'number' || typeof value.pagination.hasMore !== 'boolean') {
        throw new Error('The punishment server returned an invalid response.');
    }
    const invalidIndex = value.punishments.findIndex((item) => !isPunishment(item));
    if (invalidIndex !== -1) {
        throw new Error(`The punishment server returned an invalid entry at index ${invalidIndex}.`);
    }
    return value as unknown as PunishmentsPayload;
}

export const api = {
    commands: () => getJson<{ groups: CommandGroup[] }>('/api/commands/'),
    player: (id: string) => getJson<{ player: PlayerDetails }>(`/api/player/${encodeURIComponent(id)}`),
    punishments: async (id: string, offset = 0, limit = 50) => decodePunishments(await getJson<unknown>(
        `/api/punishments/${encodeURIComponent(id)}?offset=${offset}&limit=${limit}`
    )),
    indefiniteBans: () => getJson<Array<Record<string, unknown>>>('/api/indefbans/'),
    schematics: () => getJson<{ schematics: Schematic[] }>('/api/schematics/list')
};
