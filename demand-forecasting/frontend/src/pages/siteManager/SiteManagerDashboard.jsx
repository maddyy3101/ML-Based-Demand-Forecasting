import KpiCard from "../../components/shared/KpiCard";
import { useApiData } from "../../hooks/useApiData";
import { inventoryApi } from "../../api/inventoryApi";
import HealthGaugeChart from "../../components/charts/HealthGaugeChart";
import MaterialStockBarChart from "../../components/charts/MaterialStockBarChart";
import MovementPieChart from "../../components/charts/MovementPieChart";
import MovementTimelineChart from "../../components/charts/MovementTimelineChart";

export default function SiteManagerDashboard() {
  const items = useApiData(() => inventoryApi.items(), []);
  const movements = useApiData(() => inventoryApi.movements(), []);
  const recommendations = useApiData(() => inventoryApi.recommendations(), []);

  const inventoryRows = items.data || [];
  const movementRows = movements.data || [];

  const criticalCount = inventoryRows.filter((i) => i.stockStatus === "CRITICAL").length;
  const overstockCount = inventoryRows.filter((i) => i.stockStatus === "OVERSTOCK").length;

  const barData = inventoryRows.map((i) => ({
    material: i.materialName,
    currentStock: i.currentStock,
    reorderThreshold: i.reorderThreshold,
  }));

  const pieData = [
    { name: "RECEIPT", value: movementRows.filter((m) => m.movementType === "RECEIPT").length },
    { name: "DEPLOYMENT", value: movementRows.filter((m) => m.movementType === "DEPLOYMENT").length },
  ];

  const timelineData = movementRows.slice(0, 30).map((m, idx) => ({
    day: idx + 1,
    receipt: m.movementType === "RECEIPT" ? m.quantity : 0,
    deployment: m.movementType === "DEPLOYMENT" ? -m.quantity : 0,
    net: m.movementType === "RECEIPT" ? m.quantity : -m.quantity,
  }));

  const recRows = recommendations.data || [];
  const high = recRows.filter((r) => r.urgencyLevel === "HIGH").length;
  const medium = recRows.filter((r) => r.urgencyLevel === "MEDIUM").length;
  const low = recRows.filter((r) => r.urgencyLevel === "LOW").length;

  const healthScore = Math.max(0, Math.min(100, 100 - criticalCount * 20 - overstockCount * 8));

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
        <KpiCard title="Total Material SKUs" value={inventoryRows.length} icon="📦" accentColor="var(--navy-light)" />
        <KpiCard title="Critical Stock Items" value={criticalCount} icon="🚨" accentColor="var(--red)" />
        <KpiCard title="Overstock Items" value={overstockCount} icon="🧊" accentColor="var(--purple)" />
        <KpiCard title="Movements Today" value={movementRows.length} icon="🔁" accentColor="var(--orange)" />
        <KpiCard title="Warehouse Health Score" value={healthScore} unit="%" icon="✅" accentColor="var(--green)" />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-12 gap-4">
        <div className="xl:col-span-3">
          <HealthGaugeChart score={healthScore} />
        </div>
        <div className="xl:col-span-5">
          <MaterialStockBarChart data={barData} />
        </div>
        <div className="xl:col-span-4">
          <MovementPieChart data={pieData} />
        </div>
      </div>

      <MovementTimelineChart data={timelineData} />

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <KpiCard title="HIGH Urgency" value={high} icon="⛔" accentColor="var(--red)" />
        <KpiCard title="MEDIUM Urgency" value={medium} icon="⚠️" accentColor="var(--amber)" />
        <KpiCard title="LOW Urgency" value={low} icon="🟢" accentColor="var(--green)" />
      </div>
    </div>
  );
}
