import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

export default function MaterialStockBarChart({ data }) {
  return (
    <div className="pg-card p-4 h-80">
      <h3 className="font-display mb-3">Material Stock vs Threshold</h3>
      <div style={{ height: 260 }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data}>
            <CartesianGrid {...PG_CHART_CONFIG.grid} />
            <XAxis dataKey="material" {...PG_CHART_CONFIG.axis} />
            <YAxis {...PG_CHART_CONFIG.axis} />
            <Tooltip {...PG_CHART_CONFIG.tooltip} />
            <Legend {...PG_CHART_CONFIG.legend} />
            <Bar dataKey="currentStock" fill={PG_CHART_CONFIG.series.primary} />
            <Bar dataKey="reorderThreshold" fill={PG_CHART_CONFIG.series.tertiary} />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
