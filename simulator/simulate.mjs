#!/usr/bin/env node
// RideAI driver simulator: fake drivers that drive around, accept rides and complete them,
// so the map, matching and (later) Kafka all have real traffic to show.
//
//   node simulator/simulate.mjs                 # 15 drivers around ASU Tempe
//   DRIVERS=40 SPEED=25 node simulator/simulate.mjs
//
// Needs Node 18+ (built-in fetch). No npm install required.

const API = process.env.API ?? "http://localhost:8080/api";
const DRIVERS = Number(process.env.DRIVERS ?? 15);
const [CENTER_LAT, CENTER_LNG] = (process.env.CENTER ?? "33.4242,-111.9281").split(",").map(Number);
const RADIUS_M = Number(process.env.RADIUS_KM ?? 4) * 1000;
const SPEED = Number(process.env.SPEED ?? 20); // metres per second (20 m/s ≈ 45 mph, sped up for demos)
const TICK_MS = 3000;
const ACCEPT_RATE = Number(process.env.ACCEPT_RATE ?? 0.85);
const OSRM = process.env.OSRM_URL ?? "https://router.project-osrm.org";
const PASSWORD = "simulator-pass";

const CARS = ["Toyota Prius", "Honda Civic", "Tesla Model 3", "Hyundai Ioniq", "Kia Niro", "Ford Escape", "Chevy Bolt"];
const NAMES = ["Alex", "Priya", "Marco", "Aisha", "Wei", "Sofia", "Jamal", "Elena", "Ravi", "Hana", "Diego", "Nora"];

// ---------- geometry ----------

const toRad = (d) => (d * Math.PI) / 180;
const toDeg = (r) => (r * 180) / Math.PI;

function distanceM(a, b) {
  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * 6371000 * Math.asin(Math.sqrt(h));
}

function offset(p, meters, bearingDeg) {
  const d = meters / 6371000;
  const b = toRad(bearingDeg);
  const lat1 = toRad(p.lat);
  const lng1 = toRad(p.lng);
  const lat2 = Math.asin(Math.sin(lat1) * Math.cos(d) + Math.cos(lat1) * Math.sin(d) * Math.cos(b));
  const lng2 = lng1 + Math.atan2(Math.sin(b) * Math.sin(d) * Math.cos(lat1), Math.cos(d) - Math.sin(lat1) * Math.sin(lat2));
  return { lat: toDeg(lat2), lng: toDeg(lng2) };
}

function bearing(a, b) {
  const y = Math.sin(toRad(b.lng - a.lng)) * Math.cos(toRad(b.lat));
  const x =
    Math.cos(toRad(a.lat)) * Math.sin(toRad(b.lat)) -
    Math.sin(toRad(a.lat)) * Math.cos(toRad(b.lat)) * Math.cos(toRad(b.lng - a.lng));
  return (toDeg(Math.atan2(y, x)) + 360) % 360;
}

const CENTER = { lat: CENTER_LAT, lng: CENTER_LNG };
const randomPoint = () => offset(CENTER, Math.sqrt(Math.random()) * RADIUS_M, Math.random() * 360);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

/** Road route from OSRM, or a straight line if it's unreachable. */
async function routeBetween(from, to) {
  try {
    const url = `${OSRM}/route/v1/driving/${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson`;
    const res = await fetch(url, { signal: AbortSignal.timeout(4000) });
    const body = await res.json();
    if (body.code === "Ok") return body.routes[0].geometry.coordinates.map(([lng, lat]) => ({ lat, lng }));
  } catch {
    // fall through
  }
  return [from, to];
}

/** Advance `meters` along a polyline; returns the new position and the remaining path. */
function advance(position, path, meters) {
  let pos = position;
  let left = meters;
  const rest = [...path];
  while (rest.length && left > 0) {
    const next = rest[0];
    const d = distanceM(pos, next);
    if (d <= left) {
      pos = next;
      left -= d;
      rest.shift();
    } else {
      pos = offset(pos, left, bearing(pos, next));
      left = 0;
    }
  }
  return { pos, rest };
}

// ---------- API ----------

