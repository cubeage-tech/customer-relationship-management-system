import { Link } from 'react-router-dom';
import RoutePath from '../../core/constants/routes.constant';
import { APP_NAME } from '../../core/constants/app.constant';

const Hero = () => {
  return (
    <section className="max-w-7xl mx-auto px-6 py-16 text-center">
      <h1 className="text-4xl font-bold text-slate-900">{APP_NAME}</h1>
      <p className="text-slate-600 mt-4 max-w-2xl mx-auto">
        Track customers, qualify leads, move opportunities through your pipeline,
        send quotations and resolve service tickets — all from one place.
      </p>
      <div className="flex items-center justify-center gap-4 mt-8">
        <Link
          to={RoutePath.SIGNUP}
          className="bg-blue-600 text-white px-5 py-2 rounded-lg hover:bg-blue-700 transition-colors"
        >
          Get started
        </Link>
        <Link
          to={RoutePath.LOGIN}
          className="px-5 py-2 rounded-lg border border-slate-300 hover:bg-slate-100 transition-colors"
        >
          Log in
        </Link>
      </div>
    </section>
  );
};

export default Hero;
