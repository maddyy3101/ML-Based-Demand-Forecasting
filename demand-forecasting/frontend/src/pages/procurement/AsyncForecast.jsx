import { useCallback, useEffect, useState } from "react";
import { forecastApi } from "../../api/forecastApi";
import { jobsApi } from "../../api/jobsApi";
import { usePolling } from "../../hooks/usePolling";
import ProgressBar from "../../components/shared/ProgressBar";
import ProcurementDecisionBadge from "../../components/shared/ProcurementDecisionBadge";
import LoadingSkeleton from "../../components/shared/LoadingSkeleton";

const defaultPayload = {
  projectId: "P123",
  projectPhase: "Execution",
  state: "Rajasthan",
  region: "North",
  terrainType: "Plain",
  towerType: "400kV",
  substationType: "AIS",
  transmissionLengthKm: 210,
  budgetCrore: 1400,
  materialType: "Conductor",
  leadTimeDays: 36,
  taxPercentage: 18,
  transportationCost: 14500,
  historicalConsumption: 860,
  forecastMonth: 7,
  forecastYear: 2025,
};

function progressValue(status) {
  if (status === "FAILED") return 100;
  if (status === "COMPLETED") return 100;
  if (status === "RUNNING") return 65;
  if (status === "PENDING") return 25;
  return 0;
}

function parseError(err) {
  return err?.response?.data?.message || err?.message || "Operation failed";
}