async function call(driver, path, method = "GET", body) {
  const res = await fetch(`${API}${path}`, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(driver?.token ? { Authorization: `Bearer ${driver.token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (res.status === 204) return null;
  const data = await res.json().catch(() => null);
  if (!res.ok) {
    const err = new Error(`${method} ${path} → ${res.status} ${data?.code ?? ""} ${data?.detail ?? ""}`.trim());
    err.status = res.status;
    throw err;
  }
  return data;
}

async function signIn(i) {
  const email = `sim-driver-${i}@rideai.dev`;
  try {
    const { token, user } = await call(null, "/auth/login", "POST", { email, password: PASSWORD });
    return { token, id: user.id, name: user.fullName };
  } catch (e) {
    if (e.status !== 401) throw e;
    const plate = `SIM${String(i).padStart(3, "0")}`;
    const { token, user } = await call(null, "/auth/register", "POST", {
      email,
      password: PASSWORD,
      fullName: `${pick(NAMES)} (sim ${i})`,
      role: "DRIVER",
      vehicle: pick(CARS),
      plate,
    });
    return { token, id: user.id, name: user.fullName };
  }
}

// ---------- one driver ----------

class SimDriver {
  constructor(i, auth) {
    Object.assign(this, auth);
    this.i = i;
    this.pos = randomPoint();
    this.heading = Math.random() * 360;
    this.state = "idle"; // idle | deciding | toPickup | waiting | toDropoff
    this.trip = null;
    this.path = [];
  }

  log(msg) {
    console.log(`[driver ${String(this.i).padStart(2)}] ${msg}`);
  }

  async start() {
    // Resume a trip left over from a previous run, otherwise go online.
    const current = await call(this, "/trips/current");
    if (current) {
      this.trip = current;
      this.state = current.status === "IN_PROGRESS" ? "toDropoff" : "toPickup";
      this.path = await routeBetween(this.pos, this.state === "toDropoff" ? current.dropoff : current.pickup);
      this.log(`resuming trip #${current.id}`);
    }
    if (!current) await call(this, "/drivers/me/status", "PUT", { status: "AVAILABLE", position: this.pos });
  }

  async tick() {
    const step = (SPEED * TICK_MS) / 1000;

    if (this.state === "idle") {
      // Wander: keep a heading, turn a little, head back toward the center if too far out.
      this.heading += (Math.random() - 0.5) * 60;
      if (distanceM(this.pos, CENTER) > RADIUS_M) this.heading = bearing(this.pos, CENTER);
      this.pos = offset(this.pos, step * 0.5, this.heading);
      await call(this, "/drivers/me/location", "PUT", this.pos);

      const offer = await call(this, "/drivers/me/offer");
      if (offer) this.decide(offer);
      return;
    }

    if (this.state === "deciding" || this.state === "waiting") {
      await call(this, "/drivers/me/location", "PUT", this.pos);
      return;
    }

    // Driving to pickup or drop-off. Check the rider didn't cancel.
    const trip = await call(this, `/trips/${this.trip.id}`);
    if (trip.status === "CANCELLED") {
      this.log(`trip #${trip.id} was cancelled (${trip.cancelReason})`);
      this.reset();
      return;
    }

    const moved = advance(this.pos, this.path, step);
    this.pos = moved.pos;
    this.path = moved.rest;
    await call(this, "/drivers/me/location", "PUT", this.pos);

    if (this.path.length === 0) {
      if (this.state === "toPickup") {
        await call(this, `/trips/${trip.id}/arrive`, "POST");
        this.log(`arrived at pickup for trip #${trip.id}`);
        this.state = "waiting";
        setTimeout(async () => {
          // the rider walks to the car, then off we go
          try {
            await call(this, `/trips/${trip.id}/start`, "POST");
            this.path = await routeBetween(this.pos, trip.dropoff);
            this.state = "toDropoff";
            this.log(`started trip #${trip.id}`);
          } catch (e) {
            this.log(`couldn't start trip #${trip.id} (${e.message})`);
            this.reset();
          }
        }, 4000);
      } else {
        await call(this, `/trips/${trip.id}/complete`, "POST");
        this.log(`completed trip #${trip.id} · earned $${(trip.fareCents / 100).toFixed(2)}`);
        this.reset();
      }
    }
  }

  decide(offer) {
    this.state = "deciding";
    const thinkMs = 1500 + Math.random() * 3000;
    this.log(`offered trip #${offer.id} ($${(offer.fareCents / 100).toFixed(2)})`);
    setTimeout(async () => {
      try {
        if (Math.random() < ACCEPT_RATE) {
          this.trip = await call(this, `/trips/${offer.id}/accept`, "POST");
          this.path = await routeBetween(this.pos, this.trip.pickup);
          this.state = "toPickup";
          this.log(`accepted trip #${offer.id}, driving to pickup`);
        } else {
          await call(this, `/trips/${offer.id}/decline`, "POST");
          this.log(`declined trip #${offer.id}`);
          this.state = "idle";
        }
      } catch (e) {
        this.log(`offer #${offer.id} no longer available (${e.message})`);
        this.state = "idle";
      }
    }, thinkMs);
  }

  reset() {
    this.trip = null;
    this.path = [];
    this.state = "idle";
  }
}

// ---------- main ----------

/** Wait for the backend (useful under docker compose, where it starts after us). */
async function waitForApi() {
  for (let attempt = 1; attempt <= 60; attempt++) {
    try {
      const res = await fetch(`${API}/ping`, { signal: AbortSignal.timeout(2000) });
      if (res.ok) return;
    } catch {
      // not up yet
    }
    if (attempt === 1) console.log(`Waiting for the backend at ${API} …`);
    await sleep(2000);
  }
  console.error(`Backend not reachable at ${API}. Is it running?`);
  process.exit(1);
}

async function main() {
  await waitForApi();
  console.log(`Starting ${DRIVERS} simulated drivers around ${CENTER.lat}, ${CENTER.lng} → ${API}`);
  const drivers = [];
  for (let i = 1; i <= DRIVERS; i++) {
    try {
      const d = new SimDriver(i, await signIn(i));
      await d.start();
      drivers.push(d);
    } catch (e) {
      console.error(`Could not start driver ${i}: ${e.message}`);
      if (i === 1) {
        console.error("Is the backend running on", API, "?");
        process.exit(1);
      }
    }
  }
  console.log(`${drivers.length} drivers online. Open the rider app and request a ride. Ctrl+C to stop.`);

  let stopping = false;
  process.on("SIGINT", async () => {
    if (stopping) process.exit(0);
    stopping = true;
    console.log("\nTaking drivers offline…");
    await Promise.allSettled(
      drivers.filter((d) => d.state === "idle").map((d) => call(d, "/drivers/me/status", "PUT", { status: "OFFLINE" })),
    );
    process.exit(0);
  });

  while (!stopping) {
    const started = Date.now();
    await Promise.allSettled(
      drivers.map(async (d) => {
        try {
          await d.tick();
        } catch (e) {
          if (e.status === 401) {
            Object.assign(d, await signIn(d.i)); // token expired
          } else if (e.message.includes("OFFLINE") && d.state === "idle") {
            await call(d, "/drivers/me/status", "PUT", { status: "AVAILABLE", position: d.pos }); // back online
          } else {
            d.log(e.message);
          }
        }
      }),
    );
    await sleep(Math.max(0, TICK_MS - (Date.now() - started)));
  }
}

main();
