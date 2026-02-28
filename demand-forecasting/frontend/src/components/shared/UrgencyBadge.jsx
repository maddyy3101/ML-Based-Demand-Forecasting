const map = {
  HIGH: ["var(--red-dim)", "var(--red)"],
  MEDIUM: ["var(--amber-dim)", "var(--amber)"],
  LOW: ["var(--green-dim)", "var(--green)"],
};

export default function UrgencyBadge({ urgency }) {
  const [bg, color] = map[urgency] || map.LOW;
  return (
    <span className="pg-chip" style={{ background: bg, color }}>
      {urgency}
    </span>
  );
}
