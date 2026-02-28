export default function ExportCsvButton({ rows, fileName = "export.csv", label = "Export CSV" }) {
  const download = () => {
    if (!rows || rows.length === 0) return;
    const headers = Object.keys(rows[0]);
    const lines = [headers.join(",")];
    rows.forEach((row) => {
      lines.push(headers.map((key) => JSON.stringify(row[key] ?? "")).join(","));
    });
    const blob = new Blob([lines.join("\n")], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = fileName;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <button type="button" className="pg-btn" onClick={download}>
      {label}
    </button>
  );
}
