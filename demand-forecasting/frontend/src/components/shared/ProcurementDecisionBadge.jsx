const styleMap = {
  CRITICAL_PROCUREMENT: { bg: "var(--red-dim)", color: "var(--red)", text: "CRITICAL - Order Immediately" },
  PROCURE_NOW: { bg: "var(--orange-dim)", color: "var(--orange)", text: "PROCURE NOW" },
  PLAN_ORDER: { bg: "var(--amber-dim)", color: "var(--amber)", text: "PLAN ORDER" },
  HOLD: { bg: "var(--green-dim)", color: "var(--green)", text: "HOLD - Monitor" },
};

export default function ProcurementDecisionBadge({ decision }) {
  const style = styleMap[decision] || styleMap.PLAN_ORDER;
  return (
    <span className="pg-chip" style={{ background: style.bg, color: style.color }}>
      {style.text}
    </span>
  );
}
