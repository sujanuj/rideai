export function money(cents: number | null | undefined): string {
  if (cents == null) return "—";
  return `$${(cents / 100).toFixed(2)}`;
}

export function distance(meters: number | null | undefined): string {
  if (meters == null) return "—";
  const miles = meters / 1609.34;
  return miles < 0.1 ? `${Math.round(meters * 3.281)} ft` : `${miles.toFixed(1)} mi`;
}

export function duration(seconds: number | null | undefined): string {
  if (seconds == null) return "—";
  const min = Math.max(1, Math.round(seconds / 60));
  return min < 60 ? `${min} min` : `${Math.floor(min / 60)} h ${min % 60} min`;
}

export function secondsUntil(iso: string | null, now: number): number {
  if (!iso) return 0;
  return Math.max(0, Math.ceil((new Date(iso).getTime() - now) / 1000));
}

/** Tempe, AZ (ASU campus): the default map center and the simulator's home. */
export const DEFAULT_CENTER = { lat: 33.4242, lng: -111.9281 };
