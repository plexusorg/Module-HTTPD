import type {
    AuthState,
    CommandGroup,
    PlayerDetails,
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

export const api = {
    commands: () => getJson<{ groups: CommandGroup[] }>('/api/commands/'),
    player: (id: string) => getJson<{ player: PlayerDetails }>(`/api/player/${encodeURIComponent(id)}`),
    punishments: (id: string) => getJson<PunishmentsPayload>(`/api/punishments/${encodeURIComponent(id)}`),
    indefiniteBans: () => getJson<Array<Record<string, unknown>>>('/api/indefbans/'),
    schematics: () => getJson<{ schematics: Schematic[] }>('/api/schematics/list')
};
