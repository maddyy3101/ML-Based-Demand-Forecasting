import Sidebar from "./Sidebar";
import TopBar from "./TopBar";

export default function AppShell({ title, subtitle, children }) {
  return (
    <div className="pg-app-shell flex h-screen overflow-hidden bg-[var(--bg-base)]">
      <Sidebar />
      <div className="flex-1 min-w-0 h-screen min-h-0 flex flex-col">
        <TopBar title={title} subtitle={subtitle} />
        <main className="flex-1 min-h-0 overflow-y-auto p-6 pg-page-flow">{children}</main>
      </div>
    </div>
  );
}
