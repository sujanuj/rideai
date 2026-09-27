"use client";

import "leaflet/dist/leaflet.css";
import { useEffect } from "react";
import { CircleMarker, MapContainer, Polyline, TileLayer, Tooltip, useMap, useMapEvents } from "react-leaflet";
import type { LatLng } from "@/lib/types";

// Leaflet touches `window`, so this file is only ever loaded in the browser (see MapView.tsx).
// Everything is drawn with CircleMarkers: no marker images to configure.

export type MapProps = {
  center: LatLng;
  pickup?: LatLng | null;
  dropoff?: LatLng | null;
  route?: LatLng[];
  /** Nearby available cars (anonymous). */
  cars?: LatLng[];
  /** The assigned driver, or the driver themself on the driver screen. */
  vehicle?: LatLng | null;
  vehicleLabel?: string;
  onClick?: (point: LatLng) => void;
  /** Points the map should zoom to show; refits whenever they change. */
  fitTo?: LatLng[];
};

export default function RideMap({
  center,
  pickup,
  dropoff,
  route,
  cars = [],
  vehicle,
  vehicleLabel = "Driver",
  onClick,
  fitTo = [],
}: MapProps) {
  return (
    <MapContainer center={[center.lat, center.lng]} zoom={14} className="h-full w-full" scrollWheelZoom>
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {onClick && <ClickHandler onClick={onClick} />}
      <FitTo points={fitTo} />

      {route && route.length > 1 && (
        <Polyline positions={route.map((p) => [p.lat, p.lng])} pathOptions={{ color: "#2563eb", weight: 5, opacity: 0.8 }} />
      )}

      {cars.map((c, i) => (
        <CircleMarker
          key={i}
          center={[c.lat, c.lng]}
          radius={6}
          pathOptions={{ color: "#1e3a8a", weight: 2, fillColor: "#60a5fa", fillOpacity: 0.9 }}
        />
      ))}

      {pickup && (
        <CircleMarker center={[pickup.lat, pickup.lng]} radius={9} pathOptions={{ color: "#065f46", weight: 3, fillColor: "#10b981", fillOpacity: 1 }}>
          <Tooltip direction="top" offset={[0, -8]} permanent>Pickup</Tooltip>
        </CircleMarker>
      )}

      {dropoff && (
        <CircleMarker center={[dropoff.lat, dropoff.lng]} radius={9} pathOptions={{ color: "#111827", weight: 3, fillColor: "#f43f5e", fillOpacity: 1 }}>
          <Tooltip direction="top" offset={[0, -8]} permanent>Drop-off</Tooltip>
        </CircleMarker>
      )}

      {vehicle && (
        <CircleMarker center={[vehicle.lat, vehicle.lng]} radius={10} pathOptions={{ color: "#7c2d12", weight: 3, fillColor: "#f59e0b", fillOpacity: 1 }}>
          <Tooltip direction="top" offset={[0, -8]}>{vehicleLabel}</Tooltip>
        </CircleMarker>
      )}
    </MapContainer>
  );
}

function ClickHandler({ onClick }: { onClick: (p: LatLng) => void }) {
  useMapEvents({
    click(e) {
      onClick({ lat: e.latlng.lat, lng: e.latlng.lng });
    },
  });
  return null;
}

function FitTo({ points }: { points: LatLng[] }) {
  const map = useMap();
  const key = points.map((p) => `${p.lat.toFixed(5)},${p.lng.toFixed(5)}`).join("|");

  useEffect(() => {
    if (points.length === 1) {
      map.panTo([points[0].lat, points[0].lng]);
    } else if (points.length > 1) {
      map.fitBounds(
        points.map((p) => [p.lat, p.lng] as [number, number]),
        { padding: [48, 48], maxZoom: 16 },
      );
    }
    // Only refit when the set of points actually changes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, map]);

  return null;
}
