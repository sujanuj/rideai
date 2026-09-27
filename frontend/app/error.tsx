"use client"; // Error boundaries must be Client Components

import * as Sentry from "@sentry/browser";
import Link from "next/link";
import { useEffect } from "react";

export default function Error({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    console.error(error);
    Sentry.captureException(error); // no-op unless Sentry is configured
  }, [error]);

  return (
    <main className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center gap-4 px-4 py-16">
      <h1 className="text-2xl font-semibold">Something went wrong</h1>
      <p className="text-zinc-600 dark:text-zinc-400">The error has been reported. You can try again.</p>
      <div className="flex gap-3">
        <button onClick={() => retry()} className="rounded-lg bg-zinc-900 px-4 py-2 text-sm font-medium text-white dark:bg-white dark:text-zinc-900">
          Try again
        </button>
        <Link href="/" className="rounded-lg border border-zinc-300 px-4 py-2 text-sm dark:border-zinc-700">
          Home
        </Link>
      </div>
    </main>
  );
}
