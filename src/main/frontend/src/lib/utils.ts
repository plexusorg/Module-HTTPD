export function cn(...classes: Array<string | false | null | undefined>) {
    return classes.filter(Boolean).join(' ');
}

export function titleCase(value: string) {
    return value
        .replace(/[_-]+/g, " ")
        .replace(/\s+/g, " ")
        .trim()
        .replace(/\w\S*/g, (word) => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase());
}

export function lowerSearch(value: unknown): string {
    if (value == null) return "";
    if (typeof value === "string") return value.toLowerCase();
    if (typeof value === "number" || typeof value === "boolean") return String(value).toLowerCase();
    if (Array.isArray(value)) return value.map(lowerSearch).join(" ");
    if (typeof value === "object") return Object.values(value).map(lowerSearch).join(" ");
    return "";
}

export function formatBytes(value: number | null | undefined) {
    if (!Number.isFinite(value ?? NaN)) return "-";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let amount = Math.max(0, value as number);
    let unit = 0;
    while (amount >= 1024 && unit < units.length - 1) {
        amount /= 1024;
        unit += 1;
    }
    return `${amount >= 10 || unit === 0 ? amount.toFixed(0) : amount.toFixed(1)} ${units[unit]}`;
}

export function formatDuration(milliseconds: number) {
    if (!Number.isFinite(milliseconds) || milliseconds < 0) return "-";
    const seconds = Math.floor(milliseconds / 1000);
    const days = Math.floor(seconds / 86400);
    const hours = Math.floor((seconds % 86400) / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    if (days > 0) return `${days}d ${hours}h`;
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
}

export function pingClass(ping: number | null | undefined) {
    if (!Number.isFinite(ping ?? NaN)) return "text-faint";
    if ((ping as number) < 100) return "text-muted";
    if ((ping as number) < 250) return "text-warn";
    return "text-danger";
}

