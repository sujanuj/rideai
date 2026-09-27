// Shapes returned by the Spring Boot API (see backend *Dtos.java).

export type LatLng = { lat: number; lng: number };

export type Role = "RIDER" | "DRIVER";

export type User = { id: number; email: string; fullName: string; role: Role };

export type Session = { token: string; user: User };

export type TripStatus =
  | "REQUESTED"
  | "ACCEPTED"
  | "ARRIVED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

export type DriverInfo = {
  id: number;
  name: string;
  vehicle: string | null;
  plate: string | null;
  rating: number | null;
  position: LatLng | null;
};

export type Trip = {
  id: number;
  status: TripStatus;
  pickup: LatLng;
  dropoff: LatLng;
  distanceMeters: number | null;
  durationSeconds: number | null;
  fareCents: number | null;
  riderId: number;
  riderName: string;
  driver: DriverInfo | null;
  offerExpiresAt: string | null;
  requestedAt: string;
  acceptedAt: string | null;
  arrivedAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  cancelledAt: string | null;
  cancelReason: string | null;
};

export type Estimate = {
  distanceMeters: number;
  durationSeconds: number;
  fareCents: number;
  route: LatLng[];
  routeSource: "osrm" | "estimate";
};

export type DriverStatus = "OFFLINE" | "AVAILABLE" | "ON_TRIP";

export type DriverMe = {
  id: number;
  vehicle: string;
  plate: string;
  rating: number;
  status: DriverStatus;
  position: LatLng | null;
};
