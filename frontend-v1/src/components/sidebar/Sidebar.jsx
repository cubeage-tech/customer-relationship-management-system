import { NavLink, useNavigate } from "react-router-dom";
import { LogOut } from "lucide-react";
import { useAuth } from "../../core/hooks/useAuth";
import { getSidebarMenu } from "./SidebarConfig";
import { APP_SHORT_NAME } from "../../core/constants/app.constant";
import { getRoleLabel } from "../../core/utils/permission";
import RoutePath from "../../core/constants/routes.constant";

const Sidebar = () => {
  const { user, logoutUser } = useAuth();
  const navigate = useNavigate();

  const menus = getSidebarMenu(user);

  const handleLogout = () => {
    logoutUser();
    navigate(RoutePath.LOGIN, { replace: true });
  };

  return (
    <aside className="w-72 bg-slate-900 text-white flex flex-col h-full">

      <div className="h-20 flex flex-col items-center justify-center border-b border-slate-800 shrink-0">
        <h1 className="text-2xl font-bold">
          {APP_SHORT_NAME}
        </h1>
        {user?.role && (
          <p className="text-xs text-slate-400">
            {getRoleLabel(user.role)}
          </p>
        )}
      </div>

      <nav className="p-5 flex-1 overflow-y-auto">

        {menus.map((menu) => (

          <NavLink
            key={menu.path}
            to={menu.path}
            className={({ isActive }) =>
              `flex items-center gap-3 p-3 rounded-lg mb-2 ${
                isActive
                  ? "bg-blue-600"
                  : "hover:bg-slate-800"
              }`
            }
          >
            <menu.icon size={20} />
            {menu.label}
          </NavLink>

        ))}

      </nav>

      <div className="p-5 border-t border-slate-800 shrink-0">
        <button
          type="button"
          onClick={handleLogout}
          className="flex items-center gap-3 p-3 rounded-lg w-full text-left hover:bg-slate-800 transition-colors"
        >
          <LogOut size={20} />
          Logout
        </button>
      </div>

    </aside>
  );
};

export default Sidebar;