export default function AsyncForecast() {
  const [form, setForm] = useState(defaultPayload);
  const [jobId, setJobId] = useState("");
  const [status, setStatus] = useState(null);
  const [recentJobs, setRecentJobs] = useState([]);
  const [submitting, setSubmitting] = useState(false);
  const [loadingRecent, setLoadingRecent] = useState(false);
  const [error, setError] = useState("");

  const currentStatus = status?.status || "";
  const isPolling = Boolean(jobId && !["COMPLETED", "FAILED"].includes(currentStatus));

  const loadRecentJobs = useCallback(async () => {
    setLoadingRecent(true);
    try {
      const rows = await jobsApi.list();
      setRecentJobs((rows || []).filter((r) => r.type === "FORECAST").slice(0, 8));
    } catch {
      setRecentJobs([]);
    } finally {
      setLoadingRecent(false);
    }
  }, []);

  const submit = async () => {
    setError("");
    setSubmitting(true);
    try {
      const data = await forecastApi.asyncForecast(form);
      setJobId(data.jobId);
      setStatus({
        jobId: data.jobId,
        type: "FORECAST",
        status: data.status || "PENDING",
        message: data.message,
        result: null,
      });
      await loadRecentJobs();
    } catch (err) {
      setError(parseError(err));
    } finally {
      setSubmitting(false);
    }
  };

  usePolling(
    async () => {
      if (!jobId) return;
      const data = await jobsApi.status(jobId);
      setStatus(data);
      if (["COMPLETED", "FAILED"].includes(data.status)) {
        await loadRecentJobs();
      }
    },
    2500,
    isPolling
  );

  useEffect(() => {
    loadRecentJobs();
  }, [loadRecentJobs]);

  const result = status?.result || null;
  const showResult = status?.status === "COMPLETED" && result && typeof result === "object";

  return (
    <div className="grid grid-cols-1 xl:grid-cols-12 gap-4">
      <div className="xl:col-span-8 space-y-4">
        <div className="pg-card pg-premium-card p-5">
          <div className="flex flex-wrap gap-4 justify-between items-start">
            <div>
              <h3 className="font-display text-xl">Async Forecast Orchestrator</h3>
              <p className="text-sm text-[var(--text-2)] mt-1">
                Submit large computations asynchronously and monitor lifecycle in real-time.
              </p>
            </div>
            <button className="pg-btn" onClick={() => setForm(defaultPayload)}>
              Reset to Template
            </button>
          </div>

          <div className="mt-4 grid md:grid-cols-3 gap-3">
            <input className="pg-input" value={form.projectId} onChange={(e) => setForm({ ...form, projectId: e.target.value })} placeholder="Project ID" />
            <select className="pg-input" value={form.projectPhase} onChange={(e) => setForm({ ...form, projectPhase: e.target.value })}>
              <option>Planning</option>
              <option>Execution</option>
              <option>Commissioning</option>
            </select>
            <select className="pg-input" value={form.materialType} onChange={(e) => setForm({ ...form, materialType: e.target.value })}>
              <option>Cement</option>
              <option>Conductor</option>
              <option>Insulator</option>
              <option>Steel</option>
              <option>Transformer</option>
            </select>
            <input className="pg-input" type="number" value={form.historicalConsumption} onChange={(e) => setForm({ ...form, historicalConsumption: Number(e.target.value) })} placeholder="Historical Consumption" />
            <input className="pg-input" type="number" value={form.budgetCrore} onChange={(e) => setForm({ ...form, budgetCrore: Number(e.target.value) })} placeholder="Budget Crore" />
            <input className="pg-input" type="number" value={form.transmissionLengthKm} onChange={(e) => setForm({ ...form, transmissionLengthKm: Number(e.target.value) })} placeholder="Transmission KM" />
          </div>

          <div className="mt-4 flex gap-3">
            <button className="pg-btn pg-btn-primary" onClick={submit} disabled={submitting}>
              {submitting ? "Submitting..." : "Submit Async Forecast"}
            </button>
            <button className="pg-btn" onClick={loadRecentJobs}>Refresh Job Feed</button>
          </div>

          {error ? <div className="mt-3 text-sm" style={{ color: "var(--red)" }}>{error}</div> : null}
        </div>

        <div className="pg-card pg-premium-card p-5">
          <div className="flex items-center justify-between">
            <h4 className="font-display text-lg">Job Lifecycle</h4>
            <div className="text-xs text-[var(--text-2)]">Job ID: <span className="font-mono">{jobId || "-"}</span></div>
          </div>
          <div className="mt-3">
            <ProgressBar value={progressValue(currentStatus)} />
          </div>

          <div className="mt-3 grid md:grid-cols-3 gap-2 text-sm">
            <div className="pg-card p-3">
              <div className="text-[11px] text-[var(--text-2)]">Status</div>
              <div className="font-semibold">{currentStatus || "IDLE"}</div>
            </div>
            <div className="pg-card p-3">
              <div className="text-[11px] text-[var(--text-2)]">Type</div>
              <div className="font-semibold">{status?.type || "FORECAST"}</div>
            </div>
            <div className="pg-card p-3">
              <div className="text-[11px] text-[var(--text-2)]">Message</div>
              <div className="font-semibold">{status?.message || "Waiting for submission"}</div>
            </div>
          </div>
        </div>

        {showResult ? (
          <div className="pg-card pg-premium-card p-5">
            <div className="flex items-center justify-between">
              <h4 className="font-display text-lg">Completed Forecast Output</h4>
              <ProcurementDecisionBadge decision={result.procurementDecision} />
            </div>
            <div className="mt-3 grid md:grid-cols-2 gap-3">
              <div className="pg-card p-4">
                <div className="text-[11px] text-[var(--text-2)]">Predicted Quantity</div>
                <div className="text-3xl font-display mt-1" style={{ color: "var(--orange)" }}>
                  {result.predictedQuantity}
                </div>
                <div className="text-sm text-[var(--text-2)] mt-1">{result.materialType} ({result.unitLabel})</div>
              </div>
              <div className="pg-card p-4">
                <div className="text-[11px] text-[var(--text-2)]">Request ID</div>
                <div className="font-mono text-sm mt-1">{result.requestId}</div>
                <div className="text-[11px] text-[var(--text-2)] mt-3">Model</div>
                <div className="text-sm">{result.modelType}</div>
              </div>
            </div>
          </div>
        ) : null}

        {status ? (
          <details className="pg-card p-4">
            <summary className="cursor-pointer text-sm text-[var(--text-2)]">Raw job payload</summary>
            <pre className="text-xs mt-3 overflow-x-auto">{JSON.stringify(status, null, 2)}</pre>
          </details>
        ) : null}
      </div>

      <div className="xl:col-span-4">
        <div className="pg-card pg-premium-card p-5">
          <h4 className="font-display text-lg">Recent Async Forecast Jobs</h4>
          <p className="text-xs text-[var(--text-2)] mt-1">Persisted in database for audit and tracking.</p>
          <div className="mt-3 space-y-2">
            {loadingRecent ? <LoadingSkeleton rows={5} /> : null}
            {!loadingRecent && recentJobs.map((job) => (
              <button
                key={job.jobId}
                className="pg-card p-3 w-full text-left hover:border-[var(--border-hi)]"
                onClick={async () => {
                  const full = await jobsApi.status(job.jobId);
                  setJobId(job.jobId);
                  setStatus(full);
                }}
              >
                <div className="flex justify-between text-xs">
                  <span className="font-mono">{job.jobId.slice(0, 8)}...</span>
                  <span>{job.status}</span>
                </div>
                <div className="text-sm mt-1">{job.message}</div>
                <div className="text-[11px] text-[var(--text-2)] mt-1">
                  {job.createdBy || "system"} • {job.createdAt ? new Date(job.createdAt).toLocaleString() : "-"}
                </div>
              </button>
            ))}
            {!loadingRecent && !recentJobs.length ? (
              <div className="text-sm text-[var(--text-2)]">No async forecast jobs yet.</div>
            ) : null}
          </div>
        </div>
      </div>
    </div>
  );
}
