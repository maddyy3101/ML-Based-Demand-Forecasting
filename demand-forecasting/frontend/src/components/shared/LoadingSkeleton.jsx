export default function LoadingSkeleton({ rows = 4, height = 16 }) {
  return (
    <div className="space-y-2 animate-pulse">
      {Array.from({ length: rows }).map((_, idx) => (
        <div
          key={idx}
          className="rounded-md"
          style={{ height, background: "linear-gradient(90deg, rgba(67,24,34,0.58), rgba(96,40,30,0.72), rgba(67,24,34,0.58))" }}
        />
      ))}
    </div>
  );
}
