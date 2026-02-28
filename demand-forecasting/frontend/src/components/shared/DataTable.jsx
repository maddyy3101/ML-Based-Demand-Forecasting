import LoadingSkeleton from "./LoadingSkeleton";

export default function DataTable({ columns, rows, loading = false, emptyText = "No records" }) {
  if (loading) {
    return <LoadingSkeleton rows={6} height={28} />;
  }

  if (!rows || rows.length === 0) {
    return <div className="pg-card p-4 text-sm text-[var(--text-2)]">{emptyText}</div>;
  }

  return (
    <div className="overflow-x-auto pg-card">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-[var(--border)] backdrop-blur-md">
            {columns.map((col) => (
              <th key={col.key} className="px-3 py-2 text-left text-[var(--text-2)] font-medium">
                {col.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, rowIndex) => (
            <tr
              key={row.id || rowIndex}
              className="border-b border-[var(--border)]/50 transition-colors duration-200 hover:bg-[rgba(84,122,191,0.11)]"
            >
              {columns.map((col) => (
                <td key={col.key} className="px-3 py-2 text-[var(--text-1)]">
                  {col.render ? col.render(row[col.key], row) : row[col.key]}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
