import { NavLink, useNavigate } from "react-router-dom";
import { Sparkles } from "lucide-react";
import { useAuth } from "../../core/hooks/useAuth";
import { getSidebarMenu } from "./SidebarConfig";
import RoutePath from "../../core/constants/routes.constant";

// Optional AI credits widget data — wire this up to real usage data.
const AI_CREDITS_USED = 8420;
const AI_CREDITS_TOTAL = 10000;

const Sidebar = () => {
  const { user } = useAuth();
  const navigate = useNavigate();

  // getSidebarMenu(user) should return items shaped like:
  // { path, label, icon, badge?: string | number, isNew?: boolean }
  const menus = getSidebarMenu(user);

  const creditsPct = Math.min(
    100,
    Math.round((AI_CREDITS_USED / AI_CREDITS_TOTAL) * 100)
  );

  return (
    <aside className="w-72 bg-white border-r border-slate-200 flex flex-col h-full">

      <nav className="px-4 pt-6 pb-2 flex-1 overflow-y-auto">
        <p className="px-3 mb-3 text-xs font-semibold tracking-wider text-slate-400">
          WORKSPACE
        </p>

        {menus.map((menu) => (
          <NavLink
            key={menu.path}
            to={menu.path}
            className={({ isActive }) =>
              `relative flex items-center justify-between gap-3 px-3 py-2.5 rounded-lg mb-1 text-sm font-medium transition-colors ${
                isActive
                  ? "bg-indigo-50 text-indigo-600"
                  : "text-slate-600 hover:bg-slate-50"
              }`
            }
          >
            {({ isActive }) => (
              <>
                {isActive && (
                  <span className="absolute left-0 top-1/2 -translate-y-1/2 h-5 w-1 rounded-r-full bg-indigo-600" />
                )}

                <span className="flex items-center gap-3">
                  <menu.icon size={18} />
                  {menu.label}
                </span>

                {menu.badge !== undefined && (
                  <span
                    className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                      isActive
                        ? "bg-indigo-100 text-indigo-600"
                        : "bg-slate-100 text-slate-500"
                    }`}
                  >
                    {menu.badge}
                  </span>
                )}

                {menu.isNew && (
                  <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-indigo-600 text-white">
                    New
                  </span>
                )}
              </>
            )}
          </NavLink>
        ))}
      </nav>

      {/* AI Credits widget */}
      <div className="p-4 border-t border-slate-200 shrink-0">
        <button
          type="button"
          onClick={() => navigate(RoutePath.AI_CREDITS)}
          className="w-full text-left rounded-xl bg-indigo-50 p-4 hover:bg-indigo-100/70 transition-colors"
        >
          <div className="flex items-center gap-2 mb-2">
            <Sparkles size={16} className="text-indigo-600" />
            <span className="text-sm font-semibold text-indigo-700">
              AI Credits
            </span>
          </div>
          <p className="text-xs text-slate-500 mb-2">
            {AI_CREDITS_USED.toLocaleString()} of{" "}
            {AI_CREDITS_TOTAL.toLocaleString()} monthly credits
          </p>
          <div className="h-1.5 w-full bg-indigo-100 rounded-full overflow-hidden">
            <div
              className="h-full bg-indigo-600 rounded-full"
              style={{ width: `${creditsPct}%` }}
            />
          </div>
        </button>
      </div>

    </aside>
  );
};

export default Sidebar;