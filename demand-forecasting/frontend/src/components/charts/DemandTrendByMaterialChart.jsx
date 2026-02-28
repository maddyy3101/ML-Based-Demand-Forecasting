import {
  CartesianGrid,
  Legend,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import { PG_CHART_CONFIG } from "../../utils/chartConfig";

export default function DemandTrendByMaterialChart({ data }) {
  return (
    <div className="pg-card p-4 h-80">
      <h3 className="font-display mb-3">Demand Trend by Material</h3>
      <div style={{ height: 260 }}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data}>
            <CartesianGrid {...PG_CHART_CONFIG.grid} />
            <XAxis dataKey="month" {...PG_CHART_CONFIG.axis} />
            <YAxis yAxisId="high" {...PG_CHART_CONFIG.axis} scale="linear" />
            <YAxis yAxisId="low" orientation="right" {...PG_CHART_CONFIG.axis} scale="linear" />
            <Tooltip {...PG_CHART_CONFIG.tooltip} />
            <Legend {...PG_CHART_CONFIG.legend} />
            <Line yAxisId="high" type="monotone" dataKey="Cement" stroke={PG_CHART_CONFIG.materialColors.Cement} dot={false} />
            <Line yAxisId="high" type="monotone" dataKey="Conductor" stroke={PG_CHART_CONFIG.materialColors.Conductor} dot={false} />
            <Line yAxisId="high" type="monotone" dataKey="Insulator" stroke={PG_CHART_CONFIG.materialColors.Insulator} dot={false} />
            <Line yAxisId="high" type="monotone" dataKey="Steel" stroke={PG_CHART_CONFIG.materialColors.Steel} dot={false} />
            <Line yAxisId="low" type="monotone" dataKey="Transformer" stroke={PG_CHART_CONFIG.materialColors.Transformer} dot={false} strokeWidth={2.5} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
