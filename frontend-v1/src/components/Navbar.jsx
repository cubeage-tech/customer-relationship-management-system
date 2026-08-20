import { useState } from "react";
import { Link, NavLink } from "react-router-dom";
import RoutePath from "../core/constants/routes.constant";

const navLinks = [
  { label: "Home", to: RoutePath.HOME },
  { label: "About", to: RoutePath.ABOUT },
  { label: "Plans", to: RoutePath.PLANS },
  { label: "Contact", to: RoutePath.CONTACT },
];

const Navbar = () => {
  const [menuOpen, setMenuOpen] = useState(false);

  return (
    <nav className="bg-surface border-b border-border shadow-soft">
      <div className="max-w-7xl mx-auto flex items-center justify-between px-6 py-3">
        {/* Logo */}
        <Link to={RoutePath.HOME} className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-full gradient-primary flex items-center justify-center shadow-glow">
            <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" 
            stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" 
            class="lucide lucide-sparkles h-5 w-5 text-primary-foreground" aria-hidden="true"
             data-tsd-source="/src/components/marketing/SiteChrome.tsx:21:15">
              <path d="M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z"></path><path d="M20 2v4"></path><path d="M22 4h-4"></path><circle cx="4" cy="20" r="2"></circle></svg>
          </div>
          <div className="flex flex-col leading-tight">
            <span className="text-sm font-bold text-foreground">SmartCRM AI</span>
            <span className="text-xs text-muted-foreground">Enterprise Suite</span>
          </div>
        </Link>

        {/* Desktop Nav Links */}
        <div className="hidden md:flex items-center gap-1">
          {navLinks.map(({ label, to }) => (
            <NavLink
              key={to}
              to={to}
              end
              className={({ isActive }) =>
                `px-4 py-2 rounded-full text-sm transition-colors duration-200 ${
                  isActive
                    ? "bg-secondary text-secondary-foreground font-medium"
                    : "text-muted-foreground hover:bg-secondary hover:text-secondary-foreground"
                }`
              }
            >
              {label}
            </NavLink>
          ))}
        </div>

        {/* Desktop CTA Buttons */}
        <div className="hidden md:flex items-center gap-3">
          <Link
            to={RoutePath.LOGIN}
            className="px-5 py-2 rounded-full text-sm font-medium border border-border text-foreground hover:bg-secondary transition-colors duration-200"
          >
            Login
          </Link>
          <Link
            to={RoutePath.SIGNUP}
            className="px-5 py-2 rounded-full text-sm font-medium gradient-primary text-primary-foreground hover:opacity-90 transition-opacity duration-200"
          >
            Start free trial
          </Link>
        </div>

        {/* Hamburger Button */}
        <button
          className="md:hidden flex flex-col justify-center items-center w-9 h-9 gap-1.5"
          onClick={() => setMenuOpen((prev) => !prev)}
          aria-label="Toggle menu"
        >
          <span className={`block h-0.5 w-6 bg-foreground transition-transform duration-300 ${menuOpen ? "rotate-45 translate-y-2" : ""}`} />
          <span className={`block h-0.5 w-6 bg-foreground transition-opacity duration-300 ${menuOpen ? "opacity-0" : ""}`} />
          <span className={`block h-0.5 w-6 bg-foreground transition-transform duration-300 ${menuOpen ? "-rotate-45 -translate-y-2" : ""}`} />
        </button>
      </div>

      {/* Mobile Menu */}
      {menuOpen && (
        <div className="md:hidden border-t border-border px-6 py-4 flex flex-col gap-2 bg-surface">
          {navLinks.map(({ label, to }) => (
            <NavLink
              key={to}
              to={to}
              end
              onClick={() => setMenuOpen(false)}
              className={({ isActive }) =>
                `px-4 py-2 rounded-full text-sm transition-colors duration-200 ${
                  isActive
                    ? "bg-secondary text-secondary-foreground font-medium"
                    : "text-muted-foreground hover:bg-secondary hover:text-secondary-foreground"
                }`
              }
            >
              {label}
            </NavLink>
          ))}
          <div className="flex flex-col gap-2 mt-2">
            <Link
              to={RoutePath.LOGIN}
              onClick={() => setMenuOpen(false)}
              className="px-5 py-2 rounded-full text-sm font-medium border border-border text-foreground hover:bg-secondary transition-colors duration-200 text-center"
            >
              View Dashboard
            </Link>
            <Link
              to={RoutePath.SIGNUP}
              onClick={() => setMenuOpen(false)}
              className="px-5 py-2 rounded-full text-sm font-medium gradient-primary text-primary-foreground hover:opacity-90 transition-opacity duration-200 text-center"
            >
              Start free trial
            </Link>
          </div>
        </div>
      )}
    </nav>
  );
};

export default Navbar;
