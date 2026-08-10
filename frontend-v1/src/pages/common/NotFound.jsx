import { Link } from 'react-router-dom';
import RoutePath from '../../core/constants/routes.constant';

const NotFound = () => {
  return (
    <section className="p-10 text-center">
      <h1 className="text-2xl font-bold text-slate-900">Page not found</h1>
      <p className="text-slate-600 mt-2">
        The page you are looking for does not exist or has been moved.
      </p>
      <Link to={RoutePath.HOME} className="inline-block mt-5 text-blue-600 hover:underline">
        Go to home
      </Link>
    </section>
  );
};

export default NotFound;
