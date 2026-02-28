import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";

const roleMeta = {
  ROLE_ADMIN: {
    label: "HQ Admin",
    portalTitle: "HQ Admin Login",
    route: "/login/admin",
    hint: "POWERGRID HQ administration access",
  },
  ROLE_PROCUREMENT_OFFICER: {
    label: "Procurement Officer",
    portalTitle: "Procurement Officer Login",
    route: "/login/procurement",
    hint: "Procurement operations access",
  },
  ROLE_SITE_MANAGER: {
    label: "Site Manager",
    portalTitle: "Site Manager Login",
    route: "/login/site-manager",
    hint: "Warehouse and site operations access",
  },
};

const demoCards = [
  { label: "🔑 HQ Admin", username: "hq_admin", password: "pgAdmin@2025", role: "ROLE_ADMIN" },
  { label: "📋 Procurement (N)", username: "proc_north", password: "procN@2025", role: "ROLE_PROCUREMENT_OFFICER" },
  { label: "🏗️ Site Manager", username: "site_raj", password: "siteR@2025", role: "ROLE_SITE_MANAGER" },
];

function homeByRole(role) {
  if (role === "ROLE_ADMIN") return "/admin/dashboard";
  if (role === "ROLE_PROCUREMENT_OFFICER") return "/procurement/dashboard";
  return "/site-manager/dashboard";
}

function parseLoginError(err) {
  if (err?.response?.status === 401) {
    return "Invalid username/email or password.";
  }
  return (
    err?.response?.data?.message ||
    err?.response?.data?.error ||
    err?.message ||
    "Login failed"
  );
}

export default function LoginPage({ requiredRole = null }) {
  const { login, logout, loading } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState("");
  const [openDemo, setOpenDemo] = useState(true);
  const [form, setForm] = useState({ username: "", password: "" });
  const currentPortal = requiredRole ? roleMeta[requiredRole] : null;
  const visibleDemoCards = requiredRole
    ? demoCards.filter((card) => card.role === requiredRole)
    : demoCards;

  const submit = async (event) => {
    event.preventDefault();
    setError("");

    if (!form.username.trim() || !form.password.trim()) {
      setError("Username and password are required.");
      return;
    }

    try {
      const data = await login({
        username: form.username.trim(),
        password: form.password,
      });

      if (requiredRole && data.role !== requiredRole) {
        logout();
        setError(`This portal is restricted to ${roleMeta[requiredRole].label}. Please use the correct login page.`);
        return;
      }

      navigate(homeByRole(data.role), { replace: true });
    } catch (err) {
      setError(parseLoginError(err));
    }
  };

  return (
    <div className="min-h-screen relative overflow-hidden text-[var(--text-1)]">
      <div
        className="absolute -top-20 -left-20 w-80 h-80 rounded-full blur-3xl opacity-55"
        style={{ background: "radial-gradient(circle, rgba(79,142,247,0.45), transparent 70%)", animation: "pg-orb-drift-left 14s ease-in-out infinite" }}
      />
      <div
        className="absolute -bottom-24 right-[-40px] w-96 h-96 rounded-full blur-3xl opacity-60"
        style={{ background: "radial-gradient(circle, rgba(244,124,32,0.38), transparent 72%)", animation: "pg-orb-drift 16s ease-in-out infinite" }}
      />

      <div className="relative min-h-screen flex">
        <div className="w-[45%] hide-mobile p-10 flex flex-col justify-between">
          <div className="pg-glass rounded-3xl p-8 h-full flex flex-col justify-between">
            <div>
              <div className="font-display text-5xl md:text-[3.4rem] font-extrabold tracking-tight">⚡ POWERGRID</div>
              <div className="mt-2 pg-subtitle">Power Grid Corporation of India Limited</div>
              <div className="mt-1 text-sm" style={{ color: "var(--orange)" }}>
                Ministry of Power | Government of India
              </div>

              <div className="mt-12 space-y-4 text-base">
                <div className="pg-card px-4 py-3">🔮 AI-Powered Material Demand Forecasting</div>
                <div className="pg-card px-4 py-3">📦 Real-Time Inventory & Procurement Management</div>
                <div className="pg-card px-4 py-3">📊 Project Phase-Aware Supply Chain Planning</div>
              </div>
            </div>

            <div className="pg-chip inline-block mt-8" style={{ background: "var(--orange-dim)", color: "var(--orange)" }}>
              Problem Statement #25193 | Smart Automation
            </div>
          </div>
        </div>

        <div className="flex-1 p-6 md:p-12 flex items-center justify-center">
          <form onSubmit={submit} className="pg-card pg-premium-card pg-glass w-full max-w-lg p-7 md:p-8">
            <h1 className="font-display text-3xl tracking-tight">
              {currentPortal ? currentPortal.portalTitle : "POWERGRID Staff Login"}
            </h1>
            <p className="pg-subtitle mt-1">
              {currentPortal ? currentPortal.hint : "Secure access for authorised personnel only"}
            </p>

            <div className="mt-4 grid grid-cols-1 sm:grid-cols-3 gap-2 text-xs">
              {Object.entries(roleMeta).map(([role, meta]) => (
                <button
                  key={role}
                  type="button"
                  className="pg-btn px-2 py-2"
                  onClick={() => navigate(meta.route)}
                  style={
                    role === requiredRole
                      ? {
                          borderColor: "var(--orange)",
                          color: "var(--orange)",
                          background: "linear-gradient(90deg, rgba(244,124,32,0.18), rgba(244,124,32,0.08))",
                        }
                      : undefined
                  }
                >
                  {meta.label}
                </button>
              ))}
            </div>

            <div className="mt-6 space-y-4">
              <label className="block">
                <span className="pg-label">Username or Email</span>
                <input
                  className="pg-input mt-1"
                  value={form.username}
                  onChange={(e) => setForm((p) => ({ ...p, username: e.target.value }))}
                  placeholder="Enter username or email"
                />
              </label>
              <label className="block">
                <span className="pg-label">Password</span>
                <input
                  type="password"
                  className="pg-input mt-1"
                  value={form.password}
                  onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))}
                  placeholder="Enter password"
                />
              </label>
            </div>

            {error ? <div className="mt-3 text-sm" style={{ color: "var(--red)" }}>{error}</div> : null}

            <button disabled={loading} className="pg-btn pg-btn-primary w-full mt-6">
              {loading ? "Signing in..." : "Sign In"}
            </button>

            <div className="mt-7">
              <button
                type="button"
                className="w-full text-left text-sm text-[var(--text-2)]"
                onClick={() => setOpenDemo((v) => !v)}
              >
                Demo Credentials {openDemo ? "▲" : "▼"}
              </button>
              {openDemo ? (
                <div className="mt-3 grid gap-2">
                  {visibleDemoCards.map((card, index) => (
                    <button
                      key={card.username}
                      type="button"
                      className="pg-card p-3 text-left hover:border-[var(--orange)]"
                      style={{ animation: `pg-rise-in 0.35s ease both`, animationDelay: `${index * 0.06}s` }}
                      onClick={() => setForm({ username: card.username, password: card.password })}
                    >
                      <div className="font-medium">{card.label}</div>
                      <div className="text-xs text-[var(--text-2)]">
                        {card.username} / {card.password}
                      </div>
                    </button>
                  ))}
                </div>
              ) : null}
            </div>
          </form>
        </div>
      </div>
    </div>
  );
}
