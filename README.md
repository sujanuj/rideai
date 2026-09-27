# RideAI

A real-time ride-hailing platform with an **AI Trip Assistant** that analyzes each completed trip and suggests how the next one could go better.

**Stack:** Next.js · Spring Boot 3 (Java 21) · PostgreSQL + PostGIS · Redis (GEO) · Kafka · WebSocket (STOMP) · Claude API · Docker · GitHub Actions · Sentry

> Status: **Phases 1–2 done.** Riders and drivers sign up, riders book on a map with a live price, and the nearest available driver (Redis GEO search) gets the offer. A simulator drives fake cars around Tempe, AZ.

## Architecture

```
Next.js ──REST/WebSocket──▶ Spring Boot API ──▶ PostgreSQL + PostGIS  (users, trips, routes)
                                 │          ──▶ Redis                 (live driver positions)
                                 │          ──▶ Claude API            (trip insights)
                                 └──publish/consume──▶ Kafka          (trip-events, driver-locations)
```

## Prerequisites

- Java 21 and Maven (`brew install openjdk@21 maven`)
- Node.js 22 (`brew install node@22`)
- Docker Desktop (running)

Check with: `java -version`, `mvn -v`, `node -v`, `docker info`.

## Run it locally

```bash
# 1. Start Postgres, Redis, Kafka and Kafka UI
docker compose up -d

# 2. Start the backend (new terminal)
cd backend
mvn spring-boot:run
#   → http://localhost:8080/actuator/health  should show "UP"

# 3. Start the frontend (new terminal)
cd frontend
npm install
npm run dev
#   → http://localhost:3000
```

### Try a ride

```bash
# 4. Put 15 fake drivers on the road (new terminal, from the repo root)
node simulator/simulate.mjs
```

1. Open http://localhost:3000 → **Ride with RideAI** → create a rider account.
2. Click the map for your pickup, click again for the drop-off, then **See price** → **Request ride**.
3. Watch the nearest simulated driver accept, drive to you, and complete the trip.

To play the driver yourself, open a private window, create a driver account, click the map to place your car and **Go online**.

Simulator options: `DRIVERS=40 SPEED=25 ACCEPT_RATE=0.7 CENTER=33.4484,-112.0740 node simulator/simulate.mjs`.

Kafka UI: http://localhost:8081. You should see the `trip-events` and `driver-locations` topics the backend created on startup.

### Run the whole system in Docker (demo mode)

```bash
docker compose -f docker-compose.yml -f docker-compose.full.yml up --build
```

## Tests

```bash
cd backend && mvn verify     # starts real Postgres/Redis/Kafka in Docker via Testcontainers
cd frontend && npm test      # Vitest
```

CI (`.github/workflows/ci.yml`) runs both on every push and pull request, then builds Docker images and pushes them to GitHub Container Registry on `main`.

## How matching works

1. Online drivers send their position every 3 s. Free drivers are indexed in a Redis GEO set (`drivers:available`).
2. A ride request runs `GEOSEARCH` for drivers within 5 km of the pickup, nearest first.
3. The trip is offered to one driver at a time for 15 s. Declined or expired offers move on to the next driver.
4. Accepting is a single conditional `UPDATE … WHERE status = 'REQUESTED' AND offered_driver_id = ?`, so two accepts can never both win.
5. No driver within 90 s → the trip is cancelled with `NO_DRIVERS_AVAILABLE`.

Trips follow a state machine (`TripStatus.java`): `REQUESTED → ACCEPTED → ARRIVED → IN_PROGRESS → COMPLETED`, with `CANCELLED` allowed before the ride starts. Every step is written to `trip_events`, which the AI Trip Assistant reads in Phase 5.

## API

All endpoints except register/login need `Authorization: Bearer <token>`.

| Method | Path | Who | What |
| --- | --- | --- | --- |
| POST | `/api/auth/register`, `/api/auth/login` | anyone | Returns a JWT |
| GET | `/api/auth/me` | any | Current user |
| POST | `/api/trips/estimate` | rider | Price, distance, time and route line |
| POST | `/api/trips` | rider | Request a ride |
| GET | `/api/trips/current`, `/api/trips/me` | any | Active trip (204 if none), history |
| GET | `/api/trips/{id}`, `/api/trips/{id}/timeline` | rider, driver | Trip details, event timeline |
| POST | `/api/trips/{id}/accept`, `/decline` | driver | Respond to an offer |
| POST | `/api/trips/{id}/arrive`, `/start`, `/complete` | driver | Move the trip forward |
| POST | `/api/trips/{id}/cancel` | rider, driver | Cancel before the ride starts |
| PUT | `/api/drivers/me/status` | driver | Go `AVAILABLE` or `OFFLINE` |
| PUT | `/api/drivers/me/location` | driver | Send current position |
| GET | `/api/drivers/me`, `/api/drivers/me/offer` | driver | Profile, open offer (204 if none) |
| GET | `/api/drivers/nearby?lat=&lng=` | any | Anonymous positions of free cars |

Errors always look like `{"status": 409, "code": "INVALID_TRANSITION", "detail": "..."}`.

## Project layout

```
backend/     Spring Boot API: auth, rides, drivers, matching (com.rideai.*); Flyway migrations
frontend/    Next.js app: /login, /rider, /driver, /status (Leaflet + OpenStreetMap)
simulator/   Node script that runs fake drivers
docker-compose.yml        infrastructure for development
docker-compose.full.yml   adds the backend and frontend containers
.github/workflows/ci.yml  build, test, publish images
```

## Troubleshooting

- **Port already in use (5433, 6379, 9092, 8080, 3000):** another Postgres/Redis or old container is running. `docker ps` and stop it, or change the left-hand port in `docker-compose.yml`.
- **Testcontainers says "client version 1.32 is too old":** newer Docker Engines dropped old API versions. `backend/src/test/resources/docker-java.properties` pins `api.version=1.44`; if you still see it, update Docker Desktop.
- **Backend can't connect to Kafka:** make sure `docker compose ps` shows kafka running, and that the app uses `localhost:9092` (containers use `kafka:29092`).
- **Start over with a clean database:** `docker compose down -v`.
- **Prices say "straight-line estimate":** the free OSRM routing server was unreachable, so distance is estimated. Everything else works.
- **Signed in but everything says 401:** the backend was restarted with a different `JWT_SECRET`. Sign out and back in.

## Roadmap

- [x] Phase 0 – Setup: infra, health checks, CI
- [x] Phase 1 – Auth (JWT) and trip state machine
- [x] Phase 2 – Map, nearest-driver matching with Redis GEO, driver simulator
- [ ] Phase 3 – Live tracking over WebSocket
- [ ] Phase 4 – Kafka trip events, async matching, delay detection
- [ ] Phase 5 – AI Trip Assistant (Claude API)
- [ ] Phase 6 – Integration tests, Docker images, Sentry
- [ ] Phase 7 – Polish, demo video, load test
