import { useRef } from "react";

export default function FileDropzone({ onFile }) {
  const inputRef = useRef(null);

  const pick = () => inputRef.current?.click();

  const onChange = (event) => {
    const file = event.target.files?.[0];
    if (file) onFile(file);
  };

  const onDrop = (event) => {
    event.preventDefault();
    const file = event.dataTransfer.files?.[0];
    if (file) onFile(file);
  };

  return (
    <div
      className="pg-card p-8 text-center border-2 border-dashed"
      style={{ borderColor: "var(--orange)", background: "var(--orange-dim)" }}
      onDrop={onDrop}
      onDragOver={(e) => e.preventDefault()}
    >
      <div className="text-lg font-display">Drop powergrid_material_dataset.csv here</div>
      <p className="text-sm text-[var(--text-2)] mt-2">Must contain all 17 POWERGRID columns • CSV only • Max 100MB</p>
      <button type="button" className="pg-btn pg-btn-primary mt-4" onClick={pick}>
        Select File
      </button>
      <input ref={inputRef} type="file" accept=".csv" hidden onChange={onChange} />
    </div>
  );
}
