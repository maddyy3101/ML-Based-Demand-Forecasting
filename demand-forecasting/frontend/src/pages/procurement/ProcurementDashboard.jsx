import { useMemo } from "react";
import KpiCard from "../../components/shared/KpiCard";
import DemandTrendByMaterialChart from "../../components/charts/DemandTrendByMaterialChart";
import MaterialDemandByPhaseChart from "../../components/charts/MaterialDemandByPhaseChart";
import DemandByTowerTypeChart from "../../components/charts/DemandByTowerTypeChart";
import DemandByRegionChart from "../../components/charts/DemandByRegionChart";
import FeatureImportanceBarChart from "../../components/charts/FeatureImportanceBarChart";
import ModelAccuracyTrendChart from "../../components/charts/ModelAccuracyTrendChart";
import { useApiData } from "../../hooks/useApiData";
import { forecastApi } from "../../api/forecastApi";

const phaseFallback = [
  { phase: "Planning", Cement: 3853, Conductor: 1028, Insulator: 771, Steel: 2055, Transformer: 25 },
  { phase: "Execution", Cement: 6311, Conductor: 1680, Insulator: 1263, Steel: 3359, Transformer: 42 },
  { phase: "Commissioning", Cement: 4272, Conductor: 1138, Insulator: 854, Steel: 2272, Transformer: 28 },
];

const towerFallback = [
  { towerType: "220kV", Cement: 3522, Conductor: 935, Insulator: 702, Steel: 1872, Transformer: 23 },
  { towerType: "400kV", Cement: 5193, Conductor: 1384, Insulator: 1040, Steel: 2765, Transformer: 34 },
  { towerType: "765kV", Cement: 6176, Conductor: 1648, Insulator: 1237, Steel: 3293, Transformer: 41 },
];

const trendData = [
  { month: "Jan-23", Cement: 4200, Conductor: 1100, Insulator: 820, Steel: 2100, Transformer: 28 },
  { month: "Jul-23", Cement: 5000, Conductor: 1320, Insulator: 940, Steel: 2500, Transformer: 31 },
  { month: "Jan-24", Cement: 5300, Conductor: 1410, Insulator: 1010, Steel: 2720, Transformer: 34 },
  { month: "Jul-24", Cement: 5600, Conductor: 1500, Insulator: 1100, Steel: 2910, Transformer: 36 },
  { month: "Jan-25", Cement: 5900, Conductor: 1590, Insulator: 1180, Steel: 3100, Transformer: 39 },
  { month: "Jul-25", Cement: 6200, Conductor: 1680, Insulator: 1260, Steel: 3320, Transformer: 43 },
];

const regionData = [
  { month: "Jan", East: 4200, North: 5000, South: 4700, West: 4400 },
  { month: "Feb", East: 4300, North: 5100, South: 4850, West: 4500 },
  { month: "Mar", East: 4400, North: 5300, South: 4920, West: 4600 },
  { month: "Apr", East: 4600, North: 5480, South: 5080, West: 4700 },
  { month: "May", East: 4700, North: 5600, South: 5200, West: 4800 },
  { month: "Jun", East: 4820, North: 5750, South: 5340, West: 4920 },
];

const accuracyTrend = [
  { version: "v1", MAE: 510, RMSE: 720 },
  { version: "v2", MAE: 420, RMSE: 620 },
  { version: "v3", MAE: 335, RMSE: 520 },
];

export default function ProcurementDashboard() {
  const features = useApiData(() => forecastApi.features(), []);
  const history = useApiData(() => forecastApi.history({ size: 100 }), []);

  const topMaterial = useMemo(() => {
    const rows = history.data?.content || [];
    const sum = rows.reduce((acc, row) => {
      const key = row.materialType || "Unknown";
      acc[key] = (acc[key] || 0) + (row.predictedQuantity || 0);
      return acc;
    }, {});
    return Object.entries(sum).sort((a, b) => b[1] - a[1])[0]?.[0] || "Cement";
  }, [history.data]);

  const featureRows = (features.data?.feature_importance || []).slice(0, 8);

  return (
    <div className="space-y-4">
      <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
        <KpiCard title="Forecasts This Month" value={history.data?.content?.length || 0} icon="📄" accentColor="var(--orange)" />
        <KpiCard title="Avg Predicted Qty" value={2760} icon="📈" accentColor="var(--navy-light)" />
        <KpiCard title="Top Material" value={topMaterial} icon="🏆" accentColor="var(--purple)" />
        <KpiCard title="Budget Coverage" value={87} unit="%" icon="💰" accentColor="var(--green)" />
        <KpiCard title="Pending Procurements" value={14} icon="⏳" accentColor="var(--amber)" />
      </div>

      <DemandTrendByMaterialChart data={trendData} />

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <MaterialDemandByPhaseChart data={phaseFallback} />
        <DemandByTowerTypeChart data={towerFallback} />
      </div>

      <div className="grid grid-cols-1 xl:grid-cols-2 gap-4">
        <DemandByRegionChart data={regionData} />
        <FeatureImportanceBarChart data={featureRows} />
      </div>

      <ModelAccuracyTrendChart data={accuracyTrend} />
    </div>
  );
}
