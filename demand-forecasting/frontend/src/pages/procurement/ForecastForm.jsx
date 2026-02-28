import { useMemo, useState } from "react";
import { forecastApi } from "../../api/forecastApi";
import ProcurementDecisionBadge from "../../components/shared/ProcurementDecisionBadge";
import FluidSlider from "../../components/shared/FluidSlider";
import { mapStateToRegion, states } from "../../utils/stateRegionMapper";
import { materialUnitMap } from "../../utils/materialMeta";

const phases = ["Planning", "Execution", "Commissioning"];
const terrains = ["Coastal", "Hilly", "Plain"];
const towers = ["220kV", "400kV", "765kV"];
const substations = ["AIS", "GIS"];
const materials = ["Cement", "Conductor", "Insulator", "Steel", "Transformer"];

const initial = {
  projectId: "P101",
  projectPhase: "Execution",
  state: "Rajasthan",
  region: "North",
  terrainType: "Plain",
  towerType: "400kV",
  substationType: "AIS",
  transmissionLengthKm: 220,
  budgetCrore: 1500,
  materialType: "Conductor",
  leadTimeDays: 35,
  taxPercentage: 18,
  transportationCost: 14500,
  historicalConsumption: 850,
  forecastMonth: 7,
  forecastYear: 2025,
};

export default function ForecastForm() {
  const [form, setForm] = useState(initial);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  const unit = useMemo(() => materialUnitMap[form.materialType], [form.materialType]);

  const setField = (key, value) => {
    if (key === "state") {
      setForm((prev) => ({ ...prev, state: value, region: mapStateToRegion(value) }));
      return;
    }
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const submit = async (event) => {
    event.preventDefault();
    setError("");
    try {
      const response = await forecastApi.create(form);
      setResult(response);
    } catch (err) {
      setError(err?.response?.data?.message || "Forecast failed");
    }
  };

  return (
    <div className="space-y-4">
      <form className="pg-card p-4 grid md:grid-cols-2 gap-3" onSubmit={submit}>
        <div className="md:col-span-2">
          <h3 className="font-display text-xl">Material Demand Forecast</h3>
          <p className="text-sm text-[var(--text-2)]">Generate AI-powered procurement quantity for POWERGRID project</p>
        </div>

        <label>Project ID<input className="pg-input" value={form.projectId} onChange={(e) => setField("projectId", e.target.value)} placeholder="P1, P101, etc." /></label>
        <label>Project Phase<select className="pg-input" value={form.projectPhase} onChange={(e) => setField("projectPhase", e.target.value)}>{phases.map((v) => <option key={v}>{v}</option>)}</select></label>
        <label>State<select className="pg-input" value={form.state} onChange={(e) => setField("state", e.target.value)}>{states.map((s) => <option key={s}>{s}</option>)}</select></label>
        <label>Region<input className="pg-input" value={form.region} readOnly /></label>
        <label>Terrain Type<select className="pg-input" value={form.terrainType} onChange={(e) => setField("terrainType", e.target.value)}>{terrains.map((v) => <option key={v}>{v}</option>)}</select></label>
        <label>Tower Type<select className="pg-input" value={form.towerType} onChange={(e) => setField("towerType", e.target.value)}>{towers.map((v) => <option key={v}>{v}</option>)}</select></label>
        <label>Substation Type<select className="pg-input" value={form.substationType} onChange={(e) => setField("substationType", e.target.value)}>{substations.map((v) => <option key={v}>{v}</option>)}</select></label>
        <label>Material Type<select className="pg-input" value={form.materialType} onChange={(e) => setField("materialType", e.target.value)}>{materials.map((v) => <option key={v}>{v}</option>)}</select></label>

        <FluidSlider
          label="Transmission Length KM"
          min={50}
          max={298}
          step={1}
          snapStep={2}
          tickCount={7}
          tickFormatter={(v) => `${v}`}
          value={form.transmissionLengthKm}
          unit="KM"
          onChange={(v) => setField("transmissionLengthKm", v)}
        />
        <label>Budget (INR Crore)<input className="pg-input" type="number" min={302} max={1997} value={form.budgetCrore} onChange={(e) => setField("budgetCrore", Number(e.target.value))} /></label>
        <label>Lead Time (Days)<input className="pg-input" type="number" min={15} max={59} value={form.leadTimeDays} onChange={(e) => setField("leadTimeDays", Number(e.target.value))} /></label>
        <label>Tax Percentage<input className="pg-input" type="number" min={12} max={22} step="0.1" value={form.taxPercentage} onChange={(e) => setField("taxPercentage", Number(e.target.value))} /></label>
        <label>Transportation Cost (INR)<input className="pg-input" type="number" value={form.transportationCost} onChange={(e) => setField("transportationCost", Number(e.target.value))} /></label>
        <label>Historical Consumption ({unit})<input className="pg-input" type="number" value={form.historicalConsumption} onChange={(e) => setField("historicalConsumption", Number(e.target.value))} /></label>
        <label>Forecast Month<select className="pg-input" value={form.forecastMonth} onChange={(e) => setField("forecastMonth", Number(e.target.value))}>{Array.from({ length: 12 }).map((_, i) => <option key={i + 1} value={i + 1}>{i + 1}</option>)}</select></label>
        <label>Forecast Year<select className="pg-input" value={form.forecastYear} onChange={(e) => setField("forecastYear", Number(e.target.value))}>{Array.from({ length: 8 }).map((_, i) => <option key={2023 + i} value={2023 + i}>{2023 + i}</option>)}</select></label>

        <button className="pg-btn pg-btn-primary md:col-span-2">Generate Forecast</button>
      </form>

      {error ? <div className="text-sm" style={{ color: "var(--red)" }}>{error}</div> : null}

      {result ? (
        <div className="pg-card p-4 grid md:grid-cols-2 gap-4">
          <div>
            <div className="text-sm text-[var(--text-2)]">Predicted Quantity</div>
            <div className="font-display text-4xl" style={{ color: "var(--orange)" }}>
              {result.predictedQuantity}
            </div>
            <div className="text-sm text-[var(--text-2)] mt-1">
              {result.materialType} • {result.unitLabel}
            </div>
          </div>
          <div>
            <ProcurementDecisionBadge decision={result.procurementDecision} />
            <p className="mt-2 text-sm">{result.decisionMessage}</p>
            <div className="text-xs text-[var(--text-2)] mt-2">Model: {result.modelType} • Confidence: {result.modelConfidence}</div>
            <div className="text-xs font-mono mt-2">Request ID: {result.requestId}</div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
