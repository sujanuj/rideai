"use client";

import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { Suspense, useState, type FormEvent } from "react";
import { Button, Card, ErrorText } from "@/components/ui";
import { api, errorMessage } from "@/lib/api";
import { saveSession } from "@/lib/session";
import type { Role, Session } from "@/lib/types";

export default function LoginPage() {
  // useSearchParams needs a Suspense boundary in the App Router.
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const [mode, setMode] = useState<"login" | "register">("login");
  const [role, setRole] = useState<Role>(params.get("role") === "DRIVER" ? "DRIVER" : "RIDER");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fullName, setFullName] = useState("");
  const [vehicle, setVehicle] = useState("");
  const [plate, setPlate] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const session =
        mode === "login"
          ? await api<Session>("/auth/login", { method: "POST", body: { email, password } })
          : await api<Session>("/auth/register", {
              method: "POST",
              body: {
                email,
                password,
                fullName,
                role,
                ...(role === "DRIVER" ? { vehicle, plate } : {}),
              },
            });
      saveSession(session);
      router.replace(session.user.role === "DRIVER" ? "/driver" : "/rider");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  const input =
    "w-full rounded-lg border border-zinc-300 bg-transparent px-3 py-2 text-sm outline-none focus:border-zinc-900 dark:border-zinc-700 dark:focus:border-zinc-300";

  return (
    <main className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center gap-6 px-4 py-12">
      <Link href="/" className="text-2xl font-semibold tracking-tight">
        RideAI
      </Link>

      <Card>
        <div className="mb-5 grid grid-cols-2 gap-1 rounded-lg bg-zinc-100 p-1 text-sm dark:bg-zinc-900">
          {(["login", "register"] as const).map((m) => (
            <button
              key={m}
              type="button"
              onClick={() => setMode(m)}
              className={`rounded-md py-1.5 ${mode === m ? "bg-white font-medium shadow-sm dark:bg-zinc-800" : "text-zinc-500"}`}
            >
              {m === "login" ? "Sign in" : "Create account"}
            </button>
          ))}
        </div>

        <form onSubmit={submit} className="flex flex-col gap-3">
          {mode === "register" && (
            <>
              <div className="flex gap-2 text-sm">
                {(["RIDER", "DRIVER"] as const).map((r) => (
                  <label
                    key={r}
                    className={`flex flex-1 cursor-pointer items-center justify-center rounded-lg border px-3 py-2 ${
                      role === r ? "border-zinc-900 font-medium dark:border-zinc-200" : "border-zinc-300 dark:border-zinc-700"
                    }`}
                  >
                    <input type="radio" className="sr-only" checked={role === r} onChange={() => setRole(r)} />
                    {r === "RIDER" ? "I want to ride" : "I want to drive"}
                  </label>
                ))}
              </div>
              <input className={input} placeholder="Full name" value={fullName} onChange={(e) => setFullName(e.target.value)} required />
            </>
          )}

          <input className={input} type="email" placeholder="Email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
          <input
            className={input}
            type="password"
            placeholder={mode === "register" ? "Password (8+ characters)" : "Password"}
            autoComplete={mode === "register" ? "new-password" : "current-password"}
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />

          {mode === "register" && role === "DRIVER" && (
            <div className="flex gap-2">
              <input className={input} placeholder="Vehicle (e.g. Toyota Prius)" value={vehicle} onChange={(e) => setVehicle(e.target.value)} required />
              <input className={`${input} max-w-32`} placeholder="Plate" value={plate} onChange={(e) => setPlate(e.target.value)} required />
            </div>
          )}

          <ErrorText>{error}</ErrorText>

          <Button type="submit" disabled={busy}>
            {busy ? "Please wait…" : mode === "login" ? "Sign in" : "Create account"}
          </Button>
        </form>
      </Card>

      <p className="text-center text-xs text-zinc-500">
        Demo tip: run the driver simulator (see README) to get cars moving around Tempe.
      </p>
    </main>
  );
}
