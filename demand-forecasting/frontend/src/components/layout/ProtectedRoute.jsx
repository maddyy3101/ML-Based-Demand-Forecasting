import { Navigate, Outlet } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";

function roleHome(role) {
  if (role === "ROLE_ADMIN") return "/admin/dashboard";
  if (role === "ROLE_PROCUREMENT_OFFICER") return "/procurement/dashboard";
  if (role === "ROLE_SITE_MANAGER") return "/site-manager/dashboard";
  return "/login";
}

export default function ProtectedRoute({ role }) {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (role && user?.role !== role) {
    return <Navigate to={roleHome(user?.role)} replace />;
  }

  return <Outlet />;
}
