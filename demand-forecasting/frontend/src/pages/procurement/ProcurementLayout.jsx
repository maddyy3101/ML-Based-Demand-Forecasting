import { Outlet, useLocation } from "react-router-dom";
import AppShell from "../../components/layout/AppShell";

function titleFor(path) {
  if (path.includes("new-forecast")) return ["Material Demand Forecast", "Generate AI-powered procurement quantity for POWERGRID project"];
  if (path.includes("what-if")) return ["What-If Analysis", "Simulate project changes before committing procurement"];
  if (path.includes("multi-project-forecast")) return ["Multi-Project Procurement Forecast", "Forecast material demand across project pipeline"];
  if (path.includes("planning")) return ["Supply Chain Planning", "Safety stock, reorder point, and procurement plan"];
  if (path.includes("history")) return ["Procurement History", "Forecast history and actual performance"];
  if (path.includes("alerts")) return ["Shortage Alerts", "Critical shortage and overstock exceptions"];
  if (path.includes("async-forecast")) return ["Async Forecast", "Submit long-running forecast jobs"];
  return ["Procurement Dashboard", "POWERGRID procurement intelligence"];
}

export default function ProcurementLayout() {
  const location = useLocation();
  const [title, subtitle] = titleFor(location.pathname);
  return (
    <AppShell title={title} subtitle={subtitle}>
      <Outlet />
    </AppShell>
  );
}
