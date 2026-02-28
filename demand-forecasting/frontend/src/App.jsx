import { Navigate, Route, Routes } from "react-router-dom";
import ProtectedRoute from "./components/layout/ProtectedRoute";
import LoginPage from "./pages/LoginPage";

import AdminLayout from "./pages/admin/AdminLayout";
import AdminDashboard from "./pages/admin/AdminDashboard";
import DatasetUpload from "./pages/admin/DatasetUpload";
import ModelManagement from "./pages/admin/ModelManagement";
import UserManagement from "./pages/admin/UserManagement";
import AuditLog from "./pages/admin/AuditLog";
import SystemHealth from "./pages/admin/SystemHealth";

import ProcurementLayout from "./pages/procurement/ProcurementLayout";
import ProcurementDashboard from "./pages/procurement/ProcurementDashboard";
import ForecastForm from "./pages/procurement/ForecastForm";
import WhatIfSimulator from "./pages/procurement/WhatIfSimulator";
import MultiProjectForecast from "./pages/procurement/MultiProjectForecast";
import ProcurementPlanning from "./pages/procurement/ProcurementPlanning";
import ProcurementHistory from "./pages/procurement/ProcurementHistory";
import ShortageAlerts from "./pages/procurement/ShortageAlerts";
import AsyncForecast from "./pages/procurement/AsyncForecast";

import SiteManagerLayout from "./pages/siteManager/SiteManagerLayout";
import SiteManagerDashboard from "./pages/siteManager/SiteManagerDashboard";
import WarehouseInventory from "./pages/siteManager/WarehouseInventory";
import MaterialReceipt from "./pages/siteManager/MaterialReceipt";
import MaterialDeployment from "./pages/siteManager/MaterialDeployment";
import ProcurementRecommendations from "./pages/siteManager/ProcurementRecommendations";
import MaterialMovementHistory from "./pages/siteManager/MaterialMovementHistory";

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/login" replace />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/login/admin" element={<LoginPage requiredRole="ROLE_ADMIN" />} />
      <Route path="/login/procurement" element={<LoginPage requiredRole="ROLE_PROCUREMENT_OFFICER" />} />
      <Route path="/login/site-manager" element={<LoginPage requiredRole="ROLE_SITE_MANAGER" />} />

      <Route element={<ProtectedRoute role="ROLE_ADMIN" />}>
        <Route path="/admin" element={<AdminLayout />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<AdminDashboard />} />
          <Route path="system-health" element={<SystemHealth />} />
          <Route path="dataset-upload" element={<DatasetUpload />} />
          <Route path="model-management" element={<ModelManagement />} />
          <Route path="users" element={<UserManagement />} />
          <Route path="audit-log" element={<AuditLog />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute role="ROLE_PROCUREMENT_OFFICER" />}>
        <Route path="/procurement" element={<ProcurementLayout />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<ProcurementDashboard />} />
          <Route path="new-forecast" element={<ForecastForm />} />
          <Route path="multi-project-forecast" element={<MultiProjectForecast />} />
          <Route path="async-forecast" element={<AsyncForecast />} />
          <Route path="what-if" element={<WhatIfSimulator />} />
          <Route path="planning" element={<ProcurementPlanning />} />
          <Route path="history" element={<ProcurementHistory />} />
          <Route path="alerts" element={<ShortageAlerts />} />
        </Route>
      </Route>

      <Route element={<ProtectedRoute role="ROLE_SITE_MANAGER" />}>
        <Route path="/site-manager" element={<SiteManagerLayout />}>
          <Route index element={<Navigate to="dashboard" replace />} />
          <Route path="dashboard" element={<SiteManagerDashboard />} />
          <Route path="warehouse-inventory" element={<WarehouseInventory />} />
          <Route path="material-receipt" element={<MaterialReceipt />} />
          <Route path="material-deployment" element={<MaterialDeployment />} />
          <Route path="procurement-recommendations" element={<ProcurementRecommendations />} />
          <Route path="material-movement-history" element={<MaterialMovementHistory />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
}
