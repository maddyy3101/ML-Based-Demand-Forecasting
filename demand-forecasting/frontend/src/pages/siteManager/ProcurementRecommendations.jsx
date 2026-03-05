import { useMemo, useState } from "react";
import { useApiData } from "../../hooks/useApiData";
import { inventoryApi } from "../../api/inventoryApi";
import UrgencyBadge from "../../components/shared/UrgencyBadge";
import StockStatusBadge from "../../components/shared/StockStatusBadge";
import LoadingSkeleton from "../../components/shared/LoadingSkeleton";

function parseError(err) {
  return err?.response?.data?.message || err?.message || "Action failed";
}

function cardStyle(item) {
  if (item.stockStatus === "OVERSTOCK") return { borderColor: "rgba(202, 211, 223, 0.48)", background: "rgba(202, 211, 223, 0.12)" };
  if (item.urgencyLevel === "HIGH") return { borderColor: "rgba(245, 247, 250, 0.5)", background: "rgba(245, 247, 250, 0.12)" };
  if (item.urgencyLevel === "MEDIUM") return { borderColor: "rgba(169, 178, 191, 0.5)", background: "rgba(169, 178, 191, 0.12)" };
  return { borderColor: "rgba(122, 132, 147, 0.5)", background: "rgba(122, 132, 147, 0.12)" };
}

function meterColor(item) {
  if (item.stockStatus === "CRITICAL") return "var(--red)";
  if (item.stockStatus === "LOW") return "var(--amber)";
  if (item.stockStatus === "OVERSTOCK") return "var(--purple)";
  return "var(--green)";
}

function stockFill(item) {
  const denom = Math.max(item.reorderThreshold * 2, 1);
  return Math.max(4, Math.min(100, (item.currentStock / denom) * 100));
}

