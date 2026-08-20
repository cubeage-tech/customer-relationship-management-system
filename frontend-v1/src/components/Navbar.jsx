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
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 2L2 7l10 5 10-5-10-5z" />
              <path d="M2 17l10 5 10-5" />
              <path d="M2 12l10 5 10-5" />
            </svg>
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
            View Dashboard
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
