import { Link } from "react-router-dom";

const Footer = () => {
  return (
    <footer className="bg-blue-600 text-white mt-auto">
      <div className="max-w-7xl mx-auto px-6 py-8">
        <div className="flex flex-col md:flex-row justify-between items-center gap-6">
          {/* Logo / Brand */}
          <div>
            <h2 className="text-2xl font-bold">Logo</h2>
            <p className="text-sm text-blue-100 mt-2">
              © {new Date().getFullYear()} All Rights Reserved.
            </p>
          </div>

          {/* Navigation */}
          <div className="flex gap-6">
            <Link
              to="/"
              className="hover:text-yellow-300 transition-colors"
            >
              Home
            </Link>

            <Link
              to="/about"
              className="hover:text-yellow-300 transition-colors"
            >
              About
            </Link>

            <Link
              to="/help-center"
              className="hover:text-yellow-300 transition-colors"
            >
              Help Center
            </Link>
          </div>

          {/* Contact */}
          <div className="text-center md:text-right">
            <p>Email: support@example.com</p>
            <p>Phone: +91 98765 43210</p>
          </div>
        </div>

        {/* Bottom */}
        <div className="border-t border-blue-500 mt-6 pt-4 text-center text-sm text-blue-100">
          Built with  using React & Tailwind CSS
        </div>
      </div>
    </footer>
  );
};

export default Footer;