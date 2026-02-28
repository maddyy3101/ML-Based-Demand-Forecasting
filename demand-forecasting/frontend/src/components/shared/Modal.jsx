export default function Modal({ open, title, children, onClose, footer }) {
  if (!open) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60">
      <div className="pg-card w-[90%] max-w-2xl bg-[var(--bg-overlay)]">
        <div className="px-4 py-3 border-b border-[var(--border)] flex items-center justify-between">
          <h3 className="font-display text-lg">{title}</h3>
          <button type="button" onClick={onClose} className="pg-btn">Close</button>
        </div>
        <div className="p-4">{children}</div>
        {footer ? <div className="p-4 border-t border-[var(--border)]">{footer}</div> : null}
      </div>
    </div>
  );
}
