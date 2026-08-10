import { Outlet } from "react-router-dom";
import Sidebar from "../components/sidebar/Sidebar";

const DashboardLayout = () => {
  return (
    <div className="flex h-screen bg-gray-100">

      <Sidebar />

      <main className="flex-1 overflow-auto p-6">
        <Outlet />
      </main>

    </div>
  );
};

export default DashboardLayout;