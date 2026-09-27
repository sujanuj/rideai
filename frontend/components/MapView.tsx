"use client";

import dynamic from "next/dynamic";
import type { MapProps } from "./Map";

// Leaflet needs `window`, so skip server rendering for the map.
const RideMap = dynamic(() => import("./Map"), {
  ssr: false,
  loading: () => <div className="h-full w-full animate-pulse bg-zinc-200 dark:bg-zinc-800" />,
});

export default function MapView(props: MapProps) {
  return <RideMap {...props} />;
}
