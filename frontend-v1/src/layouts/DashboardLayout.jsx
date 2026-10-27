import { useEffect, useState } from "react";
import { Outlet, useLocation } from "react-router-dom";
import Sidebar from "../components/sidebar/Sidebar";
import UserNavbar from "../components/sidebar/UserNavbar";

/**
 * NFR-7.9: on desktop the sidebar is always shown; on tablets and phones it becomes a drawer
 * opened from the navbar's menu button and closed by tapping outside, Escape or navigating.
 */
const DashboardLayout = () => {
  const [navOpen, setNavOpen] = useState(false);
  const location = useLocation();
  const [openedAt, setOpenedAt] = useState(location.pathname);

  // Close the drawer after navigating (derived from the route, not set in an effect).
  const drawerOpen = navOpen && openedAt === location.pathname;
  const openNav = () => {
    setOpenedAt(location.pathname);
    setNavOpen(true);
  };
  const closeNav = () => setNavOpen(false);

  useEffect(() => {
    if (!drawerOpen) return undefined;
    const onKey = (e) => e.key === "Escape" && setNavOpen(false);
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [drawerOpen]);

  return (
    <div className="flex flex-col h-screen bg-slate-50">

      <UserNavbar onOpenMenu={openNav} />

      <div className="flex flex-1 overflow-hidden">

        <div className="hidden lg:flex">
          <Sidebar />
        </div>

        {drawerOpen && (
          <div className="fixed inset-0 z-40 lg:hidden" role="dialog" aria-modal="true" aria-label="Navigation">
            <button type="button" aria-label="Close menu" className="absolute inset-0 bg-slate-900/40" onClick={closeNav} />
            <div className="relative h-full w-72 max-w-[85vw] shadow-xl">
              <Sidebar onNavigate={closeNav} />
            </div>
          </div>
        )}

        <main className="flex-1 overflow-auto p-4 sm:p-6 min-w-0">
          <Outlet />
        </main>

      </div>

    </div>
  );
};

export default DashboardLayout;
