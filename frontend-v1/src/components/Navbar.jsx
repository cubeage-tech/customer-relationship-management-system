import { Link } from "react-router-dom";
import RoutePath from "../core/constants/routes.constant";
import { APP_SHORT_NAME } from "../core/constants/app.constant";

const Navbar = () => {
  return (
    <nav className="bg-blue-600 text-white shadow-md">
      <div className="max-w-7xl mx-auto flex items-center justify-between px-6 py-4">
        {/* Logo */}
        <Link to={RoutePath.HOME} className="text-2xl font-bold">
          {APP_SHORT_NAME}
        </Link>

        {/* Navigation Links */}
        <div className="flex items-center gap-8">
          <Link
            to={RoutePath.HOME}
            className="hover:text-yellow-300 transition-colors duration-300"
          >
            Home
          </Link>

          <Link
            to={RoutePath.ABOUT}
            className="hover:text-yellow-300 transition-colors duration-300"
          >
            About
          </Link>

          <Link
            to={RoutePath.CONTACT}
            className="hover:text-yellow-300 transition-colors duration-300"
          >
            Contact
          </Link>

          <Link
            to={RoutePath.LOGIN}
            className="hover:text-yellow-300 transition-colors duration-300"
          >
            Log in
          </Link>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;
