import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

export default function DemandByTowerTypeChart({ data }) {
  return (
    <div className="pg-card p-4 h-80">
      <h3 className="font-display mb-3">Demand by Tower Type</h3>
      <div style={{ height: 260 }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data}>
            <CartesianGrid {...PG_CHART_CONFIG.grid} />
            <XAxis dataKey="towerType" {...PG_CHART_CONFIG.axis} />
            <YAxis {...PG_CHART_CONFIG.axis} />
            <Tooltip {...PG_CHART_CONFIG.tooltip} />
            <Legend {...PG_CHART_CONFIG.legend} />
            <Bar dataKey="Cement" fill={PG_CHART_CONFIG.materialColors.Cement} />
            <Bar dataKey="Conductor" fill={PG_CHART_CONFIG.materialColors.Conductor} />
            <Bar dataKey="Insulator" fill={PG_CHART_CONFIG.materialColors.Insulator} />
            <Bar dataKey="Steel" fill={PG_CHART_CONFIG.materialColors.Steel} />
            <Bar dataKey="Transformer" fill={PG_CHART_CONFIG.materialColors.Transformer} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
