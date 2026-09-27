"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { useHydrated, useSession } from "@/lib/session";

// Landing page: signed-in users go straight to their screen.
export default function Home() {
  const session = useSession();
  const hydrated = useHydrated();
  const router = useRouter();

  useEffect(() => {
    if (hydrated && session) router.replace(session.user.role === "DRIVER" ? "/driver" : "/rider");
  }, [hydrated, session, router]);

  return (
    <main className="mx-auto flex w-full max-w-xl flex-1 flex-col justify-center gap-8 px-4 py-16">
      <div className="flex flex-col gap-3">
        <h1 className="text-4xl font-semibold tracking-tight">RideAI</h1>
        <p className="text-lg text-zinc-600 dark:text-zinc-400">
          Real-time ride-hailing with an AI trip assistant. Request a ride, watch the nearest driver get matched,
          and follow the trip live.
        </p>
      </div>
      <div className="flex flex-wrap gap-3">
        <Link
          href="/login?role=RIDER"
          className="rounded-lg bg-zinc-900 px-5 py-3 font-medium text-white hover:bg-zinc-700 dark:bg-white dark:text-zinc-900"
        >
          Ride with RideAI
        </Link>
        <Link
          href="/login?role=DRIVER"
          className="rounded-lg border border-zinc-300 px-5 py-3 font-medium hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900"
        >
          Drive with RideAI
        </Link>
      </div>
      <Link href="/status" className="text-sm text-zinc-500 underline">
        System status
      </Link>
    </main>
  );
}
