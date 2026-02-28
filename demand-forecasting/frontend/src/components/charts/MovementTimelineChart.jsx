import {
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

export default function MovementTimelineChart({ data }) {
  return (
    <div className="pg-card p-4 h-80">
      <h3 className="font-display mb-3">Deployment Timeline</h3>
      <div style={{ height: 260 }}>
        <ResponsiveContainer width="100%" height="100%">
          <ComposedChart data={data}>
            <CartesianGrid {...PG_CHART_CONFIG.grid} />
            <XAxis dataKey="day" {...PG_CHART_CONFIG.axis} />
            <YAxis {...PG_CHART_CONFIG.axis} />
            <Tooltip {...PG_CHART_CONFIG.tooltip} />
            <Bar dataKey="receipt" fill="#22c55e" />
            <Bar dataKey="deployment" fill="#ef4444" />
            <Line type="monotone" dataKey="net" stroke="#4f8ef7" />
          </ComposedChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
