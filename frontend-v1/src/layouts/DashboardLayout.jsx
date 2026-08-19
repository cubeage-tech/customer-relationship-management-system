import { Outlet } from "react-router-dom";
import Sidebar from "../components/sidebar/Sidebar";
import UserNavbar from "../components/sidebar/UserNavbar";

const DashboardLayout = () => {
  return (
    <div className="flex flex-col h-screen bg-slate-50">

      <UserNavbar />

      <div className="flex flex-1 overflow-hidden">

        <Sidebar />

        <main className="flex-1 overflow-auto p-6">
          <Outlet />
        </main>

      </div>

    </div>
  );
};

export default DashboardLayout;