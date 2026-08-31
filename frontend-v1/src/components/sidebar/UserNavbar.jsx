import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Search,
  Sparkles,
  Bell,
  MessageSquare,
  ChevronDown,
  LogOut,
  User,
  Settings,
} from "lucide-react";
import { useAuth } from "../../core/hooks/useAuth";
import { APP_SHORT_NAME } from "../../core/constants/app.constant";
import { getRoleLabel } from "../../core/utils/permission";
import RoutePath from "../../core/constants/routes.constant";

const UserNavbar = () => {
  const { user, logoutUser } = useAuth();
  const navigate = useNavigate();

  const [profileOpen, setProfileOpen] = useState(false);
  const dropdownRef = useRef(null);

  // dummy counts — wire these up to real data sources
  const notificationCount = 5;
  const messageCount = 2;

  const initials = user?.name
    ? user.name
        .split(" ")
        .map((n) => n[0])
        .join("")
        .slice(0, 2)
        .toUpperCase()
    : "U";

  const handleLogout = () => {
    logoutUser();
    navigate(RoutePath.LOGIN, { replace: true });
  };

  useEffect(() => {
    const handleClickOutside = (event) => {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target)) {
        setProfileOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  return (
    <header className="h-20 w-full flex items-center justify-between px-6 bg-white border-b border-slate-200 shrink-0">

      {/* Logo / Brand */}
      <div className="flex items-center gap-3">
        <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-indigo-500 to-violet-600 flex items-center justify-center shrink-0">
          <Sparkles size={20} className="text-white" />
        </div>
        <div>
          <h1 className="text-base font-semibold text-slate-900 leading-tight">
            {APP_SHORT_NAME || "SmartCRM AI"}
          </h1>
          <p className="text-xs text-slate-400 leading-tight">Enterprise Suite</p>
        </div>
      </div>

      {/* Search */}
      <div className="flex-1 max-w-xl mx-8">
        <div className="relative">
          <Search
            size={18}
            className="absolute left-3.5 top-1/2 -translate-y-1/2 text-slate-400"
          />
          <input
            type="text"
            placeholder="Search leads, deals, companies or ask AI..."
            className="w-full h-11 pl-10 pr-14 rounded-xl bg-slate-50 border border-slate-200 text-sm text-slate-700 placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 focus:border-indigo-400 transition"
          />
          <kbd className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-slate-400 bg-white border border-slate-200 rounded-md px-1.5 py-0.5">
            ⌘K
          </kbd>
        </div>
      </div>

      {/* Right side actions */}
      <div className="flex items-center gap-4">

        {/* AI Assistant */}
        <button
          type="button"
          className="flex items-center gap-2 h-10 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-violet-600 text-white text-sm font-medium hover:opacity-90 transition"
        >
          <Sparkles size={16} />
          AI Assistant
        </button>

        {/* Notifications */}
        <button
          type="button"
          className="relative w-10 h-10 rounded-full flex items-center justify-center text-slate-500 hover:bg-slate-100 transition"
        >
          <Bell size={20} />
          {notificationCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[10px] font-semibold flex items-center justify-center">
              {notificationCount}
            </span>
          )}
        </button>

        {/* Messages */}
        <button
          type="button"
          className="relative w-10 h-10 rounded-full flex items-center justify-center text-slate-500 hover:bg-slate-100 transition"
        >
          <MessageSquare size={20} />
          {messageCount > 0 && (
            <span className="absolute -top-0.5 -right-0.5 min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[10px] font-semibold flex items-center justify-center">
              {messageCount}
            </span>
          )}
        </button>

        {/* Profile dropdown */}
        <div className="relative" ref={dropdownRef}>
          <button
            type="button"
            onClick={() => setProfileOpen((prev) => !prev)}
            className="flex items-center gap-2 pl-2 pr-1 py-1 rounded-xl hover:bg-slate-100 transition"
          >
            <div className="w-9 h-9 rounded-full bg-indigo-600 text-white text-sm font-semibold flex items-center justify-center shrink-0">
              {initials}
            </div>
            <div className="text-left leading-tight hidden sm:block">
              <p className="text-sm font-medium text-slate-900">
                {user?.name || "User"}
              </p>
              <p className="text-xs text-slate-400">
                {user?.role ? getRoleLabel(user.role) : "Member"}
              </p>
            </div>
            <ChevronDown
              size={16}
              className={`text-slate-400 transition-transform ${
                profileOpen ? "rotate-180" : ""
              }`}
            />
          </button>

          {profileOpen && (
            <div className="absolute right-0 mt-2 w-56 bg-white border border-slate-200 rounded-xl shadow-lg py-2 z-50">
              <div className="px-4 py-2 border-b border-slate-100">
                <p className="text-sm font-medium text-slate-900">
                  {user?.name || "User"}
                </p>
                <p className="text-xs text-slate-400">{user?.email}</p>
              </div>

              <button
                type="button"
                onClick={() => {
                  setProfileOpen(false);
                  navigate(RoutePath.PROFILE);
                }}
                className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-slate-600 hover:bg-slate-50 transition"
              >
                <User size={16} />
                Profile
              </button>

              <button
                type="button"
                onClick={() => {
                  setProfileOpen(false);
                  navigate(RoutePath.SETTINGS);
                }}
                className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-slate-600 hover:bg-slate-50 transition"
              >
                <Settings size={16} />
                Settings
              </button>

              <div className="border-t border-slate-100 mt-1 pt-1">
                <button
                  type="button"
                  onClick={handleLogout}
                  className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-red-600 hover:bg-red-50 transition"
                >
                  <LogOut size={16} />
                  Logout
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
};

export default UserNavbar;