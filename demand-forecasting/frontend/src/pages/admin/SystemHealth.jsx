import { useApiData } from "../../hooks/useApiData";
import { adminApi } from "../../api/adminApi";
import LoadingSkeleton from "../../components/shared/LoadingSkeleton";

function statusColor(status) {
  const s = String(status || "").toLowerCase();
  if (s.includes("connected") || s === "ok" || s === "up") return "var(--green)";
  if (s.includes("degraded")) return "var(--amber)";
  return "var(--red)";
}

function StatusBadge({ label, status }) {
  return (
    <div className="pg-card p-3">
      <div className="text-xs text-[var(--text-2)]">{label}</div>
      <div className="mt-1 font-medium">
        <span style={{ color: statusColor(status) }}>●</span> {status || "unknown"}
      </div>
    </div>
  );
}

export default function SystemHealth() {
  const health = useApiData(() => adminApi.systemHealth(), []);

  if (health.loading) {
    return (
      <div className="pg-card p-4">
        <h3 className="font-display text-xl mb-3">System Health</h3>
        <LoadingSkeleton rows={6} />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="pg-card p-4">
        <h3 className="font-display text-xl mb-3">System Health</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
          <StatusBadge label="Flask API" status={health.data?.flaskApiStatus} />
          <StatusBadge label="Database" status={health.data?.dbStatus} />
          <StatusBadge label="Active Model" status={health.data?.activeModelType} />
        </div>

        <div className="mt-4 grid grid-cols-1 md:grid-cols-2 gap-3 text-sm">
          <div className="pg-card p-3">Model RMSE: {health.data?.modelRmse ?? "N/A"}</div>
          <div className="pg-card p-3">Model MAE: {health.data?.modelMae ?? "N/A"}</div>
          <div className="pg-card p-3">Forecasts Today: {health.data?.totalForecastsToday ?? 0}</div>
          <div className="pg-card p-3">Forecasts All Time: {health.data?.totalForecastsAllTime ?? 0}</div>
          <div className="pg-card p-3 md:col-span-2">
            Last Retrained: {health.data?.lastRetrainedAt || "N/A"}
          </div>
        </div>
      </div>

      <details className="pg-card p-4">
        <summary className="cursor-pointer text-sm text-[var(--text-2)]">Raw health payload</summary>
        <pre className="text-xs mt-3 overflow-x-auto">{JSON.stringify(health.data || {}, null, 2)}</pre>
      </details>
    </div>
  );
}
