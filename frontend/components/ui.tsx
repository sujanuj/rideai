import type { ButtonHTMLAttributes, ReactNode } from "react";

type Variant = "primary" | "secondary" | "danger";

const styles: Record<Variant, string> = {
  primary: "bg-zinc-900 text-white hover:bg-zinc-700 dark:bg-white dark:text-zinc-900 dark:hover:bg-zinc-200",
  secondary:
    "border border-zinc-300 hover:bg-zinc-100 dark:border-zinc-700 dark:hover:bg-zinc-900",
  danger: "border border-red-300 text-red-700 hover:bg-red-50 dark:border-red-900 dark:text-red-400 dark:hover:bg-red-950",
};

export function Button({
  variant = "primary",
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      {...props}
      className={`rounded-lg px-4 py-2 text-sm font-medium transition disabled:cursor-not-allowed disabled:opacity-50 ${styles[variant]} ${className}`}
    />
  );
}

export function Card({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div className={`rounded-xl border border-zinc-200 p-4 dark:border-zinc-800 ${className}`}>{children}</div>
  );
}

export function ErrorText({ children }: { children: ReactNode }) {
  if (!children) return null;
  return <p className="text-sm text-red-600 dark:text-red-400">{children}</p>;
}

export function Stat({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div>
      <div className="text-xs uppercase tracking-wide text-zinc-500">{label}</div>
      <div className="text-base font-semibold">{value}</div>
    </div>
  );
}

/** Green dot when the WebSocket is connected. */
export function LiveBadge({ connected }: { connected: boolean }) {
  return (
    <span className={`flex items-center gap-1 normal-case ${connected ? "text-green-600" : "text-zinc-500"}`}>
      <span className={`h-2 w-2 rounded-full ${connected ? "animate-pulse bg-green-500" : "bg-zinc-400"}`} />
      {connected ? "Live" : "Reconnecting"}
    </span>
  );
}
