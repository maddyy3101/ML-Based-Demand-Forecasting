import { useEffect, useMemo, useState } from "react";
import { inventoryApi } from "../../api/inventoryApi";

export default function MaterialReceipt() {
  const [items, setItems] = useState([]);
  const [toast, setToast] = useState("");
  const [form, setForm] = useState({
    inventoryId: "",
    movementType: "RECEIPT",
    quantity: 0,
    reason: "Vendor delivery",
    vendorName: "",
    invoiceNumber: "",
    notes: "",
  });

  useEffect(() => {
    inventoryApi.items().then((data) => {
      setItems(data);
      if (data[0]) setForm((p) => ({ ...p, inventoryId: data[0].id }));
    });
  }, []);

  const selected = useMemo(() => items.find((x) => x.id === form.inventoryId), [items, form.inventoryId]);

  const submit = async (event) => {
    event.preventDefault();
    await inventoryApi.movement(form);
    setToast(`✅ ${form.quantity} ${selected?.unitLabel || "Units"} of ${selected?.materialName || "material"} received from ${form.vendorName}`);
  };

  return (
    <form className="pg-card p-4 grid md:grid-cols-2 gap-3" onSubmit={submit}>
      <label>Warehouse Item
        <select className="pg-input" value={form.inventoryId} onChange={(e) => setForm({ ...form, inventoryId: e.target.value })}>
          {items.map((item) => <option key={item.id} value={item.id}>{item.sku} • {item.materialName}</option>)}
        </select>
      </label>
      <label>Vendor Name<input className="pg-input" value={form.vendorName} onChange={(e) => setForm({ ...form, vendorName: e.target.value })} /></label>
      <label>Invoice Number<input className="pg-input" value={form.invoiceNumber} onChange={(e) => setForm({ ...form, invoiceNumber: e.target.value })} /></label>
      <label>Quantity Received<input className="pg-input" type="number" min={1} value={form.quantity} onChange={(e) => setForm({ ...form, quantity: Number(e.target.value) })} /></label>
      <label>Unit Label<input className="pg-input" readOnly value={selected?.unitLabel || "Units"} /></label>
      <label>Delivery Date<input className="pg-input" type="date" /></label>
      <label className="md:col-span-2">Notes<textarea className="pg-input" rows={3} value={form.notes} onChange={(e) => setForm({ ...form, notes: e.target.value })} /></label>
      <button className="pg-btn pg-btn-primary md:col-span-2">Log Material Receipt</button>
      {toast ? <div className="md:col-span-2" style={{ color: "var(--green)" }}>{toast}</div> : null}
    </form>
  );
}
