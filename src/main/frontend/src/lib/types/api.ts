export interface AuthState {
    authenticated: boolean;
    reason?: string;
    user_id?: string;
    username?: string;
    is_staff?: boolean;
}

export interface StatsPayload {
    server: { version: string; startTime: number; tps: number[] };
    cpu: { process: number; system: number; cores: number; loadAverage: number };
    memory: { used: number; total: number; max: number };
    players: { online: number; max: number };
    world: { loadedChunks: number; entities: number; worlds: number };
    plugins: { active: number };
}

export interface PlayerSummary {
    uuid: string;
    name: string;
    world?: string;
    ping: number;
    op?: boolean;
    gamemode?: string;
}

export interface PlayersPayload {
    players: PlayerSummary[];
    max: number;
}

export interface PlayerDetails {
    uuid: string;
    name: string;
    ip?: string;
    firstPlayed?: string;
    nameMcUrl?: string;
}

export interface CommandGroup {
    plugin: string;
    commands: Array<{
        name: string;
        aliases?: string[];
        description?: string;
        usage?: string;
        permission?: string;
    }>;
}

export interface PunishmentsPayload {
    player: { uuid: string; name: string };
    punishments: PunishmentSummary[];
    canViewIps: boolean;
}

export interface PunishmentSummary {
    punished: string;
    punisher?: string | null;
    source: 'PLAYER' | 'CONSOLE' | 'WEB';
    punisherReference?: string | null;
    punisherDisplayName: string;
    ip: string;
    type: string;
    reason: string;
    customTime: boolean;
    active: boolean;
    issueDate: string;
    endDate?: string | null;
}

export interface Schematic {
    name: string;
    size: number;
    formattedSize: string;
    downloadUrl: string;
}

export interface InventoryItem {
    type: string;
    name?: string;
    amount: number;
    enchants?: Record<string, number>;
    maxDamage?: number;
    damage?: number;
    lore?: string[];
    unbreakable?: boolean;
    flags?: string[];
    pdcKeys?: string[];
    pdcKeysTruncated?: boolean;
    nbt?: string;
    nbtTruncated?: boolean;
    nbtTruncatedChars?: number;
    nameTruncated?: boolean;
    nameTruncatedChars?: number;
}

export interface InventoryPayload {
    online: boolean;
    storage?: Array<InventoryItem | null>;
    hotbar?: Array<InventoryItem | null>;
    armor?: Record<string, InventoryItem | null>;
    offhand?: InventoryItem | null;
}
