import { useState } from "react";
import { modelApi } from "../../api/modelApi";
import { useApiData } from "../../hooks/useApiData";
import DataTable from "../../components/shared/DataTable";

export default function ModelManagement() {
  const active = useApiData(() => modelApi.active(), []);
  const [version, setVersion] = useState("XGBoost-Tuned");
  const [message, setMessage] = useState("");

  const activate = async () => {
    const data = await modelApi.activate(version, { reason: "Controlled rollout" });
    setMessage(`Model ${data.activeVersion} activated at ${data.activatedAt}`);
  };

  const perMaterial = Object.entries(active.data?.per_material_metrics || {}).map(([material, stats]) => ({
    id: material,
    material,
    MAE: stats.MAE,
    RMSE: stats.RMSE,
    MAPE: stats.MAPE,
  }));

  return (
    <div className="space-y-4">
      <div className="pg-card p-4">
        <div className="text-sm text-[var(--text-2)]">Active model</div>
        <div className="font-display text-2xl mt-1">{active.data?.model_type || "-"}</div>
        <div className="text-sm mt-1">Version: {active.data?.activeVersion || "-"}</div>
      </div>

      <div className="pg-card p-4 flex items-end gap-3">
        <label className="flex-1">
          <span className="text-xs text-[var(--text-2)]">Activate Version</span>
          <input className="pg-input mt-1" value={version} onChange={(e) => setVersion(e.target.value)} />
        </label>
        <button className="pg-btn pg-btn-primary" onClick={activate}>
          Activate
        </button>
      </div>

      {message ? <div className="text-sm text-[var(--green)]">{message}</div> : null}

      <DataTable
        loading={active.loading}
        columns={[
          { key: "material", label: "Material" },
          { key: "MAE", label: "MAE" },
          { key: "RMSE", label: "RMSE" },
          { key: "MAPE", label: "MAPE" },
        ]}
        rows={perMaterial}
      />
    </div>
  );
}
