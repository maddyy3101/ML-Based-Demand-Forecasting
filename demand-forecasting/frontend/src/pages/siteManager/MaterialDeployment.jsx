import { useEffect, useMemo, useState } from "react";
import { inventoryApi } from "../../api/inventoryApi";

export default function MaterialDeployment() {
  const [items, setItems] = useState([]);
  const [message, setMessage] = useState("");
  const [form, setForm] = useState({
    inventoryId: "",
    movementType: "DEPLOYMENT",
    quantity: 0,
    reason: "Project Installation",
    projectId: "",
    notes: "",
  });

  useEffect(() => {
    inventoryApi.items().then((data) => {
      setItems(data);
      if (data[0]) setForm((p) => ({ ...p, inventoryId: data[0].id }));
    });
  }, []);

  const selected = useMemo(() => items.find((x) => x.id === form.inventoryId), [items, form.inventoryId]);
  const after = (selected?.currentStock || 0) - (form.quantity || 0);
  const belowThreshold = after < (selected?.reorderThreshold || 0);

  const submit = async (event) => {
    event.preventDefault();
    setMessage("");
    try {
      await inventoryApi.movement(form);
      setMessage(`✅ ${form.quantity} ${selected?.unitLabel || "Units"} of ${selected?.materialName || "material"} deployed to Project ${form.projectId}`);
    } catch (err) {
      const text = err?.response?.data?.message || "Deployment failed";
      setMessage(`❌ ${text}`);
    }
  };

  return (
    <form className="pg-card p-4 grid md:grid-cols-2 gap-3" onSubmit={submit}>
      <label>Warehouse Item
        <select className="pg-input" value={form.inventoryId} onChange={(e) => setForm({ ...form, inventoryId: e.target.value })}>
          {items.map((item) => <option key={item.id} value={item.id}>{item.sku} • {item.materialName}</option>)}
        </select>
      </label>
      <label>Project ID<input className="pg-input" value={form.projectId} onChange={(e) => setForm({ ...form, projectId: e.target.value })} /></label>
      <label>Quantity Deployed<input className="pg-input" type="number" min={1} value={form.quantity} onChange={(e) => setForm({ ...form, quantity: Number(e.target.value) })} /></label>
      <label>Reason
        <select className="pg-input" value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })}>
          <option>Project Installation</option>
          <option>Emergency Dispatch</option>
          <option>Transfer</option>
          <option>Testing</option>
        </select>
      </label>
      <label>Deployment Date<input className="pg-input" type="date" /></label>
      <label className="md:col-span-2">Notes<textarea className="pg-input" rows={3} value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} /></label>
      <div className="md:col-span-2 text-sm" style={{ color: belowThreshold ? "var(--red)" : "var(--text-2)" }}>
        Current Stock: {selected?.currentStock || 0} → After Deployment: {after}
      </div>
      <button className="pg-btn pg-btn-primary md:col-span-2">Log Material Deployment</button>
      {message ? <div className="md:col-span-2">{message}</div> : null}
    </form>
  );
}
