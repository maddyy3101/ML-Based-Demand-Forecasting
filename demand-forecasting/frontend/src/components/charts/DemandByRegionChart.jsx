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
            <Area type="monotone" dataKey="East" stroke={PG_CHART_CONFIG.regionColors.East} fill={`${PG_CHART_CONFIG.regionColors.East}4d`} />
            <Area type="monotone" dataKey="North" stroke={PG_CHART_CONFIG.regionColors.North} fill={`${PG_CHART_CONFIG.regionColors.North}4d`} />
            <Area type="monotone" dataKey="South" stroke={PG_CHART_CONFIG.regionColors.South} fill={`${PG_CHART_CONFIG.regionColors.South}4d`} />
            <Area type="monotone" dataKey="West" stroke={PG_CHART_CONFIG.regionColors.West} fill={`${PG_CHART_CONFIG.regionColors.West}4d`} />
          </AreaChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
