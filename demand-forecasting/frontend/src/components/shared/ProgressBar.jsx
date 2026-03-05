export default function ProgressBar({ value }) {
  return (
    <div className="w-full h-3 rounded-full bg-[var(--bg-surface)] border border-[var(--border)] overflow-hidden">
      <div
        className="h-full"
        style={{
          width: `${Math.max(0, Math.min(100, value))}%`,
          background: "linear-gradient(90deg, var(--navy-light), var(--text-1))",
          transition: "width 0.3s",
        }}
      />
    </div>
  );
}
