import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

export default function FeatureImportanceBarChart({ data }) {
  return (
    <div className="pg-card p-4 h-80">
      <h3 className="font-display mb-3">Feature Importance</h3>
      <div style={{ height: 260 }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} layout="vertical" margin={{ left: 18 }}>
            <CartesianGrid {...PG_CHART_CONFIG.grid} />
            <XAxis type="number" {...PG_CHART_CONFIG.axis} />
            <YAxis type="category" dataKey="feature" width={150} {...PG_CHART_CONFIG.axis} />
            <Tooltip {...PG_CHART_CONFIG.tooltip} />
            <Bar dataKey="importance" fill="#4f8ef7" />
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
