import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import "./LoginPage.css";

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

const credibilityStats = [
  "TRUSTED ACROSS 28 STATES",
  "₹4,200 CR PROCUREMENT MANAGED",
  "99.8% UPTIME",
];

const capabilityCards = [
  {
    title: "Live Demand Forecasting",
    description: "LSTM, XGBoost & Prophet models running in real time",
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M13 2L4 14h6l-1 8 9-12h-6l1-8Z" />
      </svg>
    ),
  },
  {
    title: "Multi-Project Planning",
    description: "Coordinate procurement across 500+ concurrent projects",
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M3 7h7v7H3V7Zm11 0h7v4h-7V7ZM14 14h7v7h-7v-7ZM3 17h7v4H3v-4Z" />
      </svg>
    ),
  },
  {
    title: "Inventory Optimisation",
    description: "Safety stock and reorder point auto-calculation",
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4 6h16v3H4V6Zm0 5h16v7H4v-7Zm3 2v3h4v-3H7Z" />
      </svg>
    ),
  },
  {
    title: "Exception Alerts",
    description: "Instant stockout and overstock risk notifications",
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M12 3 2 20h20L12 3Zm1 6v5h-2V9h2Zm0 8v2h-2v-2h2Z" />
      </svg>
    ),
  },
  {
    title: "ProcBot AI Assistant",
    description: "Ask questions about forecasts in plain language",
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M5 4h14a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-5l-4 3v-3H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2Zm3 6h2v2H8v-2Zm6 0h2v2h-2v-2Z" />
      </svg>
    ),
  },
  {
    title: "What-If Analysis",
    description: "Simulate procurement scenarios before committing",
    icon: (
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M4 19h16v2H4v-2Zm2-3 4-4 3 3 5-6 2 2-7 8-3-3-2 2-2-2Z" />
      </svg>
    ),
  },
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
    <div className="pg-app-shell cmdiq-login-page min-h-screen px-3 md:px-4 py-3 md:py-4">
      <div className="pg-shell-frame cmdiq-login-shell">
        <div className="pg-stage cmdiq-login-outer-card">
          <div className="pg-stage-inner cmdiq-login-stage">
            <section className="cmdiq-login-grid">
              <section className="cmdiq-left-panel">
                <div className="cmdiq-bg-animations" aria-hidden="true">
                  <div className="cmdiq-grid-lines" />
                  <svg className="cmdiq-network-lines" viewBox="0 0 1000 900" preserveAspectRatio="none">
                    <line x1="140" y1="150" x2="360" y2="240" />
                    <line x1="360" y1="240" x2="620" y2="210" />
                    <line x1="620" y1="210" x2="780" y2="360" />
                    <line x1="220" y1="460" x2="360" y2="240" />
                    <line x1="220" y1="460" x2="520" y2="540" />
                    <line x1="520" y1="540" x2="780" y2="360" />
                    <line x1="520" y1="540" x2="720" y2="680" />
                  </svg>
                  <span className="cmdiq-node" style={{ left: "13%", top: "16%", animationDelay: "0s" }} />
                  <span className="cmdiq-node" style={{ left: "33%", top: "27%", animationDelay: "0.5s" }} />
                  <span className="cmdiq-node" style={{ left: "58%", top: "22%", animationDelay: "1s" }} />
                  <span className="cmdiq-node" style={{ left: "73%", top: "40%", animationDelay: "1.5s" }} />
                  <span className="cmdiq-node" style={{ left: "21%", top: "52%", animationDelay: "2s" }} />
                  <span className="cmdiq-node" style={{ left: "48%", top: "60%", animationDelay: "2.5s" }} />
                  <span className="cmdiq-node" style={{ left: "69%", top: "72%", animationDelay: "3s" }} />
                  <svg className="cmdiq-chart-silhouette" viewBox="0 0 1000 300" preserveAspectRatio="none">
                    <polyline points="40,240 140,210 230,220 320,180 430,195 560,120 660,145 760,92 870,110 960,74" />
                  </svg>
                </div>

                <div className="cmdiq-left-content">
                  <div className="cmdiq-logo-lockup">
                    <div className="cmdiq-logo-mark" aria-hidden="true">
                      <svg viewBox="0 0 24 24">
                        <path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z" />
                      </svg>
                    </div>
                    <div>
                      <div className="cmdiq-logo-wordmark">POWERGRID</div>
                      <div className="cmdiq-logo-subline">Ministry of Power · GoI</div>
                    </div>
                  </div>

                  <div className="cmdiq-hero">
                    <h1 className="cmdiq-title">
                      <span className="cmdiq-title-word cmdiq-word-a">POWERGRID</span>
                      <span className="cmdiq-title-word cmdiq-word-b">CommandIQ</span>
                    </h1>
                    <p className="cmdiq-tagline">Intelligent Supply Chain Intelligence for India's Power Grid</p>
                    <div className="cmdiq-stats-row">
                      {credibilityStats.map((item, index) => (
                        <div className="cmdiq-stat-pill" key={item}>
                          <span>{item}</span>
                          {index < credibilityStats.length - 1 ? <span className="cmdiq-stat-divider" /> : null}
                        </div>
                      ))}
                    </div>
                  </div>

                  <div className="cmdiq-capability-grid">
                    {capabilityCards.map((card, index) => (
                      <article className="pg-card cmdiq-capability-card" key={card.title} style={{ "--card-index": index }}>
                        <div className="cmdiq-capability-icon">{card.icon}</div>
                        <h3>{card.title}</h3>
                        <p>{card.description}</p>
                      </article>
                    ))}
                  </div>
                </div>
              </section>

              <section className="cmdiq-right-panel">
                <form onSubmit={submit} className="pg-card pg-glass pg-premium-card cmdiq-login-card">
                  <h2>Sign in to CommandIQ</h2>
                  <p className="cmdiq-form-subtitle">
                    {currentPortal ? `${currentPortal.label} secure access` : "Secure access for authorised personnel only"}
                  </p>

                  <div className="cmdiq-role-tabs">
                    {Object.entries(roleMeta).map(([role, meta]) => (
                      <button
                        key={role}
                        type="button"
                        className={`cmdiq-role-tab ${role === requiredRole ? "is-active" : ""}`}
                        onClick={() => navigate(meta.route)}
                      >
                        {meta.label}
                      </button>
                    ))}
                  </div>

                  <div className="cmdiq-form-fields">
                    <label>
                      <span className="cmdiq-input-label">Username or Email</span>
                      <input
                        className="pg-input cmdiq-input"
                        value={form.username}
                        onChange={(e) => setForm((p) => ({ ...p, username: e.target.value }))}
                        placeholder="Enter username or email"
                      />
                    </label>
                    <label>
                      <span className="cmdiq-input-label">Password</span>
                      <input
                        type="password"
                        className="pg-input cmdiq-input"
                        value={form.password}
                        onChange={(e) => setForm((p) => ({ ...p, password: e.target.value }))}
                        placeholder="Enter password"
                      />
                    </label>
                  </div>

                  {error ? <div className="cmdiq-form-error">{error}</div> : null}

                  <button disabled={loading} className="pg-btn pg-btn-primary cmdiq-signin-btn">
                    {loading ? "Signing in..." : "Sign In"}
                  </button>

                  <div className="cmdiq-demo-box pg-card">
                    <button type="button" className="cmdiq-demo-toggle" onClick={() => setOpenDemo((v) => !v)}>
                      Demo Credentials {openDemo ? "▲" : "▼"}
                    </button>
                    {openDemo ? (
                      <div className="cmdiq-demo-list">
                        {visibleDemoCards.map((card, index) => (
                          <button
                            key={card.username}
                            type="button"
                            className="cmdiq-demo-card pg-card"
                            style={{ animation: "fadeUp 0.36s ease both", animationDelay: `${index * 0.06}s` }}
                            onClick={() => setForm({ username: card.username, password: card.password })}
                          >
                            <div>{card.label}</div>
                            <div className="cmdiq-demo-creds">{card.username} / {card.password}</div>
                          </button>
                        ))}
                      </div>
                    ) : null}
                  </div>
                </form>
              </section>
            </section>
          </div>
        </div>
      </div>
    </div>
  );
}
