"use client";

import { useEffect, useRef } from "react";

/**
 * Calls `fn` now and then every `ms` milliseconds while `enabled` is true.
 * Phase 3 replaces most polling with WebSocket pushes.
 */
export function usePolling(fn: () => Promise<void> | void, ms: number, enabled = true) {
  const latest = useRef(fn);

  useEffect(() => {
    latest.current = fn;
  });

  useEffect(() => {
    if (!enabled) return;
    let stopped = false;
    const tick = async () => {
      if (stopped) return;
      try {
        await latest.current();
      } catch {
        // a failed poll is retried on the next tick
      }
    };
    const first = setTimeout(tick, 0);
    const id = setInterval(tick, ms);
    return () => {
      stopped = true;
      clearTimeout(first);
      clearInterval(id);
    };
  }, [ms, enabled]);
}
