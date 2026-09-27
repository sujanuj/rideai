"use client";

import { useMemo, useSyncExternalStore } from "react";
import type { Session } from "./types";

// The logged-in session (JWT + user) lives in localStorage so it survives reloads.
// Trade-off to mention in interviews: an httpOnly cookie is safer against XSS;
// localStorage keeps this project simple.

const KEY = "rideai.session";
const listeners = new Set<() => void>();

function read(): string | null {
  try {
    return localStorage.getItem(KEY);
  } catch {
    return null;
  }
}

function subscribe(listener: () => void) {
  listeners.add(listener);
  const onStorage = (e: StorageEvent) => e.key === KEY && listener();
  window.addEventListener("storage", onStorage); // other tabs
  return () => {
    listeners.delete(listener);
    window.removeEventListener("storage", onStorage);
  };
}

export function saveSession(session: Session | null) {
  try {
    if (session) localStorage.setItem(KEY, JSON.stringify(session));
    else localStorage.removeItem(KEY);
  } catch {
    // storage blocked (private mode): the session just won't persist
  }
  listeners.forEach((l) => l());
}

/** The current session, or null. Always null during server rendering. */
export function useSession(): Session | null {
  const raw = useSyncExternalStore(subscribe, read, () => null);
  return useMemo(() => {
    if (!raw) return null;
    try {
      return JSON.parse(raw) as Session;
    } catch {
      return null;
    }
  }, [raw]);
}

/** False during server rendering and hydration, true once running in the browser. */
export function useHydrated(): boolean {
  return useSyncExternalStore(
    () => () => {},
    () => true,
    () => false,
  );
}
