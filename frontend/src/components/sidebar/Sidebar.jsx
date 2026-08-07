import { NavLink } from "react-router-dom";
import { useAuth } from "../../core/hooks/useAuth";
import { sidebarMenus } from "./SidebarConfig";

const Sidebar = () => {
  const { user } = useAuth();

  const menus = sidebarMenus[user?.role] || [];

  return (
    <aside className="w-72 bg-slate-900 text-white">

      <div className="h-20 flex items-center justify-center border-b">
        <h1 className="text-2xl font-bold">
          Smart Scrap
        </h1>
      </div>

      <nav className="p-5">

        {menus.map((menu) => (

          <NavLink
            key={menu.path}
            to={menu.path}
            className={({ isActive }) =>
              `flex items-center gap-3 p-3 rounded-lg mb-2 ${
                isActive
                  ? "bg-green-600"
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
