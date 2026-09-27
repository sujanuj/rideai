"use client";

import Link from "next/link";
import { useState } from "react";
import AppShell from "@/components/AppShell";
import MapView from "@/components/MapView";
import TripSummary from "@/components/TripSummary";
import { Button, Card, ErrorText, LiveBadge, Stat } from "@/components/ui";
import { api, ApiError, errorMessage } from "@/lib/api";
import { DEFAULT_CENTER, distance, duration, money } from "@/lib/format";
import { saveSession } from "@/lib/session";
import { useSocket, useSubscription } from "@/lib/socket";
import type { Estimate, LatLng, Session, Trip, TripSocketMessage } from "@/lib/types";
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
  const [history, setHistory] = useState<Trip[]>([]);
  const [insightPing, setInsightPing] = useState(0);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [resumed, setResumed] = useState(false);

  const active = trip && !["COMPLETED", "CANCELLED"].includes(trip.status);
  const { client, connected } = useSocket(token);

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

  // On first load, resume an active trip if there is one (e.g. after a page refresh).
  usePolling(
    async () => {
      const current = await api<Trip | null>("/trips/current", { token });
      if (current) setTrip(current);
      setResumed(true);
    },
    60_000,
    !resumed,
  );

  // Live updates for the current trip: status changes, driver position, "summary ready".
  useSubscription<TripSocketMessage>(client, connected, trip ? `/topic/trips/${trip.id}` : null, (msg) => {
    if (msg.type === "trip") setTrip(msg.trip);
    else if (msg.type === "location")
      setTrip((t) => (t && t.driver ? { ...t, driver: { ...t.driver, position: { lat: msg.lat, lng: msg.lng } } } : t));
    else if (msg.type === "insight") setInsightPing((n) => n + 1);
  });

  // Safety net: poll the trip (fast if the socket is down, slow otherwise).
  usePolling(
    async () => {
      if (!trip) return;
      setTrip(await api<Trip>(`/trips/${trip.id}`, { token }));
    },
    connected ? 15_000 : 2_000,
    Boolean(active),
  );

  // Available cars around the pickup (or the map center) while booking.
  const around = pickup ?? DEFAULT_CENTER;
  usePolling(
    async () => {
      setCars(await api<LatLng[]>(`/drivers/nearby?lat=${around.lat}&lng=${around.lng}`, { token }));
    },
    4_000,
    !active,
  );

  // Recent trips (refreshes when a trip ends).
  usePolling(
    async () => {
      setHistory(await api<Trip[]>("/trips/me", { token }));
    },
    30_000,
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
    setInsightPing(0);
  }

  const showPickup = trip ? trip.pickup : pickup;
  const showDropoff = trip ? trip.dropoff : dropoff;
  const driverPos = active ? trip?.driver?.position ?? null : null;
  const fitTo = [showPickup, showDropoff, driverPos].filter((p): p is LatLng => Boolean(p));
  const pastTrips = history.filter((t) => t.id !== trip?.id).slice(0, 5);

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
              <div className="flex items-center justify-between text-xs uppercase tracking-wide text-zinc-500">
                <span>Trip #{trip.id}</span>
                {active && <LiveBadge connected={connected} />}
              </div>
              <h2 className="text-lg font-semibold">{STATUS_TEXT[trip.status]}</h2>
              {trip.status === "REQUESTED" && (
                <p className="text-sm text-zinc-500">We&apos;re offering your ride to the nearest drivers.</p>
              )}
              {trip.status === "ACCEPTED" && trip.pickupEtaSeconds != null && (
                <p className="text-sm text-zinc-500">Arriving in about {duration(trip.pickupEtaSeconds)}.</p>
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

        {trip?.status === "COMPLETED" && <TripSummary trip={trip} token={token} refreshKey={insightPing} />}

        {!active && pastTrips.length > 0 && (
          <Card className="flex flex-col gap-2">
            <h3 className="font-semibold">Recent trips</h3>
            <ul className="flex flex-col divide-y divide-zinc-200 text-sm dark:divide-zinc-800">
              {pastTrips.map((t) => (
                <li key={t.id}>
                  <Link href={`/trips/${t.id}`} className="flex items-center justify-between py-2 hover:opacity-80">
                    <span>
                      {new Date(t.requestedAt).toLocaleDateString(undefined, { month: "short", day: "numeric" })} ·{" "}
                      {distance(t.distanceMeters)}
                    </span>
                    <span className={t.status === "COMPLETED" ? "" : "text-zinc-500"}>
                      {t.status === "COMPLETED" ? money(t.fareCents) : t.status.toLowerCase()}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
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
