import { useEffect, useState } from "react";
import { adminApi } from "../../api/adminApi";

function parseError(err) {
  return (
    err?.response?.data?.message ||
    err?.response?.data?.error ||
    err?.message ||
    "Failed to update ProcDoc API key."
  );
}

function sourceLabel(source) {
  if (source === "ADMIN_OVERRIDE") return "Admin override";
  if (source === "APPLICATION_CONFIG") return "Application config";
  if (source === "HARDCODED_DEFAULT") return "Hardcoded backend default";
  if (source === "NOT_CONFIGURED") return "Not configured";
  return source || "Unknown";
}

export default function ProcDoc() {
  const [keyInput, setKeyInput] = useState("");
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  const loadStatus = async () => {
    setLoading(true);
    setError("");
    try {
      const data = await adminApi.procDocStatus();
      setStatus(data);
    } catch (err) {
      setError(parseError(err));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadStatus();
  }, []);

  const authenticate = async (event) => {
    event.preventDefault();
    setMessage("");
    setError("");

    if (!keyInput.trim()) {
      setError("Enter an API key first.");
      return;
    }

    setSaving(true);
    try {
      const data = await adminApi.authenticateProcDocKey({ apiKey: keyInput.trim() });
      setStatus(data);
      setMessage(data.statusMessage || "ProcDoc key authenticated.");
      setKeyInput("");
    } catch (err) {
      setError(parseError(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="space-y-4">
      <div className="pg-card p-4">
        <h3 className="font-display text-xl">ProcDoc API Key Management</h3>
        <div className="text-sm text-[var(--text-2)] mt-1">Current ProcDoc key source</div>
        <div className="font-display text-2xl mt-1">
          {loading ? "Loading..." : sourceLabel(status?.source)}
        </div>
        <div className="text-sm mt-1 text-[var(--text-2)]">
          Key: {status?.maskedKey || "Not configured"}
        </div>
        <div className="text-sm mt-1">
          Status:{" "}
          <span style={{ color: status?.configured ? "var(--green)" : "var(--red)" }}>
            {status?.configured ? "Configured" : "Not configured"}
          </span>
        </div>
      </div>

      <form className="pg-card p-4 space-y-3" onSubmit={authenticate}>
        <label className="block">
          <span className="text-xs text-[var(--text-2)]">Enter Groq or Gemini API key</span>
          <input
            type="password"
            className="pg-input mt-1"
            value={keyInput}
            onChange={(e) => setKeyInput(e.target.value)}
            placeholder="gsk_... or AIza..."
            autoComplete="off"
          />
        </label>
        <div className="pg-procdoc-actions">
          <button type="submit" className="pg-btn pg-btn-primary pg-procdoc-auth-btn" disabled={saving}>
            {saving ? "Authenticating..." : "Authenticate & Use Key"}
          </button>
          <button
            type="button"
            className="pg-btn pg-procdoc-refresh-btn"
            onClick={loadStatus}
            disabled={loading || saving}
          >
            Refresh Status
          </button>
        </div>
      </form>

      {message ? (
        <div className="pg-card p-3 text-sm" style={{ color: "var(--green)" }}>
          {message}
        </div>
      ) : null}
      {error ? (
        <div className="pg-card p-3 text-sm" style={{ color: "var(--red)" }}>
          {error}
        </div>
      ) : null}
    </div>
  );
}
