"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Button, Card, ErrorText, Stat } from "@/components/ui";
import { api, ApiError, errorMessage } from "@/lib/api";
import { distance, duration, money } from "@/lib/format";
import type { Answer, Insight, Trip } from "@/lib/types";
import { usePolling } from "@/lib/usePolling";

const FLAG_LABELS: Record<string, string> = {
  SMOOTH_TRIP: "Smooth trip",
  SLOW_MATCH: "Slow to match",
  LATE_PICKUP: "Late pickup",
  LONG_WAIT: "Long wait",
  LONG_BOARDING: "Long boarding",
  SLOW_RIDE: "Slow ride",
  ROUTE_DEVIATION: "Route detour",
  DELAY_ALERT: "Delay alert",
};

/**
 * The AI Trip Assistant card: summary + suggestions for a completed trip, and a chat box.
 * `refreshKey` changes when the server pushes "insight ready" over WebSocket.
 */
export default function TripSummary({ trip, token, refreshKey = 0 }: { trip: Trip; token: string; refreshKey?: number }) {
  const [insight, setInsight] = useState<Insight | null>(null);
  const [gaveUp, setGaveUp] = useState(false);
  const attempts = useRef(0);

  // Poll until the summary exists (usually a second or two after completion).
  usePolling(
    async () => {
      try {
        setInsight(await api<Insight>(`/trips/${trip.id}/insight`, { token }));
      } catch (err) {
        if (!(err instanceof ApiError && err.code === "INSIGHT_PENDING")) throw err;
        attempts.current += 1;
        if (attempts.current >= 30) setGaveUp(true);
      }
    },
    2_000,
    !insight && !gaveUp,
  );

  // Fetch right away when the server pushes "insight ready" over the socket.
  useEffect(() => {
    if (refreshKey === 0) return;
    let cancelled = false;
    api<Insight>(`/trips/${trip.id}/insight`, { token })
      .then((i) => !cancelled && setInsight(i))
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [refreshKey, trip.id, token]);

  return (
    <Card className="flex flex-col gap-4">
      <div className="flex items-center justify-between">
        <h3 className="font-semibold">Trip assistant</h3>
        {insight && (
          <span className="rounded-full bg-violet-100 px-2 py-0.5 text-xs font-medium text-violet-800 dark:bg-violet-950 dark:text-violet-300">
            {insight.aiGenerated ? "AI · Claude" : "Rule-based"}
          </span>
        )}
      </div>

      {!insight && !gaveUp && (
        <p className="animate-pulse text-sm text-zinc-500">Analyzing your trip…</p>
      )}
      {!insight && gaveUp && <p className="text-sm text-zinc-500">The summary isn&apos;t available right now.</p>}

      {insight && (
        <>
          <p className="text-sm leading-relaxed">{insight.summary}</p>

          <div className="flex flex-wrap gap-1.5">
            {insight.flags.map((f) => (
              <span
                key={f}
                className={`rounded-full px-2 py-0.5 text-xs ${
                  f === "SMOOTH_TRIP"
                    ? "bg-green-100 text-green-800 dark:bg-green-950 dark:text-green-300"
                    : "bg-amber-100 text-amber-800 dark:bg-amber-950 dark:text-amber-300"
                }`}
              >
                {FLAG_LABELS[f] ?? f}
              </span>
            ))}
          </div>

          {insight.suggestions.length > 0 && (
            <ul className="flex flex-col gap-2">
              {insight.suggestions.map((s) => (
                <li key={s.title} className="rounded-lg bg-zinc-100 p-3 text-sm dark:bg-zinc-900">
                  <div className="font-medium">{s.title}</div>
                  <div className="text-zinc-600 dark:text-zinc-400">{s.detail}</div>
                </li>
              ))}
            </ul>
          )}

          <div className="grid grid-cols-3 gap-3 border-t border-zinc-200 pt-3 dark:border-zinc-800">
            <Stat label="Driver took" value={insight.metrics.pickupWaitMinutes != null ? duration(insight.metrics.pickupWaitMinutes * 60) : "—"} />
            <Stat label="Ride" value={insight.metrics.actualRideMinutes != null ? duration(insight.metrics.actualRideMinutes * 60) : "—"} />
            <Stat
              label="Driven"
              value={trip.actualDistanceMeters != null ? distance(trip.actualDistanceMeters) : distance(trip.distanceMeters)}
            />
          </div>

          <Chat tripId={trip.id} token={token} />
        </>
      )}
      <p className="text-xs text-zinc-500">Paid {money(trip.fareCents)}</p>
    </Card>
  );
}

function Chat({ tripId, token }: { tripId: number; token: string }) {
  const [question, setQuestion] = useState("");
  const [log, setLog] = useState<{ q: string; a: string }[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function ask(e: FormEvent) {
    e.preventDefault();
    const q = question.trim();
    if (!q) return;
    setBusy(true);
    setError("");
    try {
      const res = await api<Answer>(`/trips/${tripId}/assistant`, { method: "POST", token, body: { question: q } });
      setLog((l) => [...l, { q, a: res.answer }]);
      setQuestion("");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex flex-col gap-2 border-t border-zinc-200 pt-3 dark:border-zinc-800">
      {log.map((m, i) => (
        <div key={i} className="flex flex-col gap-1 text-sm">
          <div className="self-end rounded-lg bg-zinc-900 px-3 py-1.5 text-white dark:bg-white dark:text-zinc-900">{m.q}</div>
          <div className="rounded-lg bg-zinc-100 px-3 py-1.5 dark:bg-zinc-900">{m.a}</div>
        </div>
      ))}
      <form onSubmit={ask} className="flex gap-2">
        <input
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          maxLength={500}
          placeholder="Ask about this trip, e.g. why was pickup slow?"
          className="min-w-0 flex-1 rounded-lg border border-zinc-300 bg-transparent px-3 py-2 text-sm outline-none focus:border-zinc-900 dark:border-zinc-700 dark:focus:border-zinc-300"
        />
        <Button type="submit" disabled={busy || !question.trim()}>
          Ask
        </Button>
      </form>
      <ErrorText>{error}</ErrorText>
    </div>
  );
}
