import { Area, AreaChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

export default function DemandByRegionChart({ data }) {
  return (
    <div className="pg-card p-4 h-80">
      <h3 className="font-display mb-3">Demand by Region</h3>
      <div style={{ height: 260 }}>
        <ResponsiveContainer width="100%" height="100%">
          <AreaChart data={data}>
            <CartesianGrid {...PG_CHART_CONFIG.grid} />
            <XAxis dataKey="month" {...PG_CHART_CONFIG.axis} />
            <YAxis {...PG_CHART_CONFIG.axis} />
            <Tooltip {...PG_CHART_CONFIG.tooltip} />
            <Legend {...PG_CHART_CONFIG.legend} />
            <Area type="monotone" dataKey="East" stroke="#4f8ef7" fill="#4f8ef744" />
            <Area type="monotone" dataKey="North" stroke="#f47c20" fill="#f47c2044" />
            <Area type="monotone" dataKey="South" stroke="#22c55e" fill="#22c55e44" />
            <Area type="monotone" dataKey="West" stroke="#a78bfa" fill="#a78bfa44" />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
