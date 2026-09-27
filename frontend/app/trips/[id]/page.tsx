"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useState } from "react";
import AppShell from "@/components/AppShell";
import MapView from "@/components/MapView";
import TripSummary from "@/components/TripSummary";
import { Card, ErrorText, Stat } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { distance, duration, money } from "@/lib/format";
import type { Session, Trip } from "@/lib/types";
import { usePolling } from "@/lib/usePolling";

/** A past trip: details, and the AI Trip Assistant's summary + chat. */
export default function TripPage() {
  return <AppShell role="RIDER">{(session) => <TripDetail session={session} />}</AppShell>;
}

function TripDetail({ session }: { session: Session }) {
  const params = useParams<{ id: string }>();
  const [trip, setTrip] = useState<Trip | null>(null);
  const [error, setError] = useState("");

  usePolling(
    async () => {
      try {
        setTrip(await api<Trip>(`/trips/${params.id}`, { token: session.token }));
      } catch (err) {
        setError(errorMessage(err));
      }
    },
    60_000,
    !trip && !error,
  );

  return (
    <div className="flex flex-1 flex-col md:flex-row">
      <aside className="order-2 flex w-full flex-col gap-4 p-4 md:order-1 md:w-96 md:overflow-y-auto">
        <Link href="/rider" className="text-sm text-zinc-500 hover:underline">
          ← Back
        </Link>
        <ErrorText>{error}</ErrorText>
        {trip && (
          <>
            <Card className="flex flex-col gap-3">
              <div className="text-xs uppercase tracking-wide text-zinc-500">
                Trip #{trip.id} · {new Date(trip.requestedAt).toLocaleString()}
              </div>
              <h2 className="text-lg font-semibold">
                {trip.status === "COMPLETED" ? "Completed" : trip.status === "CANCELLED" ? "Cancelled" : "In progress"}
              </h2>
              {trip.driver && (
                <p className="text-sm text-zinc-600 dark:text-zinc-400">
                  {trip.driver.name} · {trip.driver.vehicle} · {trip.driver.plate}
                </p>
              )}
              <div className="grid grid-cols-3 gap-3">
                <Stat label="Fare" value={money(trip.fareCents)} />
                <Stat label="Distance" value={distance(trip.distanceMeters)} />
                <Stat label="Est. time" value={duration(trip.durationSeconds)} />
              </div>
            </Card>
            {trip.status === "COMPLETED" && <TripSummary trip={trip} token={session.token} />}
          </>
        )}
      </aside>
      <section className="order-1 h-[45vh] w-full md:order-2 md:h-auto md:flex-1">
        {trip && (
          <MapView center={trip.pickup} pickup={trip.pickup} dropoff={trip.dropoff} fitTo={[trip.pickup, trip.dropoff]} />
        )}
      </section>
    </div>
  );
}
