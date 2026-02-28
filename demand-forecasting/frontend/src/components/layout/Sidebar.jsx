import { NavLink } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

const navConfig = {
  ROLE_ADMIN: [
    {
      group: "OVERVIEW",
      items: [
        { label: "Dashboard", to: "/admin/dashboard" },
        { label: "System Health", to: "/admin/system-health" },
      ],
    },
    {
      group: "DATA & MODELS",
      items: [
        { label: "Dataset Upload", to: "/admin/dataset-upload" },
        { label: "Model Management", to: "/admin/model-management" },
      ],
    },
    {
      group: "GOVERNANCE",
      items: [
        { label: "User Management", to: "/admin/users" },
        { label: "Audit Log", to: "/admin/audit-log" },
      ],
    },
  ],
  ROLE_PROCUREMENT_OFFICER: [
    {
      group: "OVERVIEW",
      items: [{ label: "Dashboard", to: "/procurement/dashboard" }],
    },
    {
      group: "FORECASTING",
      items: [
        { label: "New Forecast", to: "/procurement/new-forecast" },
        { label: "Multi-Project Forecast", to: "/procurement/multi-project-forecast" },
        { label: "Async Forecast", to: "/procurement/async-forecast" },
        { label: "What-If Analysis", to: "/procurement/what-if" },
      ],
    },
    {
      group: "PLANNING",
      items: [
        { label: "Supply Chain Planning", to: "/procurement/planning" },
        { label: "Forecast History", to: "/procurement/history" },
        { label: "Alerts", to: "/procurement/alerts" },
      ],
    },
  ],
  ROLE_SITE_MANAGER: [
    {
      group: "OVERVIEW",
      items: [{ label: "Dashboard", to: "/site-manager/dashboard" }],
    },
    {
      group: "INVENTORY",
      items: [
        { label: "Warehouse Stock", to: "/site-manager/warehouse-inventory" },
        { label: "Material Receipt", to: "/site-manager/material-receipt" },
        { label: "Material Deployment", to: "/site-manager/material-deployment" },
      ],
    },
    {
      group: "INTELLIGENCE",
      items: [
        { label: "Procurement Recommendations", to: "/site-manager/procurement-recommendations" },
        { label: "Movement History", to: "/site-manager/material-movement-history" },
      ],
    },
  ],
};

function linkClass({ isActive }) {
  return isActive
    ? "block px-3 py-2 rounded-md text-sm border-l-4 transition-all duration-200"
    : "block px-3 py-2 rounded-md text-sm transition-all duration-200 hover:bg-[rgba(77,118,191,0.14)] hover:text-[var(--text-1)]";
}

export default function Sidebar() {
  const { user } = useAuth();
  const role = user?.role;
  const groups = navConfig[role] || [];

  return (
    <aside className="w-72 h-screen border-r border-[var(--border)] overflow-y-auto pg-glass">
      <div className="p-4 border-b border-[var(--border)]">
        <div className="font-display text-2xl font-bold">
          <span style={{ color: "var(--orange)" }}>⚡</span> POWERGRID
        </div>
        <div className="pg-subtitle mt-1">Infrastructure Material Intelligence</div>
      </div>

      <div className="p-4 border-b border-[var(--border)]">
        <div className="w-10 h-10 rounded-full bg-[var(--navy)] flex items-center justify-center font-semibold border border-[var(--border-hi)]">
          {(user?.username || "NA").slice(0, 2).toUpperCase()}
        </div>
        <div className="mt-2 text-sm font-medium">{user?.username}</div>
        <div className="text-xs text-[var(--text-2)]">{role}</div>
        {user?.assignedRegion ? <div className="text-xs mt-1">Region: {user.assignedRegion}</div> : null}
      </div>

      <nav className="p-3 space-y-4">
        {groups.map((group) => (
          <div key={group.group}>
            <div className="px-2 text-[11px] font-semibold tracking-[0.16em] text-[var(--text-3)] mb-1">{group.group}</div>
            <div className="space-y-1">
              {group.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={linkClass}
                  style={({ isActive }) =>
                    isActive
                      ? {
                          borderLeftColor: "var(--orange)",
                          background: "linear-gradient(90deg, rgba(244,124,32,0.2), rgba(244,124,32,0.08))",
                          color: "var(--orange)",
                          boxShadow: "0 8px 18px rgba(0,0,0,0.25)",
                        }
                      : { color: "var(--text-2)" }
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          </div>
        ))}
      </nav>
    </aside>
  );
}
