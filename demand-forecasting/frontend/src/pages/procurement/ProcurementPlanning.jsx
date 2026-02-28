import { useMemo, useState } from "react";
import { planningApi } from "../../api/planningApi";
import { inventoryApi } from "../../api/inventoryApi";
import { useApiData } from "../../hooks/useApiData";
import LoadingSkeleton from "../../components/shared/LoadingSkeleton";
import UrgencyBadge from "../../components/shared/UrgencyBadge";

const regions = ["North", "South", "East", "West"];

function parseError(err) {
  return err?.response?.data?.message || err?.message || "Request failed";
}

function toInr(value) {
  return Number(value || 0).toLocaleString("en-IN", { maximumFractionDigits: 0 });
}

export default function ProcurementPlanning() {
  const items = useApiData(() => inventoryApi.items(), []);
  const [replenishment, setReplenishment] = useState([]);
  const [purchasePlan, setPurchasePlan] = useState(null);
  const [exceptions, setExceptions] = useState([]);
  const [running, setRunning] = useState(false);
  const [error, setError] = useState("");
  const [form, setForm] = useState({
    region: "North",
    planMonth: "2026-03",
    planningHorizon: "30d",
    leadTimeDays: 35,
    serviceLevel: 0.95,
  });

  const scopedItems = useMemo(() => {
    const rows = items.data || [];
    if (!form.region) return rows;
    return rows.filter((row) => row.region === form.region);
  }, [items.data, form.region]);

  const runReplenishment = async () => {
    setRunning(true);
    setError("");
    try {
      const payload = {
        planningHorizon: form.planningHorizon,
        items: scopedItems.map((item) => ({
          inventoryId: item.id,
          leadTimeDays: Number(form.leadTimeDays),
          serviceLevel: Number(form.serviceLevel),
        })),
      };
      const [replenishmentData, exceptionData] = await Promise.all([
        planningApi.replenishment(payload),
        planningApi.exceptions(),
      ]);
      setReplenishment(replenishmentData.lines || []);
      setExceptions((exceptionData || []).filter((row) => !form.region || row.region === form.region));
    } catch (err) {
      setError(parseError(err));
    } finally {
      setRunning(false);
    }
  };

  const runPurchasePlan = async () => {
    setRunning(true);
    setError("");
    try {
      const data = await planningApi.purchasePlan({ planMonth: form.planMonth, region: form.region });
      setPurchasePlan(data);
    } catch (err) {
      setError(parseError(err));
    } finally {
      setRunning(false);
    }
  };

  const summary = useMemo(() => {
    const totalRecommendedQty = replenishment.reduce((acc, row) => acc + (row.recommendedOrderQty || 0), 0);
    const totalSafetyStock = replenishment.reduce((acc, row) => acc + (row.safetyStock || 0), 0);
    const highUrgency = (purchasePlan?.purchaseLines || []).filter((row) => row.urgency === "HIGH").length;
    const estimatedBudget = purchasePlan?.totalBudgetInr || 0;
    return { totalRecommendedQty, totalSafetyStock, highUrgency, estimatedBudget };
  }, [replenishment, purchasePlan]);

  return (
    <div className="space-y-4">
      <div className="pg-card pg-premium-card p-5">
        <h3 className="font-display text-xl">Supply Chain Planning Studio</h3>
        <p className="text-sm text-[var(--text-2)] mt-1">
          Convert forecasts into actionable replenishment and purchase strategy with exception visibility.
        </p>

        <div className="mt-4 grid md:grid-cols-5 gap-3">
          <select className="pg-input" value={form.region} onChange={(e) => setForm({ ...form, region: e.target.value })}>
            {regions.map((region) => <option key={region}>{region}</option>)}
          </select>
          <input className="pg-input" value={form.planMonth} onChange={(e) => setForm({ ...form, planMonth: e.target.value })} placeholder="YYYY-MM" />
          <input className="pg-input" value={form.planningHorizon} onChange={(e) => setForm({ ...form, planningHorizon: e.target.value })} placeholder="30d" />
          <input className="pg-input" type="number" min="1" value={form.leadTimeDays} onChange={(e) => setForm({ ...form, leadTimeDays: Number(e.target.value) })} placeholder="Lead Time Days" />
          <input className="pg-input" type="number" step="0.01" min="0.8" max="0.999" value={form.serviceLevel} onChange={(e) => setForm({ ...form, serviceLevel: Number(e.target.value) })} placeholder="Service Level" />
        </div>

        <div className="mt-4 flex flex-wrap gap-3">
          <button className="pg-btn" onClick={runReplenishment} disabled={running || !scopedItems.length}>
            {running ? "Running..." : "Compute Replenishment"}
          </button>
          <button className="pg-btn pg-btn-primary" onClick={runPurchasePlan} disabled={running}>
            {running ? "Running..." : "Generate Purchase Plan"}
          </button>
          <div className="text-xs text-[var(--text-2)] self-center">
            Inventory scope: {scopedItems.length} items in {form.region}
          </div>
        </div>

        {error ? <div className="mt-3 text-sm" style={{ color: "var(--red)" }}>{error}</div> : null}
      </div>

      <div className="grid md:grid-cols-4 gap-3">
        <div className="pg-card p-4">
          <div className="text-[11px] text-[var(--text-2)]">Recommended Order Qty</div>
          <div className="text-2xl font-display mt-1" style={{ color: "var(--orange)" }}>{summary.totalRecommendedQty}</div>
        </div>
        <div className="pg-card p-4">
          <div className="text-[11px] text-[var(--text-2)]">Total Safety Stock</div>
          <div className="text-2xl font-display mt-1">{summary.totalSafetyStock}</div>
        </div>
        <div className="pg-card p-4">
          <div className="text-[11px] text-[var(--text-2)]">High Urgency Lines</div>
          <div className="text-2xl font-display mt-1" style={{ color: "var(--red)" }}>{summary.highUrgency}</div>
        </div>
        <div className="pg-card p-4">
          <div className="text-[11px] text-[var(--text-2)]">Estimated Budget</div>
          <div className="text-2xl font-display mt-1">Rs {toInr(summary.estimatedBudget)}</div>
        </div>
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-12 gap-4">
        <div className="xl:col-span-7 pg-card p-4">
          <h4 className="font-display text-lg">Replenishment Signals</h4>
          <div className="mt-3 space-y-2">
            {running ? <LoadingSkeleton rows={6} /> : null}
            {!running && replenishment.map((line) => (
              <div key={line.inventoryId} className="pg-card p-3">
                <div className="flex items-center justify-between">
                  <div className="font-medium">{line.materialType}</div>
                  <div className="text-xs font-mono">{line.inventoryId.slice(0, 8)}...</div>
                </div>
                <div className="mt-2 grid md:grid-cols-4 gap-2 text-sm">
                  <div>Demand Variability: <b>{line.demandVariability}</b></div>
                  <div>Safety Stock: <b>{line.safetyStock}</b></div>
                  <div>Reorder Point: <b>{line.reorderPoint}</b></div>
                  <div>Recommended Qty: <b>{line.recommendedOrderQty}</b></div>
                </div>
              </div>
            ))}
            {!running && !replenishment.length ? (
              <div className="text-sm text-[var(--text-2)]">Run replenishment to view calculated lines.</div>
            ) : null}
          </div>
        </div>

        <div className="xl:col-span-5 pg-card p-4">
          <h4 className="font-display text-lg">Active Planning Exceptions</h4>
          <div className="mt-3 space-y-2">
            {exceptions.map((row, index) => (
              <div key={`${row.materialType}-${row.region}-${index}`} className="pg-card p-3">
                <div className="flex justify-between">
                  <div className="font-medium">{row.materialType} - {row.region}</div>
                  <UrgencyBadge urgency={row.urgency} />
                </div>
                <div className="text-xs text-[var(--text-2)] mt-1">Stock Status: {row.stockStatus}</div>
                <div className="text-sm mt-1">{row.message}</div>
              </div>
            ))}
            {!exceptions.length ? (
              <div className="text-sm text-[var(--text-2)]">No planning exceptions in current scope.</div>
            ) : null}
          </div>
        </div>
      </div>

      <div className="pg-card pg-premium-card p-4">
        <h4 className="font-display text-lg">Purchase Plan Output ({purchasePlan?.planMonth || form.planMonth})</h4>
        <div className="mt-3 grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3">
          {(purchasePlan?.purchaseLines || []).map((line, index) => (
            <div key={`${line.materialType}-${line.region}-${index}`} className="pg-card p-3">
              <div className="flex justify-between items-center">
                <div className="font-medium">{line.materialType}</div>
                <UrgencyBadge urgency={line.urgency} />
              </div>
              <div className="text-sm mt-2">Region: {line.region}</div>
              <div className="text-sm">Recommended Qty: <b>{line.recommendedQty}</b> {line.unitLabel}</div>
              <div className="text-sm">Estimated Cost: <b>Rs {toInr(line.estimatedCostInr)}</b></div>
            </div>
          ))}
          {purchasePlan && !(purchasePlan.purchaseLines || []).length ? (
            <div className="text-sm text-[var(--text-2)]">No purchase lines returned for this plan window.</div>
          ) : null}
          {!purchasePlan ? (
            <div className="text-sm text-[var(--text-2)]">Generate purchase plan to view budgeted procurement lines.</div>
          ) : null}
        </div>
      </div>
    </div>
  );
}
