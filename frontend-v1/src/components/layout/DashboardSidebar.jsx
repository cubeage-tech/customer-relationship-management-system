// src/components/Sidebar.jsx
//
// SHELL — working skeleton only. No styling, no icons, no real menu content.
// Config-driven so all dashboards (Administrator, Customer Support, Finance,
// Marketing Executive, Sales Executive, Sales Manager) share this ONE
// component/design and just plug in their own menu items below.
//
// Keeps the mechanics you'll actually need:
//   - expand/collapse state
//   - mobile overlay open/close + auto-close on route change
//   - body-scroll lock while mobile overlay is open
//   - role -> menu lookup

import { useState, useEffect } from "react";
import { useLocation } from "react-router-dom";

// ── Role keys — must match whatever `role` DashboardLayout passes down ──
export const ROLES = {
  ADMINISTRATOR: "ADMINISTRATOR",
  CUSTOMER_SUPPORT: "CUSTOMER_SUPPORT",
  FINANCE: "FINANCE",
  MARKETING_EXECUTIVE: "MARKETING_EXECUTIVE",
  SALES_EXECUTIVE: "SALES_EXECUTIVE",
  SALES_MANAGER: "SALES_MANAGER",
};

// ── Menu config — each role gets its own array. Fill in as screens are built. ──
// Shape per item: { text: "Label", icon: <Icon />, path: "/some/route" }
const menuItems = {
  [ROLES.ADMINISTRATOR]: [],
  [ROLES.CUSTOMER_SUPPORT]: [],
  [ROLES.FINANCE]: [],
  [ROLES.MARKETING_EXECUTIVE]: [],
  [ROLES.SALES_EXECUTIVE]: [],
  [ROLES.SALES_MANAGER]: [],
};

const DashboardSidebar = ({ role, expanded, isMobile, onToggle, onClose }) => {
  const location = useLocation();
  const menuList = menuItems[role] || [];

  // auto-close on route change (mobile)
  useEffect(() => {
    if (isMobile) onClose?.();
  }, [location.pathname]);

  // lock body scroll when mobile overlay is open
  useEffect(() => {
    if (isMobile) document.body.style.overflow = expanded ? "hidden" : "";
    return () => { document.body.style.overflow = ""; };
  }, [expanded, isMobile]);

  const isActive = (path) => location.pathname === path;

  return (
    <>
      {/* ── Mobile backdrop ── */}
      {isMobile && (
        <div
          onClick={onClose}
          style={{ display: expanded ? "block" : "none" }}
          aria-hidden="true"
        />
      )}

      {/* ── Sidebar panel ── */}
      <aside>
        {/* ── Toggle / close control ── */}
        <button type="button" onClick={onToggle}>
          {/* toggle icon goes here */}
        </button>

        {/* ── Menu content — pulled from menuItems[role] ── */}
        <nav aria-label="Dashboard navigation">
          {menuList.length === 0 ? (
            <div>{/* no menu items yet for this role — placeholder */}</div>
          ) : (
            <ul>
              {menuList.map((item) => (
                <li key={item.text}>
                  <button
                    type="button"
                    aria-current={isActive(item.path) ? "page" : undefined}
                  >
                    <span>{item.icon}</span>
                    <span>{item.text}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </nav>
      </aside>
    </>
  );
};

export default Sidebar;