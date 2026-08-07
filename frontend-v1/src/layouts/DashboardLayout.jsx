// src/layouts/DashboardLayout.jsx
//
// SHELL — working skeleton only. One layout shared by all 6 dashboards
// (Administrator, Customer Support, Finance, Marketing Executive,
// Sales Executive, Sales Manager). `role` picks which menu Sidebar renders —
// see ROLES in Sidebar.jsx.
//
// Keeps the mechanics you'll actually need to wire up later:
//   - responsive breakpoint detection (shared with Header/Sidebar)
//   - sidebar expand/collapse + auto-open/close on breakpoint cross
//   - per-route show/hide for navbar & sidebar
//   - content margin that reacts to sidebar state
//   - HOC wrapper for attaching a layout + role to a page component

import React, { useState, useEffect } from "react";
import { Outlet, useLocation } from "react-router-dom";
import Sidebar from "../components/Sidebar";
import Header from "../components/Header";

// ── Route path prefixes where sidebar/navbar should be hidden — fill in as needed ──
const NO_SIDEBAR_ROUTES = [];
const NO_NAVBAR_ROUTES = [];

// ─── Breakpoint constant — keep in sync with Header.jsx / Sidebar.jsx ────────
const MOBILE_BREAKPOINT = 640;

// ── Sidebar width constants — adjust to match your real sidebar ──
const SIDEBAR_EXPANDED_WIDTH = 240;
const SIDEBAR_COLLAPSED_WIDTH = 56;

const DashboardLayout = ({ role }) => {
  const location = useLocation();
  const [isMobile, setIsMobile] = useState(() => window.innerWidth < MOBILE_BREAKPOINT);
  const [expanded, setExpanded] = useState(() => window.innerWidth >= MOBILE_BREAKPOINT);

  useEffect(() => {
    const handleResize = () => {
      const mobile = window.innerWidth < MOBILE_BREAKPOINT;
      setIsMobile(mobile);
      // when resizing to desktop, auto-open; to mobile, auto-close
      setExpanded(!mobile);
    };
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, []);

  const hideSidebar = NO_SIDEBAR_ROUTES.some((route) =>
    location.pathname.startsWith(route)
  );

  const hideNavbar = NO_NAVBAR_ROUTES.some((route) =>
    location.pathname.startsWith(route)
  );

  // on mobile, sidebar is overlay when expanded so no extra margin; collapsed strip still takes space
  const contentMargin = hideSidebar
    ? 0
    : isMobile
    ? SIDEBAR_COLLAPSED_WIDTH
    : expanded
    ? SIDEBAR_EXPANDED_WIDTH
    : SIDEBAR_COLLAPSED_WIDTH;

  return (
    <div style={{ display: "flex", minHeight: "100vh", width: "100%", overflowX: "hidden" }}>
      {!hideNavbar && <Header role={role} />}

      {!hideSidebar && (
        <Sidebar
          role={role}
          expanded={expanded}
          isMobile={isMobile}
          onToggle={() => setExpanded((prev) => !prev)}
          onClose={() => setExpanded(false)}
        />
      )}

      <div
        style={{
          flex: 1,
          minWidth: 0,
          display: "flex",
          flexDirection: "column",
          marginLeft: contentMargin,
          transition: "margin-left 0.3s ease",
        }}
      >
        <main
          style={{
            width: "100%",
            overflowX: "hidden",
            minHeight: hideNavbar ? "100vh" : "calc(100vh - 64px)",
            paddingTop: hideNavbar ? 0 : 64,
          }}
        >
          <Outlet />
        </main>
      </div>
    </div>
  );
};

// ── HOC wrapper — attach this layout + a fixed role to a page/route group ──
// Usage: export default withDashboardLayout(SalesManagerDashboardPage, ROLES.SALES_MANAGER)
export const withDashboardLayout = (WrappedComponent, role) => {
  return (props) => (
    <DashboardLayout role={role}>
      <WrappedComponent {...props} />
    </DashboardLayout>
  );
};

export default DashboardLayout;