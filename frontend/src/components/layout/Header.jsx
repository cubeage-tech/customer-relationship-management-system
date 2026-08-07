import { Link } from "react-router-dom";
import RoutePath from "../../core/constants/routes.constant";

const Header = () => {
  return (
    <header className="app-header">
      <Link to={RoutePath.HOME}>Home</Link>
    </header>
  );
};

export default Header;