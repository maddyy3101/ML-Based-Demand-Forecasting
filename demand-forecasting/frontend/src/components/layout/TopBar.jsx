import { useAuth } from "../../context/AuthContext";

export default function TopBar({ title, subtitle }) {
  const { logout, user } = useAuth();

  return (
    <header className="border-b border-[var(--border)] px-6 py-4 flex items-center justify-between pg-glass">
      <div>
        <h1 className="font-display text-2xl tracking-tight">{title}</h1>
        {subtitle ? <p className="pg-subtitle mt-1">{subtitle}</p> : null}
      </div>
      <div className="flex items-center gap-3">
        <div className="text-right hidden md:block">
          <div className="text-sm font-medium">{user?.username}</div>
          <div className="text-xs text-[var(--text-2)]">{user?.role}</div>
        </div>
        <button type="button" className="pg-btn" onClick={logout}>
          Logout
        </button>
      </div>
    </header>
  );
}
