import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

const COLORS = ["#22c55e", "#ef4444", "#a78bfa"];

export default function MovementPieChart({ data }) {
  return (
    <div className="pg-card p-4 h-80">
      <h3 className="font-display mb-3">Movement Mix</h3>
      <div style={{ height: 260 }}>
        <ResponsiveContainer width="100%" height="100%">
          <PieChart>
            <Pie data={data} dataKey="value" nameKey="name" innerRadius={50} outerRadius={90}>
              {data.map((entry, index) => (
                <Cell key={entry.name} fill={COLORS[index % COLORS.length]} />
              ))}
            </Pie>
            <Tooltip {...PG_CHART_CONFIG.tooltip} />
          </PieChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
