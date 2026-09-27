"use client";

import { useCallback, useEffect, useState } from "react";
import { summarizeHealth, type ServiceStatus } from "@/lib/health";

type Ping = { service: string; status: string; time: string };

// Phase 0 home page: proves the frontend can reach the backend,
// and the backend can reach Postgres and Redis.
export default function Home() {
  const [ping, setPing] = useState<Ping | null>(null);
  const [services, setServices] = useState<ServiceStatus[]>([]);
  const [loading, setLoading] = useState(true);

  const check = useCallback(async () => {
    setLoading(true);
    try {
      const [pingRes, healthRes] = await Promise.all([
        fetch("/api/ping", { cache: "no-store" }),
        fetch("/backend-health", { cache: "no-store" }),
      ]);
      setPing(pingRes.ok ? await pingRes.json() : null);
      // Actuator returns 503 with a JSON body when something is DOWN, so parse either way.
      setServices(summarizeHealth(await healthRes.json().catch(() => null)));
    } catch {
      setPing(null);
      setServices(summarizeHealth(null));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- initial fetch on mount
    void check();
  }, [check]);

  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col gap-8 px-4 py-16">
      <header className="flex flex-col gap-2">
        <h1 className="text-3xl font-semibold tracking-tight">RideAI</h1>
        <p className="text-zinc-600 dark:text-zinc-400">
          Phase 0 system check: frontend → backend → database and cache.
        </p>
      </header>

      <section className="rounded-xl border border-zinc-200 p-5 dark:border-zinc-800">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-medium">Services</h2>
          <button
            onClick={check}
            disabled={loading}
            className="rounded-lg border border-zinc-300 px-3 py-1 text-sm hover:bg-zinc-100 disabled:opacity-50 dark:border-zinc-700 dark:hover:bg-zinc-900"
          >
            {loading ? "Checking…" : "Recheck"}
          </button>
        </div>

        <ul className="flex flex-col gap-2">
          <StatusRow name="Backend API" up={ping?.status === "ok"} loading={loading} />
          {services
            .filter((s) => s.name !== "Backend")
            .map((s) => (
              <StatusRow key={s.name} name={s.name} up={s.up} loading={loading} />
            ))}
        </ul>

        {ping && (
          <p className="mt-4 text-xs text-zinc-500">Backend time: {ping.time}</p>
        )}
        {!loading && !ping && (
          <p className="mt-4 text-sm text-red-600">
            Can&apos;t reach the backend. Is it running on port 8080?
          </p>
        )}
      </section>

      <p className="text-sm text-zinc-500">
        Kafka topics are visible in Kafka UI at{" "}
        <a className="underline" href="http://localhost:8081" target="_blank" rel="noreferrer">
          localhost:8081
        </a>
        .
      </p>
    </main>
  );
}

function StatusRow({ name, up, loading }: { name: string; up: boolean; loading: boolean }) {
  return (
    <li className="flex items-center justify-between text-sm">
      <span>{name}</span>
      {loading ? (
        <span className="text-zinc-500">● Checking</span>
      ) : (
        <span className={up ? "text-green-600" : "text-red-600"}>{up ? "● Up" : "● Down"}</span>
      )}
    </li>
  );
}
