import Sidebar from "./Sidebar";
import TopBar from "./TopBar";
import Chatbot from "../shared/Chatbot";

export default function AppShell({ title, subtitle, children }) {
  return (
    <div className="pg-app-shell min-h-screen px-3 md:px-4 py-3 md:py-4">
      <div className="pg-shell-frame">
        <Sidebar />
        <div className="pg-stage">
          <div className="pg-stage-inner">
            <TopBar title={title} subtitle={subtitle} />
            <main className="pg-page-flow pg-scrollbar pg-main-animate">
              {children}
            </main>
          </div>
        </div>
      </div>
      <Chatbot />
    </div>
  );
}
