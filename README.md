# RideAI

A real-time ride-hailing platform with an **AI Trip Assistant** that analyzes each completed trip and suggests how the next one could go better.

**Stack:** Next.js · Spring Boot 3 (Java 21) · PostgreSQL + PostGIS · Redis (GEO) · Kafka · WebSocket (STOMP) · Claude API · Docker · GitHub Actions · Sentry

> Status: **Phase 0 – setup.** The frontend, backend, database, cache and event broker are wired together. Ride features arrive in Phase 1+.

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
#   → http://localhost:3000 shows the service status page
```

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

## Project layout

```
backend/     Spring Boot API (com.rideai.*), Flyway migrations in src/main/resources/db/migration
frontend/    Next.js app (App Router, Tailwind)
docker-compose.yml        infrastructure for development
docker-compose.full.yml   adds the backend and frontend containers
.github/workflows/ci.yml  build, test, publish images
```

## Troubleshooting

- **Port already in use (5433, 6379, 9092, 8080, 3000):** another Postgres/Redis or old container is running. `docker ps` and stop it, or change the left-hand port in `docker-compose.yml`.
- **Testcontainers says "client version 1.32 is too old":** newer Docker Engines dropped old API versions. `backend/src/test/resources/docker-java.properties` pins `api.version=1.44`; if you still see it, update Docker Desktop.
- **Backend can't connect to Kafka:** make sure `docker compose ps` shows kafka running, and that the app uses `localhost:9092` (containers use `kafka:29092`).
- **Start over with a clean database:** `docker compose down -v`.

## Roadmap

- [x] Phase 0 – Setup: infra, health checks, CI
- [ ] Phase 1 – Auth (JWT) and trip state machine
- [ ] Phase 2 – Map, nearest-driver matching with Redis GEO, driver simulator
- [ ] Phase 3 – Live tracking over WebSocket
- [ ] Phase 4 – Kafka trip events, async matching, delay detection
- [ ] Phase 5 – AI Trip Assistant (Claude API)
- [ ] Phase 6 – Integration tests, Docker images, Sentry
- [ ] Phase 7 – Polish, demo video, load test
