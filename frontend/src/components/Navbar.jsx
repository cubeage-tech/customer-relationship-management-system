import { Link } from "react-router-dom";

const Navbar = () => {
  return (
    <nav className="bg-blue-600 text-white shadow-md">
      <div className="max-w-7xl mx-auto flex items-center justify-between px-6 py-4">
        {/* Logo */}
        <Link to="/" className="text-2xl font-bold">
          Logo
        </Link>

        {/* Navigation Links */}
        <div className="flex items-center gap-8">
          <Link
            to="/"
            className="hover:text-yellow-300 transition-colors duration-300"
          >
            Home
          </Link>

          <Link
            to="/about"
            className="hover:text-yellow-300 transition-colors duration-300"
          >
            About
          </Link>

          <Link
            to="/help-center"
            className="hover:text-yellow-300 transition-colors duration-300"
          >
            Help Center
          </Link>
        </div>
      </div>
    </nav>
  );
};

export default Navbar;