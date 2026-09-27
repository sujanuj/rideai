"use client";

import { Client, type IMessage } from "@stomp/stompjs";
import { useEffect, useRef, useState } from "react";

// Live updates over WebSocket + STOMP, straight to Spring Boot on port 8080
// (Next's dev proxy doesn't forward WebSocket upgrades).

function socketUrl(): string {
  if (process.env.NEXT_PUBLIC_WS_URL) return process.env.NEXT_PUBLIC_WS_URL;
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.hostname}:8080/ws`;
}

/** One authenticated STOMP connection that reconnects by itself. */
export function useSocket(token: string | null) {
  const [client, setClient] = useState<Client | null>(null);
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    if (!token) return;
    const c = new Client({
      brokerURL: socketUrl(),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => setConnected(true),
      onWebSocketClose: () => setConnected(false),
      onStompError: () => setConnected(false),
      debug: () => {},
    });
    c.activate();
    // eslint-disable-next-line react-hooks/set-state-in-effect -- expose the client once it exists
    setClient(c);
    return () => {
      setConnected(false);
      void c.deactivate();
    };
  }, [token]);

  return { client, connected };
}

/** Subscribe to a destination while connected; resubscribes after reconnects. */
export function useSubscription<T>(
  client: Client | null,
  connected: boolean,
  destination: string | null,
  onMessage: (body: T) => void,
) {
  const handler = useRef(onMessage);
  useEffect(() => {
    handler.current = onMessage;
  });

  useEffect(() => {
    if (!client || !connected || !destination) return;
    const sub = client.subscribe(destination, (msg: IMessage) => {
      try {
        handler.current(JSON.parse(msg.body) as T);
      } catch {
        // ignore malformed messages
      }
    });
    return () => {
      try {
        sub.unsubscribe();
      } catch {
        // connection already gone
      }
    };
  }, [client, connected, destination]);
}
