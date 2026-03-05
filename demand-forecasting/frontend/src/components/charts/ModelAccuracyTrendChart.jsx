import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

export default function ModelAccuracyTrendChart({ data }) {
  return (
    <div className="pg-card p-4 h-80">
      <h3 className="font-display mb-3">Model Accuracy Trend</h3>
      <div style={{ height: 260 }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data}>
            <CartesianGrid {...PG_CHART_CONFIG.grid} />
            <XAxis dataKey="version" {...PG_CHART_CONFIG.axis} />
            <YAxis {...PG_CHART_CONFIG.axis} />
            <Tooltip {...PG_CHART_CONFIG.tooltip} />
            <Legend {...PG_CHART_CONFIG.legend} />
            <Line type="monotone" dataKey="MAE" stroke={PG_CHART_CONFIG.series.tertiary} />
            <Line type="monotone" dataKey="RMSE" stroke={PG_CHART_CONFIG.series.primary} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
