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
  { label: "HQ Admin", username: "hq_admin", password: "pgAdmin@2025", role: "ROLE_ADMIN" },
  { label: "Procurement (North)", username: "proc_north", password: "procN@2025", role: "ROLE_PROCUREMENT_OFFICER" },
  { label: "Site Manager", username: "site_raj", password: "siteR@2025", role: "ROLE_SITE_MANAGER" },
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
    <div className="min-h-screen px-3 md:px-4 py-3 md:py-4">
      <div className="pg-stage max-w-[1500px] mx-auto">
        <section className="px-5 md:px-8 pt-10 md:pt-12 pb-8 md:pb-10 grid xl:grid-cols-[1.1fr_0.9fr] gap-6 relative">
          <div className="relative z-10">
            <h1 className="font-display text-[3rem] md:text-[5.4rem] leading-[0.98] tracking-[-0.045em]">
              Material
              <br />
              Forecasting
              <br />
              Platform
            </h1>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-8 max-w-[560px]">
              <div className="pg-card px-4 py-3">Forecast Editions</div>
              <div className="pg-card px-4 py-3">Procurement Plans</div>
              <div className="pg-card px-4 py-3">Inventory Interfaces</div>
              <div className="pg-card px-4 py-3">Risk Ventures</div>
            </div>
          </div>

          <div className="relative z-20 self-start">
            <form onSubmit={submit} className="pg-card pg-glass pg-premium-card p-5 md:p-6 rounded-[26px]">
              <h2 className="font-display text-3xl leading-tight">
                {currentPortal ? currentPortal.portalTitle : "POWERGRID Staff Login"}
              </h2>
              <p className="pg-subtitle mt-2">
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
                            borderColor: "rgba(255,255,255,0.45)",
                            color: "#f8fafc",
                            background: "linear-gradient(180deg, rgba(255,255,255,0.24), rgba(142,150,162,0.22))",
                          }
                        : undefined
                    }
                  >
                    {meta.label}
                  </button>
                ))}
              </div>

              <div className="mt-5 space-y-4">
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

              <div className="mt-6">
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
                        className="pg-card p-3 text-left"
                        style={{ animation: "pg-rise-in 0.35s ease both", animationDelay: `${index * 0.06}s` }}
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
        </section>
      </div>
    </div>
  );
}