export default function ProcurementRecommendations() {
  const recs = useApiData(() => inventoryApi.recommendations(), []);
  const actions = useApiData(() => inventoryApi.recommendationActions(), []);
  const [busyById, setBusyById] = useState({});
  const [hiddenById, setHiddenById] = useState({});
  const [feedback, setFeedback] = useState({ tone: "", text: "" });

  const actionByInventory = useMemo(() => {
    const map = {};
    for (const row of actions.data || []) {
      if (!map[row.inventoryId]) {
        map[row.inventoryId] = row;
      }
    }
    return map;
  }, [actions.data]);

  const rows = (recs.data || []).filter((row) => !hiddenById[row.inventoryId]);

  const pushAction = (action) => {
    actions.setData((prev) => [action, ...(prev || [])]);
  };

  const runWithBusy = async (inventoryId, task) => {
    setBusyById((prev) => ({ ...prev, [inventoryId]: true }));
    try {
      await task();
    } finally {
      setBusyById((prev) => ({ ...prev, [inventoryId]: false }));
    }
  };

  const onRaise = async (item) => {
    setFeedback({ tone: "", text: "" });
    await runWithBusy(item.inventoryId, async () => {
      try {
        const saved = await inventoryApi.raiseRecommendation(item.inventoryId, {
          recommendedOrderQty: item.recommendedOrderQty,
          note: `Auto-raised from recommendation panel for ${item.region} ${item.materialType}`,
        });
        pushAction(saved);
        setFeedback({
          tone: "success",
          text: `${item.materialName}: procurement request raised for ${saved.recommendedOrderQty} ${item.unitLabel}.`,
        });
      } catch (err) {
        setFeedback({ tone: "error", text: parseError(err) });
      }
    });
  };

  const onDismiss = async (item) => {
    setFeedback({ tone: "", text: "" });
    await runWithBusy(item.inventoryId, async () => {
      try {
        const saved = await inventoryApi.dismissRecommendation(item.inventoryId, {
          note: `Dismissed from recommendation panel`,
        });
        pushAction(saved);
        setHiddenById((prev) => ({ ...prev, [item.inventoryId]: true }));
        setFeedback({
          tone: "success",
          text: `${item.materialName} recommendation dismissed.`,
        });
      } catch (err) {
        setFeedback({ tone: "error", text: parseError(err) });
      }
    });
  };

  if (recs.loading) {
    return (
      <div className="pg-card p-4">
        <LoadingSkeleton rows={8} />
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {feedback.text ? (
        <div
          className="pg-card p-3 text-sm"
          style={{ color: feedback.tone === "error" ? "var(--red)" : "var(--green)" }}
        >
          {feedback.text}
        </div>
      ) : null}

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
      {rows.map((item) => (
        <div key={item.inventoryId} className="pg-card pg-premium-card p-4 transition-all duration-300 hover:-translate-y-1" style={cardStyle(item)}>
          <div className="flex items-center justify-between">
            <UrgencyBadge urgency={item.urgencyLevel} />
            <div className="text-xs">Days Until Stockout: {item.daysUntilStockout.toFixed(1)} days</div>
          </div>

          <div className="mt-2 font-display text-lg">
            {item.materialName} - {item.region} Region ({item.towerType})
          </div>
          <div className="text-xs text-[var(--text-2)] mt-1">
            Inventory ID: <span className="font-mono">{item.inventoryId.slice(0, 8)}...</span>
          </div>

          <div className="mt-4">
            <div className="flex justify-between text-xs text-[var(--text-2)]">
              <span>Current Stock</span>
              <span>{item.currentStock} {item.unitLabel}</span>
            </div>
            <div className="h-2 rounded-full mt-1 bg-[rgba(18,20,26,0.7)] border border-[var(--border)] overflow-hidden">
              <div
                className="h-full transition-all duration-500"
                style={{ width: `${stockFill(item)}%`, background: meterColor(item) }}
              />
            </div>
            <div className="text-xs text-[var(--text-2)] mt-1">Reorder at {item.reorderThreshold} {item.unitLabel}</div>
          </div>

          <div className="mt-4 grid grid-cols-2 gap-2 text-sm">
            <div className="pg-card p-2">
              <div className="text-[11px] text-[var(--text-2)]">Predicted Demand</div>
              <div className="font-semibold">{item.predictedDemand}</div>
            </div>
            <div className="pg-card p-2">
              <div className="text-[11px] text-[var(--text-2)]">Recommended Order</div>
              <div className="font-semibold">{item.recommendedOrderQty}</div>
            </div>
            <div className="pg-card p-2">
              <div className="text-[11px] text-[var(--text-2)]">Avg Daily Deployment</div>
              <div className="font-semibold">{item.avgDailyDeployment.toFixed(1)}/day</div>
            </div>
            <div className="pg-card p-2">
              <div className="text-[11px] text-[var(--text-2)]">Urgency Reason</div>
              <div className="font-semibold text-xs">{item.urgencyReason}</div>
            </div>
          </div>

          <div className="mt-3 flex items-center justify-between gap-2">
            <StockStatusBadge status={item.stockStatus} />
            {actionByInventory[item.inventoryId]?.actionType === "RAISE_REQUEST" ? (
              <span className="pg-chip" style={{ background: "var(--orange-dim)", color: "var(--orange)" }}>
                Request Raised
              </span>
            ) : null}
          </div>

          <div className="mt-3 grid grid-cols-2 gap-2">
            <button
              className="pg-btn pg-btn-primary"
              disabled={busyById[item.inventoryId]}
              onClick={() => onRaise(item)}
            >
              {busyById[item.inventoryId] ? "Saving..." : "Raise Procurement Request"}
            </button>
            <button
              className="pg-btn"
              disabled={busyById[item.inventoryId]}
              onClick={() => onDismiss(item)}
            >
              {busyById[item.inventoryId] ? "Saving..." : "Dismiss"}
            </button>
          </div>

          {actionByInventory[item.inventoryId] ? (
            <div className="mt-3 text-xs text-[var(--text-2)]">
              Last action: {actionByInventory[item.inventoryId].actionType} by {actionByInventory[item.inventoryId].actedBy}
            </div>
          ) : null}
        </div>
      ))}

      {!rows.length ? (
        <div className="pg-card p-6 text-sm text-[var(--text-2)]">
          No active recommendations. All current recommendations are addressed or dismissed.
        </div>
      ) : null}
      </div>
    </div>
  );
}
