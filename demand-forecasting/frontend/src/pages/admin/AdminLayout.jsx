import { Outlet, useLocation } from "react-router-dom";
import AppShell from "../../components/layout/AppShell";

function titleByPath(pathname) {
  if (pathname.includes("dataset-upload")) return ["Dataset Upload", "Validate and retrain POWERGRID demand model"];
  if (pathname.includes("model-management")) return ["Model Management", "Control active model rollout and metadata"];
  if (pathname.includes("procdoc")) return ["ProcDoc", "Authenticate and activate Gemini API keys for ProcBot AI"];
  if (pathname.includes("users")) return ["User Management", "Manage POWERGRID users and access scope"];
  if (pathname.includes("audit-log")) return ["Audit Log", "Cross-user procurement forecast audit trail"];
  if (pathname.includes("system-health")) return ["System Health", "Backend, ML API, and model health telemetry"];
  return ["Admin Dashboard", "POWERGRID HQ operational overview"];
}

export default function AdminLayout() {
  const location = useLocation();
  const [title, subtitle] = titleByPath(location.pathname);

  return (
    <AppShell title={title} subtitle={subtitle}>
      <Outlet />
    </AppShell>
  );
}
