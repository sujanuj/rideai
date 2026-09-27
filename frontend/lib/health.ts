// Turns Spring Boot's /actuator/health JSON into a simple list for the UI.

export type ServiceStatus = {
  name: string;
  up: boolean;
};

type HealthResponse = {
  status?: string;
  components?: Record<string, { status?: string }>;
};

// Friendly names for the Spring health components we care about.
const LABELS: Record<string, string> = {
  db: "PostgreSQL",
  redis: "Redis",
  diskSpace: "Disk space",
  ping: "Backend",
};

export function summarizeHealth(health: HealthResponse | null): ServiceStatus[] {
  if (!health?.components) {
    return [{ name: "Backend", up: false }];
  }
  return Object.entries(health.components)
    .filter(([key]) => key in LABELS)
    .map(([key, value]) => ({ name: LABELS[key], up: value.status === "UP" }))
    .sort((a, b) => a.name.localeCompare(b.name));
}
