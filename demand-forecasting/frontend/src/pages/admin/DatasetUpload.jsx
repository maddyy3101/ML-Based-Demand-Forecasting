import { useCallback, useMemo, useState } from "react";
import FileDropzone from "../../components/shared/FileDropzone";
import ProgressBar from "../../components/shared/ProgressBar";
import DataTable from "../../components/shared/DataTable";
import { adminApi } from "../../api/adminApi";
import { usePolling } from "../../hooks/usePolling";

const requiredColumns = [
  "Project_ID",
  "Project_Phase",
  "State",
  "Region",
  "Terrain_Type",
  "Tower_Type",
  "Substation_Type",
  "Transmission_Length_KM",
  "Budget_Crore",
  "Material_Type",
  "Lead_Time_Days",
  "Tax_Percentage",
  "Transportation_Cost",
  "Historical_Consumption",
  "Month",
  "Year",
  "Quantity_Required",
];

function parseCsvPreview(fileText) {
  const lines = fileText.split(/\r?\n/).filter(Boolean);
  const headers = lines[0]?.split(",") || [];
  const rows = lines.slice(1).map((line) => line.split(","));
  const previewRows = rows.slice(0, 5).map((cells, index) => {
    const obj = { id: index + 1 };
    headers.forEach((h, i) => {
      obj[h] = cells[i];
    });
    return obj;
  });

  const projectSet = new Set(rows.map((r) => r[0]));
  const materialSet = new Set(rows.map((r) => r[9]));

  return {
    headers,
    previewRows,
    stats: {
      rowCount: rows.length,
      projects: projectSet.size,
      materials: materialSet.size,
    },
  };
}

export default function DatasetUpload() {
  const [step, setStep] = useState(1);
  const [file, setFile] = useState(null);
  const [parsed, setParsed] = useState(null);
  const [uploadProgress, setUploadProgress] = useState(0);
  const [job, setJob] = useState(null);
  const [status, setStatus] = useState(null);
  const [error, setError] = useState("");

  const onFile = async (pickedFile) => {
    setFile(pickedFile);
    setError("");
    const text = await pickedFile.text();
    const parsedCsv = parseCsvPreview(text);
    setParsed(parsedCsv);
    setStep(2);
  };

  const missingCols = useMemo(() => {
    if (!parsed) return requiredColumns;
    return requiredColumns.filter((col) => !parsed.headers.includes(col));
  }, [parsed]);

  const canProceedValidation = parsed && missingCols.length === 0;

  const upload = async () => {
    if (!file || !canProceedValidation) return;
    setStep(3);
    setError("");
    try {
      const result = await adminApi.uploadDataset(file, (evt) => {
        const pct = Math.round((evt.loaded * 100) / (evt.total || 1));
        setUploadProgress(pct);
      });
      setJob(result);
      setStep(4);
    } catch (err) {
      setError(err?.response?.data?.message || "Upload failed");
    }
  };

  const pollStatus = useCallback(async () => {
    if (!job?.jobId) return;
    const data = await adminApi.retrainingStatus(job.jobId);
    setStatus(data);
  }, [job]);

  usePolling(pollStatus, 3000, Boolean(job?.jobId));

  return (
    <div className="space-y-4">
      <div className="pg-card p-4">
        <div className="text-sm text-[var(--text-2)]">Step {step} of 4</div>
        <h2 className="font-display text-xl mt-1">POWERGRID Dataset Upload Wizard</h2>
      </div>

      {step === 1 ? <FileDropzone onFile={onFile} /> : null}

      {step >= 2 && parsed ? (
        <div className="pg-card p-4 space-y-3">
          <h3 className="font-display">Step 2 - Validate Columns</h3>
          <div className="grid md:grid-cols-2 gap-2 text-sm">
            {requiredColumns.map((column) => {
              const ok = parsed.headers.includes(column);
              return (
                <div key={column} style={{ color: ok ? "var(--green)" : "var(--red)" }}>
                  {ok ? "✅" : "❌"} {column}
                </div>
              );
            })}
          </div>

          <div className="text-sm text-[var(--text-2)]">
            {parsed.stats.rowCount} rows • {parsed.stats.projects} projects • {parsed.stats.materials} materials
          </div>

          <DataTable
            columns={parsed.headers.slice(0, 8).map((h) => ({ key: h, label: h }))}
            rows={parsed.previewRows}
            emptyText="No preview rows"
          />

          <button
            type="button"
            disabled={!canProceedValidation}
            className="pg-btn pg-btn-primary"
            onClick={() => setStep(3)}
          >
            Continue to Upload
          </button>
          {!canProceedValidation ? (
            <div className="text-sm" style={{ color: "var(--red)" }}>
              Missing required columns: {missingCols.join(", ")}
            </div>
          ) : null}
        </div>
      ) : null}

      {step >= 3 ? (
        <div className="pg-card p-4 space-y-3">
          <h3 className="font-display">Step 3 - Upload</h3>
          <div className="text-sm text-[var(--text-2)]">File: {file?.name}</div>
          <ProgressBar value={uploadProgress} />
          <button type="button" className="pg-btn pg-btn-primary" onClick={upload}>
            Upload and Trigger Retraining
          </button>
        </div>
      ) : null}

      {step === 4 ? (
        <div className="pg-card p-4 space-y-3">
          <h3 className="font-display">Step 4 - Retraining Monitor</h3>
          <div className="pg-chip" style={{
            background:
              status?.status === "COMPLETED"
                ? "var(--green-dim)"
                : status?.status === "FAILED"
                ? "var(--red-dim)"
                : "var(--orange-dim)",
            color:
              status?.status === "COMPLETED"
                ? "var(--green)"
                : status?.status === "FAILED"
                ? "var(--red)"
                : "var(--orange)",
          }}>
            {status?.status || "PENDING"}
          </div>

          <pre className="p-3 rounded-md text-xs font-mono bg-black/50 max-h-64 overflow-auto">
            {status?.logOutput || "Waiting for retraining logs..."}
          </pre>

          {status?.status === "COMPLETED" ? (
            <div className="pg-card p-4 border border-[var(--green)]">
              <div className="text-xl">🎉 Retraining completed</div>
              <div className="text-sm text-[var(--text-2)] mt-1">Updated model metrics:</div>
              <pre className="text-xs mt-2">{status?.modelMetrics}</pre>
            </div>
          ) : null}
        </div>
      ) : null}

      {error ? <div className="text-sm" style={{ color: "var(--red)" }}>{error}</div> : null}
    </div>
  );
}
