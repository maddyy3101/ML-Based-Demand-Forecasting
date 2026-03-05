import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

export default function UserActivityChart({ data }) {
  return (
    <div className="pg-card p-4 h-80">
      <h3 className="font-display mb-3">User Activity</h3>
      <div style={{ height: 260 }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data}>
            <CartesianGrid {...PG_CHART_CONFIG.grid} />
            <XAxis dataKey="day" {...PG_CHART_CONFIG.axis} />
            <YAxis {...PG_CHART_CONFIG.axis} />
            <Tooltip {...PG_CHART_CONFIG.tooltip} />
            <Line type="monotone" dataKey="actions" stroke={PG_CHART_CONFIG.series.secondary} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
