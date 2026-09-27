// k6 load test: many drivers streaming GPS updates while riders look for nearby cars.
//
//   brew install k6
//   k6 run loadtest/driver-locations.js                       # 50 drivers, 1 minute
//   DRIVERS=200 DURATION=2m k6 run loadtest/driver-locations.js
//   PACE=0 DRIVERS=100 k6 run loadtest/driver-locations.js    # no pause: find the maximum throughput
//
// For your resume, read two lines from the summary:
//   location_updates ....: 12345  205.7/s   ← [N] updates per second
//   location_update_ms ..: ... p(95)=38.2ms ← [X] p95 latency

import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";

const API = __ENV.API || "http://localhost:8080/api";
const DRIVERS = Number(__ENV.DRIVERS || 50);
const DURATION = __ENV.DURATION || "1m";
const PACE = Number(__ENV.PACE ?? 1); // seconds each driver waits between updates (real app: 3)
const CENTER = { lat: 33.4242, lng: -111.9281 };

const locationLatency = new Trend("location_update_ms", true);
const nearbyLatency = new Trend("nearby_search_ms", true);
const locationUpdates = new Counter("location_updates");

export const options = {
  scenarios: {
    drivers: {
      executor: "constant-vus",
      vus: DRIVERS,
      duration: DURATION,
      exec: "driver",
    },
    riders: {
      executor: "constant-arrival-rate",
      rate: 20, // nearby searches per second
      timeUnit: "1s",
      duration: DURATION,
      preAllocatedVUs: 10,
      exec: "rider",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    location_update_ms: ["p(95)<200"],
    nearby_search_ms: ["p(95)<200"],
  },
};

const json = { headers: { "Content-Type": "application/json" } };
const auth = (token) => ({ headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` } });

function login(email, extra) {
  let res = http.post(`${API}/auth/login`, JSON.stringify({ email, password: "loadtest-pass" }), json);
  if (res.status === 401) {
    res = http.post(
      `${API}/auth/register`,
      JSON.stringify({ email, password: "loadtest-pass", fullName: "Load Test", ...extra }),
      json,
    );
  }
  return res.json("token");
}

function jitter(meters) {
  const d = meters / 111_000;
  return { lat: CENTER.lat + (Math.random() - 0.5) * 2 * d, lng: CENTER.lng + (Math.random() - 0.5) * 2 * d };
}

// Runs once: create/log in all drivers and put them online.
export function setup() {
  const drivers = [];
  for (let i = 1; i <= DRIVERS; i++) {
    const token = login(`load-driver-${i}@rideai.dev`, { role: "DRIVER", vehicle: "Load Car", plate: `LT${i}` });
    http.put(`${API}/drivers/me/status`, JSON.stringify({ status: "AVAILABLE", position: jitter(4000) }), auth(token));
    drivers.push(token);
  }
  const rider = login("load-rider@rideai.dev", { role: "RIDER" });
  return { drivers, rider };
}

export function driver(data) {
  const token = data.drivers[(__VU - 1) % data.drivers.length];
  const res = http.put(`${API}/drivers/me/location`, JSON.stringify(jitter(4000)), auth(token));
  locationLatency.add(res.timings.duration);
  if (check(res, { "location accepted": (r) => r.status === 204 })) locationUpdates.add(1);
  if (PACE > 0) sleep(PACE);
}

export function rider(data) {
  const p = jitter(3000);
  const res = http.get(`${API}/drivers/nearby?lat=${p.lat}&lng=${p.lng}`, auth(data.rider));
  nearbyLatency.add(res.timings.duration);
  check(res, { "nearby ok": (r) => r.status === 200 });
}

// Runs once at the end: take the test drivers offline so they don't get real ride offers.
export function teardown(data) {
  for (const token of data.drivers) {
    http.put(`${API}/drivers/me/status`, JSON.stringify({ status: "OFFLINE" }), auth(token));
  }
}
