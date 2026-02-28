import KpiCard from "../../components/shared/KpiCard";
import LoadingSkeleton from "../../components/shared/LoadingSkeleton";
import MonthlyForecastVolumeChart from "../../components/charts/MonthlyForecastVolumeChart";
import UserActivityChart from "../../components/charts/UserActivityChart";
import ModelAccuracyTrendChart from "../../components/charts/ModelAccuracyTrendChart";
import DataTable from "../../components/shared/DataTable";
import { useApiData } from "../../hooks/useApiData";
import { adminApi } from "../../api/adminApi";
import { modelApi } from "../../api/modelApi";

const monthlyData = [
  { month: "Jan", count: 380 },
  { month: "Feb", count: 420 },
  { month: "Mar", count: 460 },
  { month: "Apr", count: 510 },
  { month: "May", count: 495 },
  { month: "Jun", count: 530 },
];

const userActivityData = [
  { day: "Mon", actions: 34 },
  { day: "Tue", actions: 41 },
  { day: "Wed", actions: 29 },
  { day: "Thu", actions: 48 },
  { day: "Fri", actions: 44 },
  { day: "Sat", actions: 20 },
  { day: "Sun", actions: 12 },
];

const accuracyData = [
  { version: "v1", MAE: 460, RMSE: 680 },
  { version: "v2", MAE: 390, RMSE: 590 },
  { version: "v3", MAE: 340, RMSE: 520 },
];

function statusColor(status) {
  const s = String(status || "").toLowerCase();
  if (s.includes("connected") || s === "ok" || s === "up") return "var(--green)";
  if (s.includes("degraded")) return "var(--amber)";
  return "var(--red)";
}

export default function AdminDashboard() {
  const health = useApiData(() => adminApi.systemHealth(), []);
  const model = useApiData(() => modelApi.active(), []);
  const flaskStatus = health.data?.flaskApiStatus || "unknown";
  const dbStatus = health.data?.dbStatus || "unknown";

  const perMaterialMetrics = model.data?.per_material_metrics || {};
  const metricRows = Object.entries(perMaterialMetrics).map(([material, metric]) => ({
    material,
    rmse: metric.RMSE?.toFixed?.(2) ?? metric.RMSE,
    mae: metric.MAE?.toFixed?.(2) ?? metric.MAE,
    mape: metric.MAPE?.toFixed?.(2) ?? metric.MAPE,
  }));

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <KpiCard title="Total Active Projects" value={400} icon="🏗️" accentColor="var(--navy-light)" />
        <KpiCard title="Forecasts This Month" value={health.data?.totalForecastsToday || 0} icon="📈" accentColor="var(--orange)" />
        <KpiCard title="Model RMSE" value={health.data?.modelRmse || 0} icon="🎯" accentColor="var(--amber)" />
        <KpiCard title="System Uptime" value={99.92} unit="%" icon="🛰️" accentColor="var(--green)" />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-12 gap-4">
        <div className="xl:col-span-5 pg-card p-4">
          <h3 className="font-display mb-2">System Health Panel</h3>
          {health.loading ? (
            <LoadingSkeleton rows={5} />
          ) : (
            <div className="space-y-2 text-sm">
              <div>
                Flask API: <span style={{ color: statusColor(flaskStatus) }}>●</span> {flaskStatus}
              </div>
              <div>
                Database: <span style={{ color: statusColor(dbStatus) }}>●</span> {dbStatus}
              </div>
              <div>Active Model: {health.data?.activeModelType}</div>
              <div>Last Retrained: {health.data?.lastRetrainedAt || "N/A"}</div>
              <div>Total Forecasts: {health.data?.totalForecastsAllTime || 0}</div>
            </div>
          )}
        </div>

        <div className="xl:col-span-7 pg-card p-4">
          <h3 className="font-display mb-2">Per-Material RMSE</h3>
          <DataTable
            loading={model.loading}
            columns={[
              { key: "material", label: "Material" },
              { key: "rmse", label: "RMSE" },
              { key: "mae", label: "MAE" },
              { key: "mape", label: "MAPE %" },
            ]}
            rows={metricRows}
            emptyText="Per-material metrics unavailable"
          />
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-12 gap-4">
        <div className="xl:col-span-8">
          <MonthlyForecastVolumeChart data={monthlyData} />
        </div>
        <div className="xl:col-span-4">
          <UserActivityChart data={userActivityData} />
        </div>
      </div>

      <ModelAccuracyTrendChart data={accuracyData} />
    </div>
  );
}
