import { useAuth } from "../../context/AuthContext";

export default function TopBar({ title, subtitle }) {
  const { logout, user } = useAuth();

  return (
    <header className="pg-hero-wrap p-5 md:p-6 rounded-[28px]">
      <div className="pg-hero-left min-w-0">
        <div className="pg-label mb-1">POWERGRID COMMAND</div>
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <h1 className="font-display text-3xl md:text-6xl tracking-tight text-[var(--text-1)] leading-[1.02]">
            {title}
          </h1>
          <div className="text-right">
            <div className="text-xs md:text-sm font-semibold text-[var(--text-1)]">{user?.username}</div>
            <div className="text-xs text-[var(--text-2)]">{user?.role}</div>
            <button type="button" className="pg-btn pg-btn-primary mt-2" onClick={logout}>
              Logout
            </button>
          </div>
        </div>
        {subtitle ? <p className="pg-subtitle mt-3 max-w-[820px]">{subtitle}</p> : null}
      </div>
    </header>
  );
}
