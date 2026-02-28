export default function HealthGaugeChart({ score = 0 }) {
  const pct = Math.max(0, Math.min(100, score));
  return (
    <div className="pg-card p-4 h-80 flex flex-col items-center justify-center">
      <h3 className="font-display mb-3">Warehouse Health Score</h3>
      <div className="relative w-44 h-44 rounded-full border-[12px] border-[var(--border)] flex items-center justify-center">
        <div
          className="absolute inset-0 rounded-full"
          style={{
            background: `conic-gradient(var(--green) ${pct * 3.6}deg, transparent ${pct * 3.6}deg)`,
            WebkitMask: "radial-gradient(circle 56px at center, transparent 98%, black 100%)",
            mask: "radial-gradient(circle 56px at center, transparent 98%, black 100%)",
          }}
        />
        <div className="font-display text-3xl font-bold">{pct}%</div>
      </div>
    </div>
  );
}
