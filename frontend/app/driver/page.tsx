"use client";

import { useState } from "react";
import AppShell from "@/components/AppShell";
import MapView from "@/components/MapView";
import { Button, Card, ErrorText, LiveBadge, Stat } from "@/components/ui";
import { api, ApiError, errorMessage } from "@/lib/api";
import { DEFAULT_CENTER, distance, duration, money, secondsUntil } from "@/lib/format";
import { saveSession } from "@/lib/session";
import { useSocket, useSubscription } from "@/lib/socket";
import type { DriverMe, LatLng, Session, Trip, TripSocketMessage } from "@/lib/types";
import { usePolling } from "@/lib/usePolling";

export default function DriverPage() {
  return <AppShell role="DRIVER">{(session) => <DriverScreen session={session} />}</AppShell>;
}

// What the driver does next, per trip status.
const NEXT_STEP: Partial<Record<Trip["status"], { label: string; action: string }>> = {
  ACCEPTED: { label: "I've arrived at pickup", action: "arrive" },
  ARRIVED: { label: "Start trip", action: "start" },
  IN_PROGRESS: { label: "Complete trip", action: "complete" },
};

function DriverScreen({ session }: { session: Session }) {
  const token = session.token;
  const [me, setMe] = useState<DriverMe | null>(null);
  // Where the driver is. On a laptop you "drive" by clicking the map; or use real GPS.
  const [position, setPosition] = useState<LatLng | null>(null);
  const [offer, setOffer] = useState<Trip | null>(null);
  const [trip, setTrip] = useState<Trip | null>(null);
  const [lastTrip, setLastTrip] = useState<Trip | null>(null);
  const [now, setNow] = useState(() => Date.now());
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  const online = me?.status === "AVAILABLE" || me?.status === "ON_TRIP";
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

  // Driver profile + current trip (also restores state after a refresh). Slow when the socket is up.
  usePolling(
    async () => {
      const [profile, current] = await Promise.all([
        api<DriverMe>("/drivers/me", { token }),
        api<Trip | null>("/trips/current", { token }),
      ]);
      setMe(profile);
      setPosition((p) => p ?? profile.position);
      setTrip(current);
    },
    connected ? 10_000 : 3_000,
  );

  // Ride offers are pushed to this driver only: /user/queue/offers.
  useSubscription<Trip>(client, connected, "/user/queue/offers", (offered) => setOffer(offered));

  // Changes to the current trip (e.g. the rider cancels) arrive live too.
  useSubscription<TripSocketMessage>(client, connected, trip ? `/topic/trips/${trip.id}` : null, (msg) => {
    if (msg.type !== "trip") return;
    if (msg.trip.status === "CANCELLED") {
      setTrip(null);
      setError("The rider cancelled this trip.");
      setMe((m) => (m ? { ...m, status: "AVAILABLE" } : m));
    } else {
      setTrip(msg.trip);
    }
  });

  // Send our position every 3 s while online: over the socket when connected, else REST.
  usePolling(
    async () => {
      if (!position) return;
      if (client && connected) {
        client.publish({ destination: "/app/driver/location", body: JSON.stringify(position) });
      } else {
        await api("/drivers/me/location", { method: "PUT", token, body: position });
      }
    },
    3_000,
    online,
  );

  // Backup check for offers (in case a push was missed while reconnecting).
  usePolling(
    async () => {
      setOffer(await api<Trip | null>("/drivers/me/offer", { token }));
    },
    connected ? 8_000 : 2_000,
    me?.status === "AVAILABLE",
  );

  // Tick the offer countdown.
  usePolling(() => setNow(Date.now()), 500, Boolean(offer));

  async function goOnline() {
    if (!position) {
      setError("Click the map to set your location first.");
      return;
    }
    const updated = await call(() =>
      api<DriverMe>("/drivers/me/status", { method: "PUT", token, body: { status: "AVAILABLE", position } }),
    );
    if (updated) setMe(updated);
  }

  async function goOffline() {
    const updated = await call(() => api<DriverMe>("/drivers/me/status", { method: "PUT", token, body: { status: "OFFLINE" } }));
    if (updated) {
      setMe(updated);
      setOffer(null);
    }
  }

  async function accept() {
    if (!offer) return;
    const t = await call(() => api<Trip>(`/trips/${offer.id}/accept`, { method: "POST", token }));
    setOffer(null);
    if (t) {
      setTrip(t);
      setMe((m) => (m ? { ...m, status: "ON_TRIP" } : m));
    }
  }

  async function decline() {
    if (!offer) return;
    await call(() => api(`/trips/${offer.id}/decline`, { method: "POST", token }));
    setOffer(null);
  }

  async function advance(action: string) {
    if (!trip) return;
    const t = await call(() => api<Trip>(`/trips/${trip.id}/${action}`, { method: "POST", token }));
    if (!t) return;
    if (t.status === "COMPLETED") {
      setLastTrip(t);
      setTrip(null);
      setMe((m) => (m ? { ...m, status: "AVAILABLE" } : m));
    } else {
      setTrip(t);
    }
  }

  async function cancelTrip() {
    if (!trip) return;
    const t = await call(() => api<Trip>(`/trips/${trip.id}/cancel`, { method: "POST", token, body: {} }));
    if (t) {
      setTrip(null);
      setMe((m) => (m ? { ...m, status: "AVAILABLE" } : m));
    }
  }

  function locateWithGps() {
    navigator.geolocation?.getCurrentPosition(
      (p) => setPosition({ lat: p.coords.latitude, lng: p.coords.longitude }),
      () => setError("Couldn't read your location. Click the map instead."),
    );
  }

  const offerSeconds = secondsUntil(offer?.offerExpiresAt ?? null, now);
  const visibleOffer = offer && offerSeconds > 0 ? offer : null;
  const shown = trip ?? visibleOffer;
  const fitTo = [position, shown?.pickup, shown?.dropoff].filter((p): p is LatLng => Boolean(p));
  const step = trip ? NEXT_STEP[trip.status] : undefined;

  return (
    <div className="flex flex-1 flex-col md:flex-row">
      <aside className="order-2 flex w-full flex-col gap-4 p-4 md:order-1 md:w-96 md:overflow-y-auto">
        <Card className="flex flex-col gap-3">
          <div className="flex items-center justify-between">
            <div>
              <div className="text-xs uppercase tracking-wide text-zinc-500">{me ? `${me.vehicle} · ${me.plate}` : "…"}</div>
              <h2 className="text-lg font-semibold">
                {me?.status === "ON_TRIP" ? "On a trip" : online ? "Online, waiting for rides" : "You're offline"}
              </h2>
              {online && (
                <div className="text-xs">
                  <LiveBadge connected={connected} />
                </div>
              )}
            </div>
            <span className={`h-3 w-3 rounded-full ${online ? "bg-green-500" : "bg-zinc-400"}`} />
          </div>
          <p className="text-sm text-zinc-500">
            {position
              ? "Click the map to move your car."
              : "Click the map to set your location (or use GPS)."}
          </p>
          <div className="flex gap-2">
            {!online ? (
              <Button disabled={busy || !me} onClick={goOnline} className="flex-1">
                Go online
              </Button>
            ) : (
              <Button variant="secondary" disabled={busy || me?.status === "ON_TRIP"} onClick={goOffline} className="flex-1">
                Go offline
              </Button>
            )}
            <Button variant="secondary" onClick={locateWithGps}>
              Use GPS
            </Button>
          </div>
        </Card>

        {visibleOffer && !trip && (
          <Card className="flex flex-col gap-3 border-amber-400 dark:border-amber-600">
            <div className="flex items-center justify-between">
              <h2 className="text-lg font-semibold">New ride request</h2>
              <span className="rounded-full bg-amber-100 px-2 py-0.5 text-sm font-medium text-amber-800 dark:bg-amber-950 dark:text-amber-300">
                {offerSeconds}s
              </span>
            </div>
            <div className="grid grid-cols-3 gap-3">
              <Stat label="Fare" value={money(visibleOffer.fareCents)} />
              <Stat label="Trip" value={distance(visibleOffer.distanceMeters)} />
              <Stat label="Time" value={duration(visibleOffer.durationSeconds)} />
            </div>
            <p className="text-sm text-zinc-500">Rider: {visibleOffer.riderName}</p>
            <div className="flex gap-2">
              <Button disabled={busy} onClick={accept} className="flex-1">
                Accept
              </Button>
              <Button variant="secondary" disabled={busy} onClick={decline}>
                Decline
              </Button>
            </div>
          </Card>
        )}

        {trip && (
          <Card className="flex flex-col gap-3">
            <div className="text-xs uppercase tracking-wide text-zinc-500">Trip #{trip.id}</div>
            <h2 className="text-lg font-semibold">
              {trip.status === "ACCEPTED" && `Pick up ${trip.riderName}`}
              {trip.status === "ARRIVED" && `Waiting for ${trip.riderName}`}
              {trip.status === "IN_PROGRESS" && "Drive to the drop-off"}
            </h2>
            <div className="grid grid-cols-3 gap-3">
              <Stat label="Fare" value={money(trip.fareCents)} />
              <Stat label="Trip" value={distance(trip.distanceMeters)} />
              <Stat label="Time" value={duration(trip.durationSeconds)} />
            </div>
            {step && (
              <Button disabled={busy} onClick={() => advance(step.action)}>
                {step.label}
              </Button>
            )}
            {trip.status !== "IN_PROGRESS" && (
              <Button variant="danger" disabled={busy} onClick={cancelTrip}>
                Cancel trip
              </Button>
            )}
          </Card>
        )}

        {lastTrip && !trip && (
          <Card>
            <div className="text-sm text-zinc-500">Last trip #{lastTrip.id} completed</div>
            <div className="text-lg font-semibold">You earned {money(lastTrip.fareCents)}</div>
          </Card>
        )}

        <ErrorText>{error}</ErrorText>
      </aside>

      <section className="order-1 h-[55vh] w-full md:order-2 md:h-auto md:flex-1">
        <MapView
          center={DEFAULT_CENTER}
          pickup={shown?.pickup}
          dropoff={shown?.dropoff}
          vehicle={position}
          vehicleLabel="You"
          onClick={setPosition}
          fitTo={fitTo}
        />
      </section>
    </div>
  );
}
