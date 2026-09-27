"use client";

import { useState } from "react";
import AppShell from "@/components/AppShell";
import MapView from "@/components/MapView";
import { Button, Card, ErrorText, Stat } from "@/components/ui";
import { api, ApiError, errorMessage } from "@/lib/api";
import { DEFAULT_CENTER, distance, duration, money } from "@/lib/format";
import { saveSession } from "@/lib/session";
import type { Estimate, LatLng, Session, Trip } from "@/lib/types";
import { usePolling } from "@/lib/usePolling";

export default function RiderPage() {
  return <AppShell role="RIDER">{(session) => <RiderScreen session={session} />}</AppShell>;
}

const STATUS_TEXT: Record<Trip["status"], string> = {
  REQUESTED: "Finding you a driver…",
  ACCEPTED: "Your driver is on the way",
  ARRIVED: "Your driver has arrived",
  IN_PROGRESS: "On your way",
  COMPLETED: "You've arrived",
  CANCELLED: "Trip cancelled",
};

function RiderScreen({ session }: { session: Session }) {
  const token = session.token;
  const [pickup, setPickup] = useState<LatLng | null>(null);
  const [dropoff, setDropoff] = useState<LatLng | null>(null);
  const [estimate, setEstimate] = useState<Estimate | null>(null);
  const [trip, setTrip] = useState<Trip | null>(null);
  const [cars, setCars] = useState<LatLng[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [resumed, setResumed] = useState(false);

  const active = trip && !["COMPLETED", "CANCELLED"].includes(trip.status);

  async function call<T>(fn: () => Promise<T>): Promise<T | undefined> {
    setBusy(true);
    setError("");
    try {
      return await fn();
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) saveSession(null);
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  // On first load, pick up an active trip if there is one (e.g. after a page refresh).
  usePolling(
    async () => {
      const current = await api<Trip | null>("/trips/current", { token });
      if (current) setTrip(current);
      setResumed(true);
    },
    60_000,
    !resumed,
  );

  // Follow the active trip.
  usePolling(
    async () => {
      if (!trip) return;
      setTrip(await api<Trip>(`/trips/${trip.id}`, { token }));
    },
    2_000,
    Boolean(active),
  );

  // Show available cars around the pickup (or the map center) while booking.
  const around = pickup ?? DEFAULT_CENTER;
  usePolling(
    async () => {
      setCars(await api<LatLng[]>(`/drivers/nearby?lat=${around.lat}&lng=${around.lng}`, { token }));
    },
    4_000,
    !active,
  );

  function onMapClick(p: LatLng) {
    if (active) return;
    setEstimate(null);
    if (!pickup || (pickup && dropoff)) {
      setPickup(p);
      setDropoff(null);
    } else {
      setDropoff(p);
    }
  }

  async function getEstimate() {
    const e = await call(() => api<Estimate>("/trips/estimate", { method: "POST", token, body: { pickup, dropoff } }));
    if (e) setEstimate(e);
  }

  async function requestRide() {
    const t = await call(() => api<Trip>("/trips", { method: "POST", token, body: { pickup, dropoff } }));
    if (t) setTrip(t);
  }

  async function cancelRide() {
    if (!trip) return;
    const t = await call(() => api<Trip>(`/trips/${trip.id}/cancel`, { method: "POST", token, body: {} }));
    if (t) setTrip(t);
  }

  function startOver() {
    setTrip(null);
    setPickup(null);
    setDropoff(null);
    setEstimate(null);
  }

  const showPickup = trip ? trip.pickup : pickup;
  const showDropoff = trip ? trip.dropoff : dropoff;
  const driverPos = active ? trip?.driver?.position ?? null : null;
  const fitTo = [showPickup, showDropoff, driverPos].filter((p): p is LatLng => Boolean(p));

  return (
    <div className="flex flex-1 flex-col md:flex-row">
      <aside className="order-2 flex w-full flex-col gap-4 p-4 md:order-1 md:w-96 md:overflow-y-auto">
        {!trip && (
          <Card className="flex flex-col gap-4">
            <div>
              <h2 className="text-lg font-semibold">Where to?</h2>
              <p className="text-sm text-zinc-500">
                {!pickup
                  ? "Click the map to set your pickup."
                  : !dropoff
                    ? "Now click where you're going."
                    : "Click again to start over."}
              </p>
            </div>

            {estimate && (
              <div className="grid grid-cols-3 gap-3">
                <Stat label="Price" value={money(estimate.fareCents)} />
                <Stat label="Distance" value={distance(estimate.distanceMeters)} />
                <Stat label="Time" value={duration(estimate.durationSeconds)} />
              </div>
            )}
            {estimate?.routeSource === "estimate" && (
              <p className="text-xs text-zinc-500">Road routing unavailable, showing a straight-line estimate.</p>
            )}

            <ErrorText>{error}</ErrorText>

            {!estimate ? (
              <Button disabled={!pickup || !dropoff || busy} onClick={getEstimate}>
                See price
              </Button>
            ) : (
              <Button disabled={busy} onClick={requestRide}>
                Request ride · {money(estimate.fareCents)}
              </Button>
            )}
            <p className="text-xs text-zinc-500">
              {cars.length} {cars.length === 1 ? "car" : "cars"} available nearby
            </p>
          </Card>
        )}

        {trip && (
          <Card className="flex flex-col gap-4">
            <div>
              <div className="text-xs uppercase tracking-wide text-zinc-500">Trip #{trip.id}</div>
              <h2 className="text-lg font-semibold">{STATUS_TEXT[trip.status]}</h2>
              {trip.status === "REQUESTED" && (
                <p className="text-sm text-zinc-500">We&apos;re offering your ride to the nearest drivers.</p>
              )}
              {trip.status === "CANCELLED" && trip.cancelReason && (
                <p className="text-sm text-zinc-500">{humanReason(trip.cancelReason)}</p>
              )}
            </div>

            {trip.driver && (
              <div className="rounded-lg bg-zinc-100 p-3 text-sm dark:bg-zinc-900">
                <div className="font-medium">{trip.driver.name}</div>
                <div className="text-zinc-600 dark:text-zinc-400">
                  {trip.driver.vehicle} · <span className="font-mono">{trip.driver.plate}</span> · ★ {trip.driver.rating}
                </div>
              </div>
            )}

            <div className="grid grid-cols-3 gap-3">
              <Stat label={trip.status === "COMPLETED" ? "Paid" : "Price"} value={money(trip.fareCents)} />
              <Stat label="Distance" value={distance(trip.distanceMeters)} />
              <Stat label="Time" value={duration(trip.durationSeconds)} />
            </div>

            <ErrorText>{error}</ErrorText>

            {active && ["REQUESTED", "ACCEPTED", "ARRIVED"].includes(trip.status) && (
              <Button variant="danger" disabled={busy} onClick={cancelRide}>
                Cancel ride
              </Button>
            )}
            {!active && <Button onClick={startOver}>Book another ride</Button>}
          </Card>
        )}
      </aside>

      <section className="order-1 h-[55vh] w-full md:order-2 md:h-auto md:flex-1">
        <MapView
          center={DEFAULT_CENTER}
          pickup={showPickup}
          dropoff={showDropoff}
          route={!trip ? estimate?.route : undefined}
          cars={active ? [] : cars}
          vehicle={driverPos}
          vehicleLabel={trip?.driver?.name ?? "Driver"}
          onClick={active ? undefined : onMapClick}
          fitTo={fitTo}
        />
      </section>
    </div>
  );
}

function humanReason(reason: string): string {
  switch (reason) {
    case "NO_DRIVERS_AVAILABLE":
      return "No drivers were available. Please try again in a moment.";
    case "CANCELLED_BY_RIDER":
      return "You cancelled this ride.";
    case "CANCELLED_BY_DRIVER":
      return "Your driver cancelled. Please request again.";
    default:
      return reason;
  }
}
