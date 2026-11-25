"use client";

import { useState } from "react";
import { Loader2, ShieldCheck, Workflow } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { login, setAuth } from "@/lib/batch-api";
import type { BatchAuth } from "@/lib/batch-types";

export function LoginGate({ onLogin }: { onLogin: (auth: BatchAuth) => void }) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const auth = await login(username.trim(), password);
      setAuth(auth);
      onLogin(auth);
    } catch (e) {
      setError(e instanceof Error ? e.message : "Đăng nhập thất bại");
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="min-h-screen grid place-items-center bg-gradient-to-br from-teal-50/60 via-white to-stone-100 px-4">
      <Card className="w-full max-w-sm rounded-2xl shadow-sm">
        <CardHeader className="text-center gap-2">
          <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-2xl bg-teal-600 text-white">
            <Workflow className="h-6 w-6" />
          </div>
          <CardTitle className="text-xl">DevHub Batch</CardTitle>
          <CardDescription>
            Bảng điều hành hệ thống batch — Ops Console
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="username">Tên đăng nhập (Username)</Label>
              <Input
                id="username"
                autoComplete="username"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Mật khẩu (Password)</Label>
              <Input
                id="password"
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
              />
            </div>
            {error && (
              <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700 border border-red-100">
                {error}
              </p>
            )}
            <Button type="submit" className="w-full" disabled={busy}>
              {busy && <Loader2 className="mr-2 h-4 w-4 animate-spin" />}
              Đăng nhập (Sign in)
            </Button>
          </form>
          <div className="mt-4 flex items-start gap-2 rounded-lg bg-stone-50 border border-stone-100 px-3 py-2">
            <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0 text-teal-600" />
            <p className="text-xs text-muted-foreground">
              Đăng nhập bằng tài khoản được cấu hình cho workspace.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}
