import { useMemo, useState } from "react";
import { forecastApi } from "../../api/forecastApi";
import FluidSlider from "../../components/shared/FluidSlider";
import { materialUnitMap } from "../../utils/materialMeta";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

const basePayload = {
  projectId: "P205",
  projectPhase: "Execution",
  state: "Rajasthan",
  region: "North",
  terrainType: "Plain",
  towerType: "400kV",
  substationType: "AIS",
  transmissionLengthKm: 180,
  budgetCrore: 1100,
  materialType: "Conductor",
  leadTimeDays: 35,
  taxPercentage: 18,
  transportationCost: 14000,
  historicalConsumption: 820,
  forecastMonth: 8,
  forecastYear: 2025,
};

export default function WhatIfSimulator() {
  const [form, setForm] = useState(basePayload);
  const [baseline, setBaseline] = useState(null);
  const [simulated, setSimulated] = useState(null);

  const run = async () => {
    const [base, sim] = await Promise.all([
      forecastApi.create(basePayload),
      forecastApi.whatIf(form),
    ]);
    setBaseline(base.predictedQuantity || 0);
    setSimulated(sim?.simulated?.quantity_required || 0);
  };

  const delta = (simulated || 0) - (baseline || 0);
  const deltaPct = baseline ? ((delta / baseline) * 100).toFixed(1) : "0.0";

  const comparisonData = useMemo(
    () => [
      { name: form.materialType, Baseline: baseline || 0, Simulated: simulated || 0 },
    ],
    [form.materialType, baseline, simulated]
  );

  return (
    <div className="grid xl:grid-cols-2 gap-4">
      <div className="pg-card p-4 space-y-3">
        <h3 className="font-display text-xl">Adjust Project Parameters</h3>
        <FluidSlider
          label="Budget (INR Crore)"
          min={302}
          max={1997}
          step={1}
          snapStep={25}
          tickCount={7}
          value={form.budgetCrore}
          onChange={(v) => setForm({ ...form, budgetCrore: v })}
        />
        <FluidSlider
          label="Transmission Length (KM)"
          min={50}
          max={298}
          step={1}
          snapStep={2}
          tickCount={7}
          value={form.transmissionLengthKm}
          unit="KM"
          onChange={(v) => setForm({ ...form, transmissionLengthKm: v })}
        />
        <FluidSlider
          label="Historical Consumption"
          min={8}
          max={1800}
          step={1}
          snapStep={10}
          tickCount={7}
          value={form.historicalConsumption}
          onChange={(v) => setForm({ ...form, historicalConsumption: v })}
        />
        <FluidSlider
          label="Transportation Cost (INR)"
          min={2608}
          max={30627}
          step={1}
          snapStep={250}
          tickCount={7}
          value={form.transportationCost}
          onChange={(v) => setForm({ ...form, transportationCost: v })}
        />
        <label>Project Phase<select className="pg-input" value={form.projectPhase} onChange={(e) => setForm({ ...form, projectPhase: e.target.value })}><option>Planning</option><option>Execution</option><option>Commissioning</option></select></label>
        <label>Tower Type<select className="pg-input" value={form.towerType} onChange={(e) => setForm({ ...form, towerType: e.target.value })}><option>220kV</option><option>400kV</option><option>765kV</option></select></label>
        <label>Material Type<select className="pg-input" value={form.materialType} onChange={(e) => setForm({ ...form, materialType: e.target.value })}><option>Cement</option><option>Conductor</option><option>Insulator</option><option>Steel</option><option>Transformer</option></select></label>
        <button className="pg-btn pg-btn-primary" onClick={run}>Run Simulation</button>
      </div>

      <div className="pg-card p-4 space-y-3">
        <h3 className="font-display text-xl">Simulated Demand Forecast</h3>
        <div className="text-sm text-[var(--text-2)]">
          {baseline === null ? "Run simulation" : `${delta >= 0 ? "+" : ""}${delta} units vs baseline (${deltaPct}%)`}
        </div>
        <div className="text-lg">
          Baseline: <b>{baseline || 0}</b> {materialUnitMap[form.materialType]}
        </div>
        <div className="text-lg">
          Simulated: <b>{simulated || 0}</b> {materialUnitMap[form.materialType]}
        </div>
        <div style={{ height: 260 }}>
          <ResponsiveContainer width="100%" height="100%">
            <BarChart data={comparisonData}>
              <CartesianGrid {...PG_CHART_CONFIG.grid} />
              <XAxis dataKey="name" {...PG_CHART_CONFIG.axis} />
              <YAxis {...PG_CHART_CONFIG.axis} />
              <Tooltip {...PG_CHART_CONFIG.tooltip} />
              <Bar dataKey="Baseline" fill="#4f8ef7" />
              <Bar dataKey="Simulated" fill="#f47c20" />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
