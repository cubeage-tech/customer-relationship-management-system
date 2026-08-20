import { Link } from "react-router-dom";
import RoutePath from "../core/constants/routes.constant";

const Footer = () => {
  return (
    <footer className="bg-surface border-t border-border shadow-soft">
      <div className="max-w-7xl mx-auto flex flex-col sm:flex-row items-center justify-between gap-3 px- 8 py-4">
        <p className="text-sm text-muted-foreground">
          © {new Date().getFullYear()} SmartCRM AI · Built for revenue teams
        </p>

        <div className="flex flex-nowrap justify-center items-center gap-1">
          {[
            { label: "Home", to: RoutePath.HOME },
            { label: "About", to: RoutePath.ABOUT },
            { label: "Plans", to: RoutePath.PLANS },
            { label: "Contact", to: RoutePath.CONTACT },
          ].map(({ label, to }) => (
            <Link
              key={to}
              to={to}
              className="px-2 sm:px-4 py-2 rounded-full text-sm text-muted-foreground hover:bg-secondary hover:text-secondary-foreground transition-colors duration-200"
            >
              {label}
            </Link>
          ))}
        </div>
      </div>
    </footer>
  );
};

export default Footer;
