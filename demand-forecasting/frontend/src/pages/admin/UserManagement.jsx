import { useEffect, useMemo, useState } from "react";
import { adminApi } from "../../api/adminApi";
import DataTable from "../../components/shared/DataTable";

const regions = ["North", "South", "East", "West"];

const defaultUser = {
  username: "",
  email: "",
  password: "",
  role: "ROLE_PROCUREMENT_OFFICER",
  assignedRegion: "North",
  employeeId: "",
};

function parseError(err) {
  return (
    err?.response?.data?.message ||
    err?.response?.data?.error ||
    err?.message ||
    "Operation failed"
  );
}

export default function UserManagement() {
  const [users, setUsers] = useState([]);
  const [form, setForm] = useState(defaultUser);
  const [loadingUsers, setLoadingUsers] = useState(false);
  const [creating, setCreating] = useState(false);
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [createdLogin, setCreatedLogin] = useState(null);

  const isAdminRole = form.role === "ROLE_ADMIN";

  const canSubmit = useMemo(() => {
    if (!form.username.trim()) return false;
    if (!form.email.trim()) return false;
    if (form.password.length < 8) return false;
    if (!isAdminRole && !form.assignedRegion.trim()) return false;
    return true;
  }, [form, isAdminRole]);

  const load = async () => {
    setLoadingUsers(true);
    try {
      const data = await adminApi.users();
      setUsers(data);
    } catch (err) {
      setError(parseError(err));
    } finally {
      setLoadingUsers(false);
    }
  };

  useEffect(() => {
    load();
  }, []);

  const create = async (event) => {
    event.preventDefault();
    setMessage("");
    setError("");
    setCreatedLogin(null);

    if (!canSubmit) {
      setError("Please fill all required fields. Password must be at least 8 characters.");
      return;
    }

    const payload = {
      ...form,
      username: form.username.trim().toLowerCase(),
      email: form.email.trim().toLowerCase(),
      assignedRegion: isAdminRole ? null : form.assignedRegion.trim(),
      employeeId: form.employeeId.trim() || undefined,
    };

    setCreating(true);
    try {
      const created = await adminApi.createUser(payload);
      setMessage(`User created: ${created.username} (${created.role})`);
      setCreatedLogin({
        username: payload.username,
        password: payload.password,
        role: created.role,
        assignedRegion: created.assignedRegion,
      });
      setForm(defaultUser);
      await load();
    } catch (err) {
      setError(parseError(err));
    } finally {
      setCreating(false);
    }
  };

  const deactivate = async (id) => {
    setError("");
    setMessage("");
    try {
      await adminApi.deactivateUser(id);
      setMessage("User deactivated.");
      await load();
    } catch (err) {
      setError(parseError(err));
    }
  };

  const reactivate = async (id) => {
    setError("");
    setMessage("");
    try {
      await adminApi.reactivateUser(id);
      setMessage("User reactivated.");
      await load();
    } catch (err) {
      setError(parseError(err));
    }
  };

  const removePermanently = async (id, username) => {
    setError("");
    setMessage("");
    const proceed = window.confirm(`Permanently delete user '${username}'? This cannot be undone.`);
    if (!proceed) return;
    try {
      await adminApi.deleteUserPermanently(id);
      setMessage("User permanently deleted.");
      await load();
    } catch (err) {
      setError(parseError(err));
    }
  };

  return (
    <div className="space-y-4">
      <form className="pg-card p-4 grid md:grid-cols-3 gap-3" onSubmit={create}>
        <input
          className="pg-input"
          placeholder="Username (min 4 chars)"
          value={form.username}
          onChange={(e) => setForm({ ...form, username: e.target.value })}
        />
        <input
          className="pg-input"
          type="email"
          placeholder="Email"
          value={form.email}
          onChange={(e) => setForm({ ...form, email: e.target.value })}
        />
        <input
          className="pg-input"
          type="password"
          placeholder="Password (min 8 chars)"
          value={form.password}
          onChange={(e) => setForm({ ...form, password: e.target.value })}
        />

        <select
          className="pg-input"
          value={form.role}
          onChange={(e) =>
            setForm({
              ...form,
              role: e.target.value,
              assignedRegion: e.target.value === "ROLE_ADMIN" ? "" : form.assignedRegion || "North",
            })
          }
        >
          <option value="ROLE_ADMIN">HQ Admin</option>
          <option value="ROLE_PROCUREMENT_OFFICER">Procurement Officer</option>
          <option value="ROLE_SITE_MANAGER">Site Manager</option>
        </select>

        <select
          className="pg-input"
          value={form.assignedRegion}
          onChange={(e) => setForm({ ...form, assignedRegion: e.target.value })}
          disabled={isAdminRole}
        >
          {isAdminRole ? <option value="">No region (HQ scope)</option> : null}
          {regions.map((region) => (
            <option key={region} value={region}>
              {region}
            </option>
          ))}
        </select>

        <input
          className="pg-input"
          placeholder="Employee ID (optional)"
          value={form.employeeId}
          onChange={(e) => setForm({ ...form, employeeId: e.target.value })}
        />

        <button className="pg-btn pg-btn-primary md:col-span-3" disabled={!canSubmit || creating}>
          {creating ? "Creating user..." : "Create User Login"}
        </button>
      </form>

      {message ? <div className="pg-card p-3 text-sm" style={{ color: "var(--green)" }}>{message}</div> : null}
      {error ? <div className="pg-card p-3 text-sm" style={{ color: "var(--red)" }}>{error}</div> : null}

      {createdLogin ? (
        <div className="pg-card p-4 text-sm">
          <div className="font-display text-base">New Login Credentials</div>
          <div className="mt-1 font-mono">Username: {createdLogin.username}</div>
          <div className="font-mono">Password: {createdLogin.password}</div>
          <div>Role: {createdLogin.role}</div>
          <div>Assigned Region: {createdLogin.assignedRegion || "HQ"}</div>
        </div>
      ) : null}

      <DataTable
        loading={loadingUsers}
        columns={[
          { key: "username", label: "Username" },
          { key: "email", label: "Email" },
          { key: "role", label: "Role" },
          { key: "assignedRegion", label: "Region" },
          { key: "active", label: "Active" },
          { key: "createdAt", label: "Created At" },
          {
            key: "action",
            label: "Action",
            render: (_, row) =>
              row.active ? (
                <button className="pg-btn" onClick={() => deactivate(row.id)}>
                  Deactivate
                </button>
              ) : (
                <div className="flex gap-2">
                  <button
                    className="pg-btn"
                    onClick={() => reactivate(row.id)}
                    style={{ borderColor: "var(--green)", color: "var(--green)" }}
                  >
                    Reactivate
                  </button>
                  <button
                    className="pg-btn"
                    onClick={() => removePermanently(row.id, row.username)}
                    style={{ borderColor: "var(--red)", color: "var(--red)" }}
                  >
                    Delete
                  </button>
                </div>
              ),
          },
        ]}
        rows={users}
      />
    </div>
  );
}
