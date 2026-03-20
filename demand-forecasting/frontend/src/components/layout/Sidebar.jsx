import { NavLink } from "react-router-dom";
import { useAuth } from "../../context/AuthContext";
import { useEffect, useRef, useState } from "react";

const navConfig = {
  ROLE_ADMIN: [
    { label: "Dashboard", to: "/admin/dashboard" },
    { label: "System Health", to: "/admin/system-health" },
    { label: "Dataset Upload", to: "/admin/dataset-upload" },
    { label: "Model Management", to: "/admin/model-management" },
    { label: "ProcDoc", to: "/admin/procdoc" },
    { label: "User Management", to: "/admin/users" },
    { label: "Audit Log", to: "/admin/audit-log" },
  ],
  ROLE_PROCUREMENT_OFFICER: [
    { label: "Dashboard", to: "/procurement/dashboard" },
    { label: "New Forecast", to: "/procurement/new-forecast" },
    { label: "Multi-Project Forecast", to: "/procurement/multi-project-forecast" },
    { label: "Async Forecast", to: "/procurement/async-forecast" },
    { label: "What-If Analysis", to: "/procurement/what-if" },
    { label: "Supply Chain Planning", to: "/procurement/planning" },
    { label: "Forecast History", to: "/procurement/history" },
    { label: "Alerts", to: "/procurement/alerts" },
  ],
  ROLE_SITE_MANAGER: [
    { label: "Dashboard", to: "/site-manager/dashboard" },
    { label: "Warehouse Stock", to: "/site-manager/warehouse-inventory" },
    { label: "Material Receipt", to: "/site-manager/material-receipt" },
    { label: "Material Deployment", to: "/site-manager/material-deployment" },
    { label: "Procurement Recommendations", to: "/site-manager/procurement-recommendations" },
    { label: "Movement History", to: "/site-manager/material-movement-history" },
  ],
};

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function getInlineGap(viewportWidth) {
  if (viewportWidth <= 767) return 24;
  if (viewportWidth <= 900) return 32;
  if (viewportWidth <= 1180) return 18;
  return 32;
}

function getDynamicGap(count, viewportWidth) {
  const base = count <= 6 ? 9 : count <= 8 ? 8 : 7;
  const viewportAdjustment = viewportWidth < 1280 ? -1 : 0;
  return clamp(base + viewportAdjustment, 6, 12);
}

function getDynamicShellPadX(count, viewportWidth) {
  let pad = count <= 6 ? 30 : count <= 8 ? 26 : 24;
  if (viewportWidth <= 1180) pad = Math.min(pad, 18);
  if (viewportWidth <= 900) pad = Math.min(pad, 14);
  if (viewportWidth <= 767) pad = Math.min(pad, 12);
  return clamp(pad, 10, 32);
}

function getDynamicEdgePad(shellPadX, viewportWidth) {
  if (viewportWidth <= 900) return 0;
  if (viewportWidth <= 1180) return 2;
  return clamp(Math.round(shellPadX * 0.2), 4, 10);
}

export default function Sidebar() {
  const { user } = useAuth();
  const role = user?.role;
  const links = navConfig[role] || [];
  const [scrolled, setScrolled] = useState(false);
  const [navVars, setNavVars] = useState({});
  const headerRef = useRef(null);
  const linksRef = useRef(null);

  useEffect(() => {
    let rafId = null;
    const update = () => {
      const y = window.scrollY || document.documentElement.scrollTop || 0;
      setScrolled((prev) => {
        // Hysteresis prevents jitter when near the threshold.
        if (!prev && y > 28) return true;
        if (prev && y < 8) return false;
        return prev;
      });
      rafId = null;
    };

    const onScroll = () => {
      if (rafId !== null) return;
      rafId = window.requestAnimationFrame(update);
    };

    update();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      if (rafId !== null) window.cancelAnimationFrame(rafId);
    };
  }, []);

  useEffect(() => {
    let rafId = null;
    let resizeObserver = null;

    const measure = () => {
      const linksEl = linksRef.current;
      const headerEl = headerRef.current;
      if (!linksEl || !headerEl) {
        rafId = null;
        return;
      }

      const viewportWidth = window.innerWidth || document.documentElement.clientWidth || 1280;
      const count = Math.max(links.length, 1);
      const navGap = getDynamicGap(count, viewportWidth);
      const shellPadX = getDynamicShellPadX(count, viewportWidth);
      const linksEdgePad = getDynamicEdgePad(shellPadX, viewportWidth);
      const inlineGap = getInlineGap(viewportWidth);

      const buttons = Array.from(linksEl.querySelectorAll(".pg-top-nav-link"));
      const buttonsWidth = buttons.reduce((sum, button) => sum + button.getBoundingClientRect().width, 0);
      const totalGap = Math.max(0, buttons.length - 1) * navGap;
      const linksTrackWidth = buttonsWidth + totalGap + linksEdgePad * 2;

      // Keep safe breathing room from rounded borders in compact mode.
      const minimumCompactWidth = linksTrackWidth + shellPadX * 2 + 12;
      const maxAvailableWidth = Math.max(320, viewportWidth - inlineGap);
      const compactWidth = Math.min(Math.ceil(minimumCompactWidth), Math.ceil(maxAvailableWidth));

      setNavVars({
        "--pg-nav-inline-gap": `${inlineGap}px`,
        "--pg-nav-shell-pad-x": `${shellPadX}px`,
        "--pg-nav-links-gap": `${navGap}px`,
        "--pg-nav-links-edge-pad": `${linksEdgePad}px`,
        "--pg-nav-scrolled-width": `${compactWidth}px`,
      });

      rafId = null;
    };

    const queueMeasure = () => {
      if (rafId !== null) return;
      rafId = window.requestAnimationFrame(measure);
    };

    queueMeasure();
    window.addEventListener("resize", queueMeasure, { passive: true });

    if (typeof ResizeObserver !== "undefined" && linksRef.current) {
      resizeObserver = new ResizeObserver(queueMeasure);
      resizeObserver.observe(linksRef.current);
    }

    if (document.fonts?.ready) {
      document.fonts.ready.then(queueMeasure);
    }

    return () => {
      window.removeEventListener("resize", queueMeasure);
      if (rafId !== null) window.cancelAnimationFrame(rafId);
      if (resizeObserver) resizeObserver.disconnect();
    };
  }, [links.length, role]);

  return (
    <>
      <div className="pg-top-nav-anchor" />
      <header
        ref={headerRef}
        className={`pg-top-nav-fixed ${scrolled ? "pg-top-nav-scrolled" : ""}`}
        style={navVars}
      >
        <div className={`pg-top-nav-shell ${scrolled ? "is-compact" : ""}`}>
          <div className="pg-top-nav-brand">
            <span style={{ color: "var(--orange)" }}>⚡</span> POWERGRID
          </div>

          <nav ref={linksRef} className="pg-top-nav-links">
            {links.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) => (isActive ? "pg-top-nav-link pg-top-nav-link-active" : "pg-top-nav-link")}
              >
                {item.label}
              </NavLink>
            ))}
          </nav>

          <div className="pg-top-nav-user">
            <span className="pg-nav-username">{user?.username || "user"}</span>
            {user?.assignedRegion ? <span className="pg-chip">{user.assignedRegion}</span> : null}
          </div>
        </div>
      </header>
    </>
  );
}
