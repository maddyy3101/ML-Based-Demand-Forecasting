import { Outlet, useLocation } from "react-router-dom";
import AppShell from "../../components/layout/AppShell";

function titleFor(path) {
  if (path.includes("warehouse-inventory")) return ["Warehouse Stock", "Region-scoped inventory visibility and stock health"];
  if (path.includes("material-receipt")) return ["Log Material Receipt", "Record materials received from vendor at warehouse"];
  if (path.includes("material-deployment")) return ["Log Material Deployment", "Record materials dispatched from warehouse to project site"];
  if (path.includes("procurement-recommendations")) return ["Procurement Recommendations", "AI-driven procurement priorities"];
  if (path.includes("material-movement-history")) return ["Movement History", "Receipt and deployment audit timeline"];
  return ["Site Manager Dashboard", "Warehouse operations and material flow intelligence"];
}

export default function SiteManagerLayout() {
  const location = useLocation();
  const [title, subtitle] = titleFor(location.pathname);

  return (
    <AppShell title={title} subtitle={subtitle}>
      <Outlet />
    </AppShell>
  );
}
