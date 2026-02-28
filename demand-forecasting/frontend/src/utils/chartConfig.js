export const PG_CHART_CONFIG = {
  grid: { stroke: "#1e3a6e", strokeDasharray: "3 3" },
  axis: {
    tick: { fill: "#8ba3cc", fontSize: 11, fontFamily: "Manrope, sans-serif", fontWeight: 500 },
    axisLine: { stroke: "#1e3a6e" },
    tickLine: false,
    tickMargin: 8,
  },
  tooltip: {
    contentStyle: {
      backgroundColor: "#0f2040",
      border: "1px solid #2d5299",
      borderRadius: "12px",
      color: "#e8f0fe",
      fontSize: "13px",
      fontFamily: "Manrope, sans-serif",
      fontWeight: 500,
      letterSpacing: "0.01em",
      boxShadow: "0 8px 32px rgba(0,0,0,0.7)",
    },
    itemStyle: {
      color: "#e8f0fe",
      fontFamily: "Manrope, sans-serif",
      fontWeight: 600,
    },
    labelStyle: {
      color: "#8ba3cc",
      fontFamily: "Manrope, sans-serif",
      fontWeight: 500,
    },
  },
  legend: {
    iconType: "circle",
    wrapperStyle: {
      color: "#c9d8f2",
      fontFamily: "Manrope, sans-serif",
      fontSize: "12px",
      fontWeight: 600,
      letterSpacing: "0.01em",
      paddingTop: "8px",
    },
  },
  materialColors: {
    Cement: "#f59e0b",
    Conductor: "#4f8ef7",
    Insulator: "#22c55e",
    Steel: "#a78bfa",
    Transformer: "#ef4444",
  },
  phaseColors: {
    Planning: "#4f8ef7",
    Execution: "#f47c20",
    Commissioning: "#22c55e",
  },
  towerColors: {
    "220kV": "#4f8ef7",
    "400kV": "#f47c20",
    "765kV": "#a78bfa",
  },
};
