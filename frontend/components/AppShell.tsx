"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, type ReactNode } from "react";
import { saveSession, useHydrated, useSession } from "@/lib/session";
import type { Role, Session } from "@/lib/types";

/**
 * Page frame for signed-in screens: header, and a guard that sends you to /login
 * if you're signed out or to the right screen if you have the other role.
 */
export default function AppShell({
  role,
  children,
}: {
  role: Role;
  children: (session: Session) => ReactNode;
}) {
  const session = useSession();
  const hydrated = useHydrated();
  const router = useRouter();

  useEffect(() => {
    if (!hydrated) return;
    if (!session) router.replace("/login");
    else if (session.user.role !== role) router.replace(session.user.role === "DRIVER" ? "/driver" : "/rider");
  }, [hydrated, session, role, router]);

  if (!session || session.user.role !== role) {
    return <div className="flex flex-1 items-center justify-center text-sm text-zinc-500">Loading…</div>;
  }

  return (
    <div className="flex min-h-dvh flex-col">
      <header className="flex items-center justify-between border-b border-zinc-200 px-4 py-3 dark:border-zinc-800">
        <Link href="/" className="text-lg font-semibold tracking-tight">
          RideAI <span className="text-sm font-normal text-zinc-500">{role === "DRIVER" ? "Driver" : "Rider"}</span>
        </Link>
        <div className="flex items-center gap-3 text-sm">
          <span className="hidden text-zinc-600 sm:inline dark:text-zinc-400">{session.user.fullName}</span>
          <button
            onClick={() => {
              saveSession(null);
              router.replace("/login");
            }}
            className="rounded-lg border border-zinc-300 px-3 py-1 hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900"
          >
            Sign out
          </button>
        </div>
      </header>
      {children(session)}
    </div>
  );
}
