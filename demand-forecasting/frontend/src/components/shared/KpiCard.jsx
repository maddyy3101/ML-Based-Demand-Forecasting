import { useEffect, useMemo, useRef, useState } from "react";
import { formatNumber } from "../../utils/formatters";

function easeOutCubic(t) {
  return 1 - Math.pow(1 - t, 3);
}

export default function KpiCard({
  title,
  value,
  unit,
  icon,
  trend,
  trendUp,
  trendGood,
  accentColor = "var(--orange)",
  sparkline,
}) {
  const [displayValue, setDisplayValue] = useState(0);
  const rafRef = useRef(null);

  const numeric = Number(value || 0);

  useEffect(() => {
    const start = performance.now();
    const from = 0;
    const duration = 1200;

    const run = (now) => {
      const t = Math.min((now - start) / duration, 1);
      const eased = easeOutCubic(t);
      setDisplayValue(from + (numeric - from) * eased);
      if (t < 1) {
        rafRef.current = requestAnimationFrame(run);
      }
    };

    rafRef.current = requestAnimationFrame(run);
    return () => {
      if (rafRef.current) cancelAnimationFrame(rafRef.current);
    };
  }, [numeric]);

  const trendColor = useMemo(() => {
    if (trendGood === undefined) return "var(--text-2)";
    return trendGood ? "var(--green)" : "var(--red)";
  }, [trendGood]);

  return (
    <div
      className="pg-card pg-premium-card p-4"
      style={{ borderColor: accentColor, boxShadow: `0 0 0 1px ${accentColor}33` }}
    >
      <div className="flex items-center justify-between">
        <div>
          <div className="pg-label">{title}</div>
          <div className="font-display text-2xl font-bold text-[var(--text-1)] mt-1">
            {formatNumber(displayValue.toFixed(0))}
            {unit ? <span className="text-sm text-[var(--text-2)] ml-1">{unit}</span> : null}
          </div>
        </div>
        <div className="text-xl">{icon}</div>
      </div>
      <div className="mt-3 flex items-center justify-between">
        <div className="text-xs" style={{ color: trendColor }}>
          {trend ? `${trendUp ? "▲" : "▼"} ${trend}` : "No trend"}
        </div>
        {sparkline ? (
          <svg width="80" height="24" viewBox="0 0 80 24" fill="none">
            <polyline
              points={sparkline}
              fill="none"
              stroke={accentColor}
              strokeWidth="2"
              strokeLinecap="round"
            />
          </svg>
        ) : null}
      </div>
    </div>
  );
}
