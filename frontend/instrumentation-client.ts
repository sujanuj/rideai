// Runs in the browser before the app becomes interactive (Next.js convention).
// Error monitoring is off unless NEXT_PUBLIC_SENTRY_DSN is set.
import * as Sentry from "@sentry/browser";

const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN;

if (dsn) {
  try {
    Sentry.init({
      dsn,
      environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT ?? "local",
      tracesSampleRate: 0.1,
    });
  } catch {
    // never let monitoring break the app
  }
}
