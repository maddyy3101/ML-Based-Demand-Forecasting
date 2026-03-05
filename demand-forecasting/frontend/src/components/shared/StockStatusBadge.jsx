const map = {
  OK: ["var(--green-dim)", "var(--green)"],
  LOW: ["var(--amber-dim)", "var(--amber)"],
  CRITICAL: ["var(--red-dim)", "var(--red)"],
  OVERSTOCK: ["rgba(156, 166, 180, 0.14)", "var(--purple)"],
};

export default function StockStatusBadge({ status }) {
  const [bg, color] = map[status] || map.OK;
  return (
    <span className="pg-chip" style={{ background: bg, color }}>
      {status}
    </span>
  );
}
