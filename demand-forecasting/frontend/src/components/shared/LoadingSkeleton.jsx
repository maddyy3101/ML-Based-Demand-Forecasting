export default function LoadingSkeleton({ rows = 4, height = 16 }) {
  return (
    <div className="space-y-2 animate-pulse">
      {Array.from({ length: rows }).map((_, idx) => (
        <div
          key={idx}
          className="rounded-md"
          style={{ height, background: "linear-gradient(90deg, #12244a, #1b366e, #12244a)" }}
        />
      ))}
    </div>
  );
}
