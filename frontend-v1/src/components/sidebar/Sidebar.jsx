import { NavLink } from "react-router-dom";
import { useAuth } from "../../core/hooks/useAuth";
import { getSidebarMenu } from "./SidebarConfig";
import { APP_SHORT_NAME } from "../../core/constants/app.constant";
import { getRoleLabel } from "../../core/utils/permission";

const Sidebar = () => {
  const { user } = useAuth();

  const menus = getSidebarMenu(user);

  return (
    <aside className="w-72 bg-slate-900 text-white">

      <div className="h-20 flex flex-col items-center justify-center border-b">
        <h1 className="text-2xl font-bold">
          {APP_SHORT_NAME}
        </h1>
        {user?.role && (
          <p className="text-xs text-slate-400">
            {getRoleLabel(user.role)}
          </p>
        )}
      </div>

      <nav className="p-5">

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

    </aside>
  );
};

export default Sidebar;
