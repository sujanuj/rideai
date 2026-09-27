import type { NextConfig } from "next";

// Where the Spring Boot API lives. Locally that's localhost:8080;
// inside docker-compose.full.yml it's the "backend" container.
// Note: with output "standalone" this is read at BUILD time.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // Produces a small self-contained server for the Docker image.
  output: "standalone",

  // Proxy API calls through Next.js so the browser never needs CORS.
  async rewrites() {
    return [
      { source: "/api/:path*", destination: `${backendUrl}/api/:path*` },
      { source: "/backend-health", destination: `${backendUrl}/actuator/health` },
    ];
  },
};

export default nextConfig;
