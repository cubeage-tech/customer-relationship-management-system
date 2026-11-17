import { useRef } from "react";
import { Outlet, useLocation } from "react-router-dom";
import Sidebar from "../components/sidebar/Sidebar";
import UserNavbar from "../components/sidebar/UserNavbar";
import HelpButton from "../components/common/HelpButton";
import useScrollReveal from "../core/hooks/useScrollReveal";

const DashboardLayout = () => {
  const location = useLocation();
  const pageRef = useRef(null);
  useScrollReveal(pageRef, location.pathname);

  return (
    <div className="dashboard-shell page-canvas flex flex-col h-screen">

      <UserNavbar />

      <div className="flex flex-1 overflow-hidden">

        <Sidebar />

        <main className="dashboard-content flex-1 overflow-auto p-6">
          <div ref={pageRef} key={location.pathname} className="route-enter min-h-full">
            <Outlet />
          </div>
        </main>

      </div>
      <HelpButton />

    </div>
  );
};

export default DashboardLayout;